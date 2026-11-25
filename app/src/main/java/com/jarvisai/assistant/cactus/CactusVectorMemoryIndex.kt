package com.jarvisai.assistant.cactus

import android.content.Context
import android.util.Log
import com.jarvisai.assistant.data.local.MemoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.sqrt

/**
 * Persistent on-device Vector Index for Agentra's 5-Tier Cognitive Memory Center & Document RAG.
 *
 * Stores float embedding vectors produced by the Cactus local embedding engine
 * (`Cactus-Compute/Qwen3-Embedding-0.6B` via `/v1/embeddings` or native Cactus CLI)
 * inside `filesDir/cactus/memory_vectors.json` and computes fast cosine similarity scores
 * so memory retrieval works semantically 100% offline.
 */
object CactusVectorMemoryIndex {
    private const val TAG = "CactusVectorIndex"
    private const val INDEX_FILE_NAME = "memory_vectors.json"

    private val mutex = Mutex()

    // memoryId -> (contentHash, normalized FloatArray)
    private val memoryVectors = LinkedHashMap<Long, Pair<Int, FloatArray>>()
    @Volatile
    private var isLoaded = false
    @Volatile
    private var appContextRef: Context? = null

    fun initialize(context: Context) {
        appContextRef = context.applicationContext
        if (!isLoaded) {
            loadFromDiskLockedSync(context.applicationContext)
        }
    }

    fun getIndexedCount(): Int = synchronized(memoryVectors) { memoryVectors.size }

    private fun indexFile(context: Context): File {
        val dir = File(context.filesDir, "cactus").apply { mkdirs() }
        return File(dir, INDEX_FILE_NAME)
    }

    private fun loadFromDiskLockedSync(context: Context) {
        try {
            val file = indexFile(context)
            if (!file.exists()) {
                isLoaded = true
                return
            }
            val raw = file.readText()
            if (raw.isBlank()) {
                isLoaded = true
                return
            }
            val root = JSONObject(raw)
            val entries = root.optJSONArray("entries") ?: JSONArray()
            synchronized(memoryVectors) {
                memoryVectors.clear()
                for (i in 0 until entries.length()) {
                    val obj = entries.optJSONObject(i) ?: continue
                    val id = obj.optLong("id", -1L)
                    val hash = obj.optInt("hash", 0)
                    val vecArr = obj.optJSONArray("vec") ?: continue
                    if (id > 0L && vecArr.length() > 0) {
                        val arr = FloatArray(vecArr.length()) { idx -> vecArr.optDouble(idx, 0.0).toFloat() }
                        memoryVectors[id] = hash to normalizeVector(arr)
                    }
                }
            }
            isLoaded = true
        } catch (e: Exception) {
            Log.w(TAG, "Failed loading Cactus vector index: ${e.message}")
            isLoaded = true
        }
    }

    private fun persistToDiskSync(context: Context) {
        try {
            val entries = JSONArray()
            synchronized(memoryVectors) {
                memoryVectors.forEach { (id, pair) ->
                    val obj = JSONObject()
                    obj.put("id", id)
                    obj.put("hash", pair.first)
                    val vecArr = JSONArray()
                    pair.second.forEach { v -> vecArr.put(v.toDouble()) }
                    obj.put("vec", vecArr)
                    entries.put(obj)
                }
            }
            val root = JSONObject().apply {
                put("version", 1)
                put("updatedAt", System.currentTimeMillis())
                put("entries", entries)
            }
            val target = indexFile(context)
            val tmp = File(target.parentFile, "$INDEX_FILE_NAME.tmp")
            tmp.writeText(root.toString())
            tmp.renameTo(target)
        } catch (e: Exception) {
            Log.w(TAG, "Failed persisting Cactus vector index: ${e.message}")
        }
    }

    /**
     * Indexes or refreshes a single memory record using Cactus local embeddings.
     * Returns true if a real embedding vector was computed and persisted.
     */
    suspend fun indexMemory(context: Context, memory: MemoryEntity): Boolean = withContext(Dispatchers.IO) {
        initialize(context)
        val text = "${memory.title}: ${memory.content} [${memory.category}] ${memory.tags}".trim()
        val hash = text.hashCode()
        val existing = synchronized(memoryVectors) { memoryVectors[memory.id] }
        if (existing != null && existing.first == hash) {
            return@withContext true
        }

        val vec = CactusAiService.computeLocalEmbedding(context, text) ?: return@withContext false
        if (vec.isEmpty()) return@withContext false

        mutex.withLock {
            synchronized(memoryVectors) {
                memoryVectors[memory.id] = hash to normalizeVector(vec)
            }
            persistToDiskSync(context.applicationContext)
        }
        CactusNativeRuntime.updateIndexedVectorsCount(getIndexedCount())
        true
    }

    /**
     * Re-indexes all memories in batches using Cactus local embeddings.
     * Returns Pair(indexedCount, totalMemories).
     */
    suspend fun reindexAllMemories(
        context: Context,
        memories: List<MemoryEntity>,
        onProgress: ((done: Int, total: Int) -> Unit)? = null
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        initialize(context)
        if (memories.isEmpty()) return@withContext 0 to 0

        var successCount = 0
        val validIds = memories.map { it.id }.toSet()

        // Prune deleted memory IDs
        synchronized(memoryVectors) {
            val staleKeys = memoryVectors.keys.filter { it !in validIds }
            staleKeys.forEach { memoryVectors.remove(it) }
        }

        memories.forEachIndexed { idx, mem ->
            val ok = indexMemory(context, mem)
            if (ok) successCount++
            onProgress?.invoke(idx + 1, memories.size)
        }

        mutex.withLock {
            persistToDiskSync(context.applicationContext)
        }
        CactusNativeRuntime.updateIndexedVectorsCount(getIndexedCount())
        successCount to memories.size
    }

    suspend fun removeMemory(context: Context, memoryId: Long) = withContext(Dispatchers.IO) {
        initialize(context)
        mutex.withLock {
            val removed = synchronized(memoryVectors) { memoryVectors.remove(memoryId) != null }
            if (removed) {
                persistToDiskSync(context.applicationContext)
                CactusNativeRuntime.updateIndexedVectorsCount(getIndexedCount())
            }
        }
    }

    suspend fun clearAll(context: Context) = withContext(Dispatchers.IO) {
        initialize(context)
        mutex.withLock {
            synchronized(memoryVectors) { memoryVectors.clear() }
            runCatching { indexFile(context).delete() }
            CactusNativeRuntime.updateIndexedVectorsCount(0)
        }
    }

    /**
     * Computes semantic cosine similarity scores `[0.0 .. 1.0]` for all indexed memories against `query`.
     * Uses a fast short timeout so it never slows down MemoryRepository if the Cactus embedding server is offline.
     */
    suspend fun scoreMemoriesForQuery(
        query: String,
        memories: List<MemoryEntity>
    ): Map<Long, Double> = withContext(Dispatchers.IO) {
        val ctx = appContextRef ?: return@withContext emptyMap()
        if (query.isBlank() || memories.isEmpty()) return@withContext emptyMap()
        if (!CactusNativeRuntime.config.value.memoryVectorRagEnabled) return@withContext emptyMap()
        if (getIndexedCount() == 0) return@withContext emptyMap()

        val queryVecRaw = CactusAiService.computeLocalEmbedding(ctx, query, quickTimeout = true)
            ?: return@withContext emptyMap()
        if (queryVecRaw.isEmpty()) return@withContext emptyMap()
        val queryVec = normalizeVector(queryVecRaw)

        val scores = HashMap<Long, Double>()
        synchronized(memoryVectors) {
            for (mem in memories) {
                val memVec = memoryVectors[mem.id]?.second ?: continue
                if (memVec.size == queryVec.size) {
                    val sim = dotProduct(queryVec, memVec).coerceIn(0.0, 1.0)
                    if (sim > 0.15) {
                        scores[mem.id] = sim
                    }
                }
            }
        }
        scores
    }

    /**
     * Ranks document chunks semantically using Cactus embeddings for Offline Document RAG.
     * Falls back cleanly to lexical overlap ranking if embeddings are unavailable.
     */
    suspend fun selectTopDocumentChunks(
        context: Context,
        documentText: String,
        query: String,
        maxChunks: Int = 5,
        chunkSizeChars: Int = 1100
    ): List<String> = withContext(Dispatchers.IO) {
        val cleanDoc = documentText.trim()
        if (cleanDoc.length <= chunkSizeChars * maxChunks) {
            return@withContext listOf(cleanDoc)
        }

        // Split into overlapping chunks
        val chunks = mutableListOf<String>()
        var cursor = 0
        val step = (chunkSizeChars - 150).coerceAtLeast(300)
        while (cursor < cleanDoc.length && chunks.size < 40) {
            val end = (cursor + chunkSizeChars).coerceAtMost(cleanDoc.length)
            chunks.add(cleanDoc.substring(cursor, end).trim())
            if (end >= cleanDoc.length) break
            cursor += step
        }

        val queryVecRaw = CactusAiService.computeLocalEmbedding(context, query, quickTimeout = true)
        if (queryVecRaw != null && queryVecRaw.isNotEmpty()) {
            val queryVec = normalizeVector(queryVecRaw)
            val scored = chunks.mapIndexed { idx, chunk ->
                val chunkVec = CactusAiService.computeLocalEmbedding(context, chunk.take(900), quickTimeout = true)
                val sim = if (chunkVec != null && chunkVec.size == queryVec.size) {
                    dotProduct(queryVec, normalizeVector(chunkVec))
                } else {
                    0.0
                }
                Triple(idx, chunk, sim)
            }
            if (scored.any { it.third > 0.05 }) {
                return@withContext scored
                    .sortedByDescending { it.third }
                    .take(maxChunks)
                    .sortedBy { it.first }
                    .map { it.second }
            }
        }

        // Lexical overlap fallback for long documents when embedding server isn't loaded
        val queryTokens = query.lowercase()
            .split(Regex("[^a-zA-Z0-9\\u0900-\\u097F]+"))
            .filter { it.length >= 3 }
            .toSet()

        chunks.mapIndexed { idx, chunk ->
            val lower = chunk.lowercase()
            val hits = queryTokens.count { lower.contains(it) }
            Triple(idx, chunk, hits)
        }
            .sortedByDescending { it.third }
            .take(maxChunks)
            .sortedBy { it.first }
            .map { it.second }
    }

    private fun normalizeVector(vec: FloatArray): FloatArray {
        var normSq = 0.0
        for (v in vec) normSq += v.toDouble() * v.toDouble()
        val norm = sqrt(normSq)
        if (norm <= 1e-9) return vec
        return FloatArray(vec.size) { i -> (vec[i] / norm).toFloat() }
    }

    private fun dotProduct(a: FloatArray, b: FloatArray): Double {
        var sum = 0.0
        for (i in a.indices) {
            sum += a[i].toDouble() * b[i].toDouble()
        }
        return sum
    }
}
