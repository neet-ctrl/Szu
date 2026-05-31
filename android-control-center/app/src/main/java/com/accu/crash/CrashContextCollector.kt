package com.accu.crash

import org.json.JSONArray
import org.json.JSONObject

/**
 * Lightweight in-memory session context.
 * Updated by navigation, ViewModels, and services so CrashEngine
 * always has fresh state at the moment of a crash.
 *
 * All writes are @Volatile — safe for cross-thread access without
 * heavier synchronisation overhead during crash recording.
 */
object CrashContextCollector {

    @Volatile var currentRoute: String = "unknown"
    @Volatile var currentActivity: String = "unknown"
    @Volatile var currentFragment: String = ""
    @Volatile var currentViewModel: String = ""
    @Volatile var currentService: String = ""
    @Volatile var shizukuState: String = "UNKNOWN"
    @Volatile var rootState: String = "NONE"
    @Volatile var networkState: String = "UNKNOWN"
    @Volatile var wirelessAdbState: String = "DISCONNECTED"
    @Volatile var sessionId: String = java.util.UUID.randomUUID().toString()
    @Volatile var sessionStartMs: Long = System.currentTimeMillis()

    private val recentActions = ArrayDeque<String>()
    private const val MAX_ACTIONS = 20

    fun recordNavigation(route: String) {
        currentRoute = route
        addAction("nav:$route")
    }

    fun recordAction(label: String) = addAction(label)

    fun recordActivityStart(name: String) {
        currentActivity = name
        addAction("activity:$name")
    }

    fun recordViewModelInit(name: String) { currentViewModel = name }
    fun recordServiceStart(name: String) { currentService = name }

    fun getRecentActionsJson(): String {
        val arr = JSONArray()
        synchronized(recentActions) { recentActions.forEach { arr.put(it) } }
        return arr.toString()
    }

    fun sessionDurationSec(): Long =
        (System.currentTimeMillis() - sessionStartMs) / 1000

    fun resetSession() {
        sessionId = java.util.UUID.randomUUID().toString()
        sessionStartMs = System.currentTimeMillis()
        synchronized(recentActions) { recentActions.clear() }
    }

    private fun addAction(label: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())
        synchronized(recentActions) {
            recentActions.addFirst("[$ts] $label")
            while (recentActions.size > MAX_ACTIONS) recentActions.removeLast()
        }
    }

    fun snapshot(): JSONObject = JSONObject().apply {
        put("route", currentRoute)
        put("activity", currentActivity)
        put("fragment", currentFragment)
        put("viewModel", currentViewModel)
        put("service", currentService)
        put("shizuku", shizukuState)
        put("root", rootState)
        put("network", networkState)
        put("wirelessAdb", wirelessAdbState)
        put("sessionId", sessionId)
        put("sessionDurationSec", sessionDurationSec())
        put("recentActions", JSONArray(getRecentActionsJson()))
    }
}
