package me.rerere.rikkahub.service

import android.animation.ValueAnimator
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.activity.AssistChatActivity
import org.koin.android.ext.android.inject
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.uuid.Uuid

class AssistBubbleService : Service() {
    private val chatService: ChatService by inject()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var containerView: View? = null
    private var bubbleView: View? = null
    private var dismissView: View? = null
    private var dismissCircle: View? = null
    private var dismissFallAnimator: ValueAnimator? = null
    private var pulseAnimator: ValueAnimator? = null
    private var generationWorking = false
    private var pressed = false
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

        val density = resources.displayMetrics.density
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val bubbleSizePx = (BUBBLE_SIZE_DP * density).roundToInt()
        val marginPx = (SHADOW_MARGIN_DP * density).roundToInt()
        val containerSizePx = bubbleSizePx + marginPx * 2
        val dismissSizePx = (DISMISS_SIZE_DP * density).roundToInt()
        val dismissMarginPx = (DISMISS_MARGIN_BOTTOM_DP * density).roundToInt()
        val screenLoc = IntArray(2)
        var cachedCenter: Pair<Float, Float>? = null

        fun measuredCenter(): Pair<Float, Float>? {
            dismissCircle?.getLocationOnScreen(screenLoc)
            if (screenLoc[0] == 0 && screenLoc[1] == 0) return null
            return (screenLoc[0] + dismissSizePx / 2f) to
                (screenLoc[1] + dismissSizePx / 2f)
        }

        fun dismissCenter(): Pair<Float, Float> =
            cachedCenter ?: measuredCenter() ?: (
                screenWidth / 2f to
                    screenHeight - dismissMarginPx - dismissSizePx / 2f
                )
        val snapThresholdPx = (bubbleSizePx + dismissSizePx) / 2f + DISMISS_MAGNET_DP * density
        val params = WindowManager.LayoutParams(
            containerSizePx,
            containerSizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screenWidth - containerSizePx).coerceAtLeast(0)
            y = (screenHeight / 3)
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

        val circle = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF2E2E2E.toInt())
                setStroke((1.5f * density).roundToInt(), 0x80FFFFFF.toInt())
            }
            alpha = 0f
            layoutParams = FrameLayout.LayoutParams(
                dismissSizePx,
                dismissSizePx,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            ).apply {
                bottomMargin = dismissMarginPx
            }
            addView(
                TextView(context).apply {
                    text = "×"
                    textSize = 28f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER,
                    )
                }
            )
        }
        dismissCircle = circle
        val dismiss = FrameLayout(this).apply {
            visibility = View.GONE
            addView(circle)
        }
        dismissView = dismiss
        val dismissParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var dragged = false
        var inTarget = false
        val touchSlop = density * 8f

        fun hideDismiss() {
            inTarget = false
            cachedCenter = null
            dismissCircle?.animate()?.cancel()
            dismissCircle?.alpha = 0f
            dismissCircle?.translationY = 0f
            dismissCircle?.scaleX = 1f
            dismissCircle?.scaleY = 1f
            dismissView?.visibility = View.GONE
        }

        fun inDismissRange(cx: Float, cy: Float): Boolean {
            val (centerX, centerY) = dismissCenter()
            return hypot(cx - centerX, cy - centerY) < snapThresholdPx
        }

        fun updateTargetHighlight(view: View, inside: Boolean) {
            if (inside == inTarget) return
            inTarget = inside
            if (inside) {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                dismissCircle?.animate()?.scaleX(HOVER_SCALE)?.scaleY(HOVER_SCALE)
                    ?.setDuration(POP_DURATION_MS)?.start()
            } else {
                dismissCircle?.animate()?.scaleX(1f)?.scaleY(1f)
                    ?.setDuration(POP_DURATION_MS)?.start()
            }
        }

        container.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragged = false
                    pressed = true
                    pulseAnimator?.cancel()
                    pulseAnimator = null
                    bubbleView?.animate()
                        ?.scaleX(PRESS_SCALE)
                        ?.scaleY(PRESS_SCALE)
                        ?.setDuration(PRESS_DURATION_MS)
                        ?.start()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (dragged || abs(dx) > touchSlop || abs(dy) > touchSlop) {
                        if (!dragged) {
                            dragged = true
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            dismissView?.visibility = View.VISIBLE
                            dismissCircle?.alpha = 0f
                            dismissCircle?.animate()?.alpha(1f)
                                ?.setDuration(DISMISS_FADE_MS)?.start()
                            dismissCircle?.post {
                                measuredCenter()?.let { cachedCenter = it }
                            }
                        }
                        params.x = (startX + dx).roundToInt()
                            .coerceIn(0, screenWidth - containerSizePx)
                        params.y = (startY + dy).roundToInt()
                            .coerceIn(0, screenHeight - containerSizePx)
                        windowManager?.updateViewLayout(view, params)
                        updateTargetHighlight(
                            view,
                            inDismissRange(event.rawX, event.rawY),
                        )
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    pressed = false
                    if (dragged && inDismissRange(event.rawX, event.rawY)) {
                        inTarget = false
                        val fromY = params.y
                        val toY = (fromY + containerSizePx)
                            .coerceAtMost(screenHeight - containerSizePx)
                        dismissCircle?.animate()?.alpha(0f)
                            ?.translationY(dismissSizePx.toFloat())
                            ?.setDuration(DISMISS_DURATION_MS)
                            ?.setInterpolator(AccelerateInterpolator())?.start()
                        bubbleView?.animate()
                            ?.alpha(0f)
                            ?.setDuration(DISMISS_DURATION_MS)
                            ?.setInterpolator(AccelerateInterpolator())
                            ?.withEndAction {
                                runCatching { windowManager?.removeView(view) }
                                runCatching { windowManager?.removeView(dismissView) }
                                containerView = null
                                bubbleView = null
                                dismissView = null
                                dismissCircle = null
                                stopSelf()
                            }
                            ?.start()
                        val fall = ValueAnimator.ofFloat(0f, 1f).apply {
                            duration = DISMISS_DURATION_MS
                            interpolator = AccelerateInterpolator()
                            addUpdateListener { anim ->
                                val t = anim.animatedValue as Float
                                params.y = (fromY + (toY - fromY) * t).roundToInt()
                                windowManager?.updateViewLayout(view, params)
                            }
                            start()
                        }
                        dismissFallAnimator = fall
                    } else {
                        restoreBubbleScale()
                        hideDismiss()
                        if (!dragged) {
                            startActivity(
                                Intent(this, AssistChatActivity::class.java).apply {
                                    putExtra("conversationId", conversationId.toString())
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                            stopSelf()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    pressed = false
                    hideDismiss()
                    restoreBubbleScale()
                    true
                }
                else -> false
            }
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager?.addView(dismiss, dismissParams)
        windowManager?.addView(container, params)
        containerView = container

        serviceScope.launch {
            chatService.getGenerationJobStateFlow(conversationId).collect { job ->
                setWorking(job != null)
            }
        }
        return START_STICKY
    }

    private fun restoreBubbleScale() {
        val bubble = bubbleView ?: return
        bubble.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(POP_DURATION_MS)
            .setInterpolator(OvershootInterpolator(2f))
            .withEndAction {
                if (pulseAnimator == null && generationWorking) {
                    setWorking(true)
                }
            }
            .start()
    }

    private fun setWorking(working: Boolean) {
        generationWorking = working
        val bubble = bubbleView ?: return
        if (working) {
            if (pressed) return
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
            if (pressed) return
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
        dismissFallAnimator?.cancel()
        dismissFallAnimator = null
        pulseAnimator?.cancel()
        pulseAnimator = null
        dismissView?.let { runCatching { windowManager?.removeView(it) } }
        dismissView = null
        dismissCircle = null
        containerView?.let { windowManager?.removeView(it) }
        containerView = null
        bubbleView = null
        super.onDestroy()
    }

    private companion object {
        const val BUBBLE_SIZE_DP = 56f
        const val SHADOW_MARGIN_DP = 16f
        const val PRESS_SCALE = 0.88f
        const val PRESS_DURATION_MS = 120L
        const val PULSE_DURATION_MS = 800L
        const val POP_DURATION_MS = 250L
        const val DISMISS_SIZE_DP = 72f
        const val DISMISS_MARGIN_BOTTOM_DP = 54f
        const val DISMISS_MAGNET_DP = 16f
        const val DISMISS_FADE_MS = 200L
        const val DISMISS_DURATION_MS = 200L
        const val HOVER_SCALE = 1.15f
    }
}
