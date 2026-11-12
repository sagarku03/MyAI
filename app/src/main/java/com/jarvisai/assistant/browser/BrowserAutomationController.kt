package com.jarvisai.assistant.browser

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Coordinates timed browser automation leases (`busy`) and explicit multi-step locks (`locks`)
 * per browser tab. Automatically expires stale automation leases after [LEASE_TIMEOUT_MS] (60s)
 * so user touch and soft keyboard control are always restored if an AI agent disconnects or crashes.
 */
object BrowserAutomationController {

    private const val LEASE_TIMEOUT_MS = 60_000L
    private const val DEFAULT_BUSY_MS = 3_000L

    private data class BusyEntry(
        val ownerId: String,
        val expiresAtElapsedMs: Long,
        val clearRunnable: Runnable
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()

    // tabId -> (ownerId -> BusyEntry)
    private val busyByTab = mutableMapOf<String, MutableMap<String, BusyEntry>>()

    // tabId -> set of ownerIds holding an explicit agent lock
    private val locksByTab = mutableMapOf<String, MutableSet<String>>()

    // tabId -> runnable that expires stale explicit locks after 60s without refresh
    private val lockWatchdogs = mutableMapOf<String, Runnable>()

    /**
     * Acquires or refreshes a short-lived "light lock" (busy state) for [tabId] owned by [owner].
     * Suppresses the Android soft keyboard during single tool calls and auto-releases after [durationMs].
     */
    fun markBusy(
        tabId: String,
        owner: String = "mcp_agent",
        durationMs: Long = DEFAULT_BUSY_MS
    ) {
        if (tabId.isBlank()) return
        val clampedDuration = durationMs.coerceIn(250L, LEASE_TIMEOUT_MS)
        val expiresAt = SystemClock.elapsedRealtime() + clampedDuration

        synchronized(lock) {
            val tabMap = busyByTab.getOrPut(tabId) { mutableMapOf() }
            tabMap[owner]?.let { existing ->
                mainHandler.removeCallbacks(existing.clearRunnable)
            }
            val runnable = Runnable {
                clearBusy(tabId, owner)
            }
            tabMap[owner] = BusyEntry(
                ownerId = owner,
                expiresAtElapsedMs = expiresAt,
                clearRunnable = runnable
            )
            mainHandler.postDelayed(runnable, clampedDuration)
        }
        syncTabState(tabId)
    }

    /**
     * Releases a busy lease for [owner] on [tabId].
     */
    fun clearBusy(tabId: String, owner: String = "mcp_agent") {
        if (tabId.isBlank()) return
        synchronized(lock) {
            val tabMap = busyByTab[tabId] ?: return@synchronized
            val removed = tabMap.remove(owner)
            if (removed != null) {
                mainHandler.removeCallbacks(removed.clearRunnable)
            }
            if (tabMap.isEmpty()) {
                busyByTab.remove(tabId)
            }
        }
        syncTabState(tabId)
    }

    /**
     * Acquires an explicit full-screen agent lock (`browser_lock`) on [tabId] for [owner].
     * Automatically attaches a 60-second watchdog lease that releases the lock if not refreshed.
     */
    fun lockTab(
        tabId: String,
        owner: String = "mcp_agent",
        leaseMs: Long = LEASE_TIMEOUT_MS
    ) {
        if (tabId.isBlank()) return
        val timeout = leaseMs.coerceIn(1_000L, LEASE_TIMEOUT_MS)
        synchronized(lock) {
            val owners = locksByTab.getOrPut(tabId) { mutableSetOf() }
            owners.add(owner)

            lockWatchdogs[tabId]?.let { mainHandler.removeCallbacks(it) }
            val watchdog = Runnable {
                unlockAll(tabId)
            }
            lockWatchdogs[tabId] = watchdog
            mainHandler.postDelayed(watchdog, timeout)
        }
        syncTabState(tabId)
    }

    /**
     * Releases an explicit agent lock held by [owner] on [tabId].
     */
    fun unlockTab(tabId: String, owner: String = "mcp_agent") {
        if (tabId.isBlank()) return
        synchronized(lock) {
            val owners = locksByTab[tabId]
            owners?.remove(owner)
            if (owners.isNullOrEmpty()) {
                locksByTab.remove(tabId)
                lockWatchdogs.remove(tabId)?.let { mainHandler.removeCallbacks(it) }
            }
        }
        syncTabState(tabId)
    }

    /**
     * Immediately clears all busy and explicit locks for [tabId] (e.g., when the user taps "Unlock").
     */
    fun unlockAll(tabId: String) {
        if (tabId.isBlank()) return
        synchronized(lock) {
            busyByTab.remove(tabId)?.values?.forEach { entry ->
                mainHandler.removeCallbacks(entry.clearRunnable)
            }
            locksByTab.remove(tabId)
            lockWatchdogs.remove(tabId)?.let { mainHandler.removeCallbacks(it) }
        }
        syncTabState(tabId)
    }

    /**
     * Releases all leases across all tabs held by [owner].
     */
    fun releaseOwner(owner: String) {
        val affectedTabs = mutableSetOf<String>()
        synchronized(lock) {
            val busyIter = busyByTab.entries.iterator()
            while (busyIter.hasNext()) {
                val (tabId, ownerMap) = busyIter.next()
                val removed = ownerMap.remove(owner)
                if (removed != null) {
                    mainHandler.removeCallbacks(removed.clearRunnable)
                    affectedTabs.add(tabId)
                }
                if (ownerMap.isEmpty()) {
                    busyIter.remove()
                }
            }

            val lockIter = locksByTab.entries.iterator()
            while (lockIter.hasNext()) {
                val (tabId, owners) = lockIter.next()
                if (owners.remove(owner)) {
                    affectedTabs.add(tabId)
                }
                if (owners.isEmpty()) {
                    lockIter.remove()
                    lockWatchdogs.remove(tabId)?.let { mainHandler.removeCallbacks(it) }
                }
            }
        }
        affectedTabs.forEach { syncTabState(it) }
    }

    fun isTabBusy(tabId: String): Boolean = synchronized(lock) {
        pruneExpiredLocked(tabId)
        !busyByTab[tabId].isNullOrEmpty() || !locksByTab[tabId].isNullOrEmpty()
    }

    fun isTabLocked(tabId: String): Boolean = synchronized(lock) {
        !locksByTab[tabId].isNullOrEmpty()
    }

    private fun pruneExpiredLocked(tabId: String) {
        val now = SystemClock.elapsedRealtime()
        val tabMap = busyByTab[tabId] ?: return
        val iter = tabMap.entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next().value
            if (entry.expiresAtElapsedMs <= now) {
                mainHandler.removeCallbacks(entry.clearRunnable)
                iter.remove()
            }
        }
        if (tabMap.isEmpty()) {
            busyByTab.remove(tabId)
        }
    }

    /**
     * Re-applies the active automation input lock script on page finish if the tab is currently
     * busy or locked, and skips Google Sign-In URLs.
     */
    fun onPageFinished(tab: BrowserTab, url: String?) {
        val shouldLockInput = isTabBusy(tab.id) || isTabLocked(tab.id)
        if (!shouldLockInput) return
        if (BrowserIdentity.isGoogleSignInUrl(url)) return
        mainHandler.post {
            tab.webView?.evaluateJavascript(BrowserAutomationScripts.inputLock(true), null)
        }
    }

    private fun syncTabState(tabId: String) {
        val isBusy: Boolean
        val isLocked: Boolean
        synchronized(lock) {
            pruneExpiredLocked(tabId)
            isBusy = !busyByTab[tabId].isNullOrEmpty() || !locksByTab[tabId].isNullOrEmpty()
            isLocked = !locksByTab[tabId].isNullOrEmpty()
        }

        mainHandler.post {
            val manager = BrowserSessionManager.instance
            val tab = manager.getTab(tabId)
            if (tab != null) {
                val prevInputLock = tab.aiBusy || tab.aiLocked
                tab.aiBusy = isBusy
                tab.aiLocked = isLocked
                val newInputLock = isBusy || isLocked
                if (prevInputLock != newInputLock && !BrowserIdentity.isGoogleSignInUrl(tab.url)) {
                    tab.webView?.evaluateJavascript(
                        BrowserAutomationScripts.inputLock(newInputLock),
                        null
                    )
                }
            }
            val activeTab = manager.getActiveTab()
            if (activeTab == null || activeTab.id == tabId) {
                manager.setTouchLocked(isLocked)
            }
        }
    }
}
