package com.jarvisai.assistant.data.remote.inspector

import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import java.util.UUID

class NetworkInspectorInterceptor(
    private val defaultServiceType: String? = null
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        if (!NetworkInspectorManager.isEnabled) {
            return chain.proceed(chain.request())
        }

        val request = chain.request()
        val startTime = System.currentTimeMillis()
        val logId = UUID.randomUUID().toString()
        val url = request.url.toString()
        val path = request.url.encodedPath
        val isGrpc = request.header("X-Grpc-Web") != null ||
            request.header("content-type")?.contains("grpc", ignoreCase = true) == true ||
            path.contains("LanguageServerService", ignoreCase = true)

        val serviceType = defaultServiceType ?: if (isGrpc) "Agentra Hub (gRPC)" else "Bridge (HTTP)"
        val startEntry = NetworkLogEntry(
            id = logId,
            timestamp = startTime,
            url = url,
            path = path,
            method = if (isGrpc) "gRPC" else request.method,
            protocol = if (isGrpc) "HTTP/2" else "HTTP/1.1",
            serviceType = serviceType,
            startTimeMs = startTime,
            durationMs = -1L,
            statusCode = 0,
            requestHeaders = headersToMap(request.headers)
        )
        runCatching { NetworkInspectorManager.recordStart(startEntry) }

        val response = try {
            chain.proceed(request)
        } catch (e: Exception) {
            runCatching {
                NetworkInspectorManager.recordError(
                    logId,
                    e.message ?: e.toString(),
                    System.currentTimeMillis() - startTime
                )
            }
            throw e
        }

        val resHeaders = headersToMap(response.headers)
        val initialGrpcStatus = response.header("grpc-status")
            ?: runCatching { response.trailers().get("grpc-status") }.getOrNull()

        val body = response.body ?: return response.also {
            runCatching {
                NetworkInspectorManager.recordComplete(
                    id = logId,
                    statusCode = response.code,
                    grpcStatus = initialGrpcStatus,
                    responseHeaders = resHeaders,
                    durationMs = System.currentTimeMillis() - startTime
                )
            }
        }

        val forwardingSource = object : ForwardingSource(body.source()) {
            private var finished = false

            override fun read(sink: Buffer, byteCount: Long): Long {
                return try {
                    val bytesRead = super.read(sink, byteCount)
                    if (bytesRead == -1L && !finished) {
                        finished = true
                        val finalGrpc = response.header("grpc-status")
                            ?: runCatching { response.trailers().get("grpc-status") }.getOrNull()
                        runCatching {
                            NetworkInspectorManager.recordComplete(
                                id = logId,
                                statusCode = response.code,
                                grpcStatus = finalGrpc,
                                responseHeaders = resHeaders,
                                durationMs = System.currentTimeMillis() - startTime
                            )
                        }
                    }
                    bytesRead
                } catch (e: Exception) {
                    if (!finished) {
                        finished = true
                        runCatching {
                            NetworkInspectorManager.recordError(
                                id = logId,
                                errorMsg = e.message ?: e.toString(),
                                durationMs = System.currentTimeMillis() - startTime
                            )
                        }
                    }
                    throw e
                }
            }

            override fun close() {
                if (!finished) {
                    finished = true
                    runCatching {
                        NetworkInspectorManager.recordComplete(
                            id = logId,
                            statusCode = response.code,
                            grpcStatus = initialGrpcStatus,
                            responseHeaders = resHeaders,
                            durationMs = System.currentTimeMillis() - startTime
                        )
                    }
                }
                super.close()
            }
        }

        val wrappedBody = object : ResponseBody() {
            override fun contentType(): MediaType? = body.contentType()
            override fun contentLength(): Long = body.contentLength()
            override fun source(): BufferedSource = forwardingSource.buffer()
        }

        return response.newBuilder().body(wrappedBody).build()
    }

    private fun headersToMap(headers: Headers): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (i in 0 until headers.size) {
            val name = headers.name(i)
            val value = if (name.contains("authorization", ignoreCase = true) ||
                name.contains("csrf", ignoreCase = true) ||
                name.contains("token", ignoreCase = true)
            ) {
                val raw = headers.value(i)
                if (raw.length > 10) "${raw.take(6)}...${raw.takeLast(4)}" else "***"
            } else {
                headers.value(i)
            }
            out[name] = value
        }
        return out
    }
}
