package me.rerere.rikkahub.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val TAG = "ScreenshotService"

class ScreenshotService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    sealed interface CaptureResult {
        data class Success(val bitmap: Bitmap) : CaptureResult
        data object Throttled : CaptureResult
        data object Failed : CaptureResult
    }

    companion object {
        private var instance: ScreenshotService? = null

        fun isEnabled(): Boolean = instance != null

        @RequiresApi(Build.VERSION_CODES.R)
        suspend fun capture(): CaptureResult {
            val service = instance ?: return CaptureResult.Failed
            val result = suspendCancellableCoroutine { cont ->
                service.takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    { it.run() },
                    object : TakeScreenshotCallback {
                        override fun onSuccess(result: ScreenshotResult) {
                            val buffer = result.hardwareBuffer
                            try {
                                val wrapped = Bitmap.wrapHardwareBuffer(buffer, null)
                                val bitmap = wrapped?.copy(Bitmap.Config.ARGB_8888, false)
                                if (bitmap != null) cont.resume(CaptureResult.Success(bitmap))
                                else cont.resume(CaptureResult.Failed)
                            } finally {
                                buffer.close()
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            Log.w(TAG, "takeScreenshot failed: $errorCode")
                            if (errorCode == ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) {
                                cont.resume(CaptureResult.Throttled)
                            } else {
                                cont.resume(CaptureResult.Failed)
                            }
                        }
                    }
                )
            }
            if (result is CaptureResult.Throttled) {
                kotlinx.coroutines.delay(400)
                return capture()
            }
            return result
        }
    }
}
