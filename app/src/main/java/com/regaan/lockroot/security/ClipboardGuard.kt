package com.regaan.lockroot.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper

class ClipboardGuard(
    context: Context,
    private val handler: Handler = Handler(Looper.getMainLooper()),
) {
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val clearRunnable = Runnable { clearIfAppOwned() }

    fun copySecret(value: String) {
        clipboard.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, value))
        handler.removeCallbacks(clearRunnable)
        handler.postDelayed(clearRunnable, CLEAR_DELAY_MILLIS)
    }

    fun clearIfAppOwned() {
        val label = clipboard.primaryClipDescription?.label?.toString()
        if (label != CLIP_LABEL) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            clipboard.clearPrimaryClip()
        } else {
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }

    fun destroy() {
        handler.removeCallbacks(clearRunnable)
        clearIfAppOwned()
    }

    companion object {
        private const val CLIP_LABEL = "Copied text"
        private const val CLEAR_DELAY_MILLIS = 20_000L
    }
}
