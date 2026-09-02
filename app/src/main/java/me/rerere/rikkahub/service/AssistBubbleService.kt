package me.rerere.rikkahub.service

import android.animation.ValueAnimator
import android.app.Service
import android.content.Intent
import android.graphics.Outline
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.activity.AssistChatActivity
import org.koin.android.ext.android.inject
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.uuid.Uuid

class AssistBubbleService : Service() {
    private val chatService: ChatService by inject()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var containerView: View? = null
    private var bubbleView: View? = null
    private var pulseAnimator: ValueAnimator? = null
    private var windowManager: WindowManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (containerView != null) return START_STICKY
        val conversationId = intent?.getStringExtra("conversationId")
            ?.let { raw -> runCatching { Uuid.parse(raw) }.getOrNull() }
        if (conversationId == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val bubbleSizePx = (BUBBLE_SIZE_DP * resources.displayMetrics.density).roundToInt()
        val marginPx = (SHADOW_MARGIN_DP * resources.displayMetrics.density).roundToInt()
        val containerSizePx = bubbleSizePx + marginPx * 2
        val params = WindowManager.LayoutParams(
            containerSizePx,
            containerSizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (resources.displayMetrics.widthPixels - containerSizePx).coerceAtLeast(0)
            y = (resources.displayMetrics.heightPixels / 3)
        }

        val container = FrameLayout(this).apply {
            val bubble = ImageView(context).apply {
                setImageResource(R.mipmap.ic_launcher)
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setOval(0, 0, view.width, view.height)
                    }
                }
                elevation = 12f
                layoutParams = FrameLayout.LayoutParams(
                    bubbleSizePx,
                    bubbleSizePx,
                    Gravity.CENTER,
                )
            }
            bubbleView = bubble
            addView(bubble)
        }

        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var dragged = false
        val touchSlop = resources.displayMetrics.density * 8f

        container.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (dragged || abs(dx) > touchSlop || abs(dy) > touchSlop) {
                        dragged = true
                        params.x = (startX + dx).toInt()
                            .coerceIn(0, resources.displayMetrics.widthPixels - containerSizePx)
                        params.y = (startY + dy).toInt()
                            .coerceIn(0, resources.displayMetrics.heightPixels - containerSizePx)
                        windowManager?.updateViewLayout(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragged) {
                        startActivity(
                            Intent(this, AssistChatActivity::class.java).apply {
                                putExtra("conversationId", conversationId.toString())
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                        stopSelf()
                    }
                    true
                }
                else -> false
            }
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager?.addView(container, params)
        containerView = container

        serviceScope.launch {
            chatService.getGenerationJobStateFlow(conversationId).collect { job ->
                setWorking(job != null)
            }
        }
        return START_STICKY
    }

    private fun setWorking(working: Boolean) {
        val bubble = bubbleView ?: return
        if (working) {
            if (pulseAnimator?.isRunning == true) return
            pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = PULSE_DURATION_MS
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { anim ->
                    val t = anim.animatedValue as Float
                    bubble.scaleX = 1f + 0.12f * t
                    bubble.scaleY = 1f + 0.12f * t
                    bubble.alpha = 1f - 0.28f * t
                }
                start()
            }
        } else {
            pulseAnimator?.cancel()
            pulseAnimator = null
            bubble.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(POP_DURATION_MS)
                .start()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        pulseAnimator?.cancel()
        pulseAnimator = null
        containerView?.let { windowManager?.removeView(it) }
        containerView = null
        bubbleView = null
        super.onDestroy()
    }

    private companion object {
        const val BUBBLE_SIZE_DP = 56f
        const val SHADOW_MARGIN_DP = 16f
        const val PULSE_DURATION_MS = 800L
        const val POP_DURATION_MS = 250L
    }
}
