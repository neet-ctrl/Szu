package com.accu.sdk

fun Int.isGranted()            = this == AccuConstants.PERMISSION_GRANTED
fun Int.isDenied()             = this == AccuConstants.PERMISSION_DENIED
fun Int.isNotYetRequested()    = this == AccuConstants.PERMISSION_NOT_YET_REQUESTED
fun Int.isRequestCancelled()   = this == AccuConstants.PERMISSION_REQUEST_CANCELLED
fun Int.isServiceUnavailable() = this == AccuConstants.PERMISSION_SERVICE_UNAVAILABLE

/**
 * Human-readable label for [checkPermission] results.
 * Note: -1 appears as both NOT_YET_REQUESTED (from checkPermission) and
 * REQUEST_CANCELLED (from requestPermission callback). Use [toCallbackLabel]
 * when logging results that came from [AccuClient.requestPermission].
 */
fun Int.toPermissionLabel(): String = when (this) {
    AccuConstants.PERMISSION_GRANTED             -> "PERMISSION_GRANTED"
    AccuConstants.PERMISSION_DENIED              -> "PERMISSION_DENIED"
    AccuConstants.PERMISSION_NOT_YET_REQUESTED   -> "NOT_YET_REQUESTED"
    AccuConstants.PERMISSION_SERVICE_UNAVAILABLE -> "SERVICE_UNAVAILABLE"
    else                                          -> "UNKNOWN($this)"
}

/**
 * Human-readable label specifically for results from [AccuClient.requestPermission].
 * -1 here means the user dismissed the dialog without choosing (REQUEST_CANCELLED),
 * which is distinct from NOT_YET_REQUESTED returned by [AccuClient.checkPermission].
 */
fun Int.toCallbackLabel(): String = when (this) {
    AccuConstants.PERMISSION_GRANTED             -> "PERMISSION_GRANTED"
    AccuConstants.PERMISSION_DENIED              -> "PERMISSION_DENIED"
    AccuConstants.PERMISSION_REQUEST_CANCELLED   -> "REQUEST_CANCELLED"
    AccuConstants.PERMISSION_SERVICE_UNAVAILABLE -> "SERVICE_UNAVAILABLE"
    else                                          -> "UNKNOWN($this)"
}
