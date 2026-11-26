package com.sameerasw.airsync.utils

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.TimeUnit

/**
 * Reads the currently connected Wi-Fi SSID.
 *
 * Without location permission Android redacts the SSID for apps, but this app is rooted: the
 * shell user can ask the wifi service directly (`cmd wifi status`). We only use this to decide
 * whether a blind reconnect attempt is worth making, so any failure returns null and the caller
 * treats the network as "unknown" (never blocks a reconnect because we could not read the SSID).
 */
object WifiGate {
    private const val TAG = "WifiGate"
    private const val CACHE_MS = 15_000L

    private var cachedSsid: String? = null
    private var cachedAt = 0L

    fun currentSsid(context: Context, force: Boolean = false): String? {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - cachedAt < CACHE_MS) return cachedSsid
        val ssid = readSsidViaShell() ?: readSsidViaWifiManager(context)
        cachedSsid = ssid
        cachedAt = now
        return ssid
    }

    /** Empty whitelist means "any network". Unknown SSID also means "do not block". */
    fun isAllowed(context: Context, allowed: Set<String>): Boolean {
        if (allowed.isEmpty()) return true
        val ssid = currentSsid(context) ?: return true
        return allowed.any { it.equals(ssid, ignoreCase = true) }
    }

    private fun readSsidViaShell(): String? = try {
        val p = ProcessBuilder("su", "-c", "cmd wifi status").redirectErrorStream(true).start()
        val out = StringBuilder()
        val reader = p.inputStream.bufferedReader()
        val pump = Thread {
            runCatching {
                reader.forEachLine { line -> out.append(line).append('\n') }
            }
        }
        pump.isDaemon = true
        pump.start()
        if (!p.waitFor(3, TimeUnit.SECONDS)) {
            p.destroyForcibly()
        }
        parseSsid(out.toString())
    } catch (t: Throwable) {
        Log.d(TAG, "shell SSID read failed: ${t.message}")
        null
    }

    private fun parseSsid(output: String): String? {
        val match = Regex("SSID:\\s*\"?([^\"\\n,]+)\"?").find(output) ?: return null
        val ssid = match.groupValues[1].trim()
        return ssid.takeUnless { it.isEmpty() || it == "<unknown>" }
    }

    @Suppress("DEPRECATION")
    private fun readSsidViaWifiManager(context: Context): String? = try {
        val wm = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
        val ssid = wm?.connectionInfo?.ssid
        if (ssid.isNullOrBlank() || ssid == "<unknown>") null else ssid.trim('"')
    } catch (t: Throwable) {
        null
    }
}
