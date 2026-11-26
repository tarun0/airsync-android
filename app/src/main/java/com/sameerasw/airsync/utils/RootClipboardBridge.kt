package com.sameerasw.airsync.utils

import android.content.Context
import android.util.Base64
import android.util.Log
import com.sameerasw.airsync.data.local.DataStoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Optional background clipboard capture that does not need an Accessibility service.
 *
 * Android only lets the focused app (or the default IME) read the clipboard, but the `shell`
 * user (uid 2000) is granted READ_CLIPBOARD_IN_BACKGROUND. We ship a small dex
 * (assets/clipwatch.dex) and run it through app_process as uid 2000 via `su`; it subscribes to
 * clipboard changes (no polling) and prints each new text as `CLIP <base64>` on stdout, which
 * we forward over the existing WebSocket.
 *
 * Requires root (KernelSU/Magisk) for this app. Off by default; users who cannot grant root or
 * do not want it keep using the Accessibility service, the tile, or the share sheet.
 */
object RootClipboardBridge {
    private const val TAG = "RootClipBridge"
    private const val DEX_PATH = "/data/local/tmp/airsync-clipwatch.dex"
    private const val MAIN_CLASS = "ClipWatch"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var prefJob: Job? = null
    private var helper: Process? = null
    private var reader: Thread? = null
    private var appContext: Context? = null

    fun startWithPreference(context: Context) {
        appContext = context.applicationContext
        prefJob?.cancel()
        prefJob = scope.launch {
            DataStoreManager.getInstance(context).getRootClipboardEnabled().collect { enabled ->
                if (enabled) startHelper(context) else stopHelper()
            }
        }
    }

    fun stop() {
        prefJob?.cancel()
        prefJob = null
        stopHelper()
    }

    private fun startHelper(context: Context) {
        if (helper?.isAlive == true) return

        if (!deployDex(context)) {
            Log.w(TAG, "helper dex not installed (root denied or su missing)")
            return
        }

        // Any helper left over from a previous app process would double-send.
        runCatching {
            ProcessBuilder("su", "-c", "pkill -f \"[a]irsync-clipwatch\\.dex\"").start().waitFor()
        }

        val p = runCatching {
            ProcessBuilder(
                "su", "2000", "-c",
                "CLASSPATH=$DEX_PATH /system/bin/app_process /system/bin $MAIN_CLASS"
            ).redirectErrorStream(true).start()
        }.onFailure { Log.w(TAG, "su failed: ${it.message}") }.getOrNull() ?: return

        helper = p
        Log.d(TAG, "helper started")
        reader = Thread { readLoop(p) }.also {
            it.isDaemon = true
            it.start()
        }
    }

    private fun readLoop(p: Process) {
        try {
            BufferedReader(InputStreamReader(p.inputStream)).use { r ->
                while (true) {
                    val line = r.readLine() ?: break
                    if (line.startsWith("CLIP ")) {
                        forward(line.substring(5))
                    } else {
                        Log.d(TAG, "helper: $line")
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "helper reader ended: ${e.message}")
        }
        if (helper === p) helper = null
    }

    private fun forward(base64: String) {
        val context = appContext ?: return
        val text = runCatching {
            String(Base64.decode(base64, Base64.DEFAULT), Charsets.UTF_8)
        }.getOrNull()
        if (text.isNullOrBlank()) return

        scope.launch {
            val dataStore = DataStoreManager.getInstance(context)
            if (!dataStore.getClipboardSyncEnabled().first()) return@launch
            if (!WebSocketUtil.isConnected()) return@launch
            ClipboardSyncManager.syncTextToDesktop(text)
        }
    }

    private fun stopHelper() {
        val p = helper
        helper = null
        if (p != null) {
            runCatching { p.destroy() }
            runCatching {
                ProcessBuilder("su", "-c", "pkill -f \"[a]irsync-clipwatch\\.dex\"").start().waitFor()
            }
        }
    }

    /** Copies the bundled helper dex where uid 2000 can read it. Needs root. */
    private fun deployDex(context: Context): Boolean {
        return runCatching {
            val bytes = context.assets.open("clipwatch.dex").readBytes()
            val p = ProcessBuilder(
                "su", "-c", "cat > $DEX_PATH && chmod 644 $DEX_PATH"
            ).start()
            p.outputStream.use { it.write(bytes) }
            p.waitFor() == 0
        }.getOrDefault(false)
    }
}
