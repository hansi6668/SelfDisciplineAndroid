package com.sihan.selfdiscipline

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.view.accessibility.AccessibilityEvent

class SelfDisciplineAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile private var activeInstance: SelfDisciplineAccessibilityService? = null
        fun stopCurrentPrompt() { activeInstance?.stopPromptNow() }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var currentOverlay: View? = null
    private var currentPackage: String? = null
    private var lastEventAt = 0L
    private var tempAllowedUntil = 0L
    private var tempAllowedPackage: String? = null
    private var player: MediaPlayer? = null
    private var tone: ToneGenerator? = null

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeInstance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg == packageName) {
            stopPromptNow()
            currentPackage = pkg
            return
        }
        val now = System.currentTimeMillis()
        if (pkg == currentPackage && now - lastEventAt < 800) return
        lastEventAt = now
        currentPackage = pkg
        evaluatePackage(pkg)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        stopPromptNow()
        if (activeInstance === this) activeInstance = null
        super.onDestroy()
    }

    private fun evaluatePackage(pkg: String) {
        when (AppPrefs.mode(this, pkg)) {
            AppPrefs.Mode.BLOCK -> {
                if (tempAllowedPackage == pkg && System.currentTimeMillis() < tempAllowedUntil) {
                    stopPromptNow()
                    return
                }
                performGlobalAction(GLOBAL_ACTION_HOME)
                showBlockingOverlay(pkg)
                playSound(AppPrefs.blockSound(this))
            }
            AppPrefs.Mode.WELCOME -> {
                showWelcomeOverlay()
                playSound(AppPrefs.welcomeSound(this))
            }
            AppPrefs.Mode.OFF -> stopPromptNow()
        }
    }

    private fun showBlockingOverlay(pkg: String) {
        removeOverlay()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(28), dp(28), dp(28))
            background = roundedBackground(Color.argb(250, 249, 250, 253), dp(28))
            isClickable = true
        }
        root.addView(TextView(this).apply {
            text = "自律提醒"
            textSize = 13f
            setTextColor(Color.rgb(54, 92, 245))
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            background = roundedBackground(Color.rgb(235, 240, 255), dp(30))
            setPadding(dp(14), dp(7), dp(14), dp(7))
        }, centeredLp(-2, -2, 0, 14))
        root.addView(TextView(this).apply {
            text = "先停一下"
            textSize = 30f
            setTextColor(Color.rgb(24, 28, 38))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }, lp(-1, -2, 0, 10))
        root.addView(TextView(this).apply {
            text = AppPrefs.blockMessage(this@SelfDisciplineAccessibilityService)
            textSize = 18f
            setLineSpacing(0f, 1.15f)
            setTextColor(Color.rgb(74, 79, 91))
            gravity = Gravity.CENTER
        }, lp(-1, -2, 0, 22))
        root.addView(TextView(this).apply {
            text = "给自己几秒钟，重新决定现在最重要的事。"
            textSize = 13f
            setTextColor(Color.rgb(128, 133, 145))
            gravity = Gravity.CENTER
        }, lp(-1, -2, 0, 20))
        root.addView(actionButton("回到桌面", Color.rgb(54, 92, 245), true).apply {
            setOnClickListener {
                stopPromptNow()
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }, lp(-1, dp(52), 0, 10))
        root.addView(actionButton("我确定，进入 5 分钟", Color.TRANSPARENT, false).apply {
            setTextColor(Color.rgb(54, 92, 245))
            setOnClickListener {
                stopPromptNow()
                tempAllowedPackage = pkg
                tempAllowedUntil = System.currentTimeMillis() + 5 * 60 * 1000L
                openPackage(pkg)
            }
        }, lp(-1, dp(52), 0, 0))
        addOverlay(root, true)
    }

    private fun showWelcomeOverlay() {
        removeOverlay()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(13), dp(18), dp(13))
            background = roundedBackground(Color.rgb(236, 250, 243), dp(22), Color.rgb(202, 237, 215))
            isClickable = false
        }
        root.addView(TextView(this).apply {
            text = "✓"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(45, 157, 99))
            gravity = Gravity.CENTER
            background = roundedBackground(Color.WHITE, dp(18))
        }, lp(dp(38), dp(38), 0, 0))
        root.addView(TextView(this).apply {
            text = AppPrefs.welcomeMessage(this@SelfDisciplineAccessibilityService)
            textSize = 16f
            setLineSpacing(0f, 1.08f)
            setTextColor(Color.rgb(35, 90, 58))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addOverlay(root, false)
        handler.postDelayed({
            removeOverlay()
            stopSound()
        }, 1800)
    }

    private fun actionButton(text: String, color: Int, filled: Boolean): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
        minHeight = 0
        stateListAnimator = null
        background = if (filled) roundedBackground(color, dp(18))
        else roundedBackground(Color.TRANSPARENT, dp(18), Color.rgb(218, 222, 231))
    }

    private fun addOverlay(view: View, blocking: Boolean) {
        val type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        val flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            if (blocking) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT,
            type, flags, android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = if (blocking) Gravity.CENTER else Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = if (blocking) 0 else dp(72)
        }
        try {
            windowManager?.addView(view, params)
            currentOverlay = view
        } catch (_: Exception) {
            currentOverlay = null
        }
    }

    private fun removeOverlay() {
        currentOverlay?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) { }
        }
        currentOverlay = null
    }

    private fun stopPromptNow() {
        handler.removeCallbacksAndMessages(null)
        removeOverlay()
        stopSound()
    }

    private fun openPackage(pkg: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(pkg) ?: return
        launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        try { startActivity(launchIntent) } catch (_: Exception) { }
    }

    private fun playSound(uriString: String?) {
        stopSound()
        if (uriString.isNullOrBlank()) {
            try {
                tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80).also {
                    it.startTone(ToneGenerator.TONE_PROP_BEEP2, 180)
                }
                val captured = tone
                handler.postDelayed({
                    if (tone === captured) {
                        try { captured?.release() } catch (_: Exception) { }
                        tone = null
                    }
                }, 250)
            } catch (_: Exception) { tone = null }
            return
        }
        try {
            player = MediaPlayer.create(this, Uri.parse(uriString))?.also {
                it.setOnCompletionListener { mp ->
                    try { mp.release() } catch (_: Exception) { }
                    if (player === mp) player = null
                }
                it.start()
            }
        } catch (_: Exception) { player = null }
    }

    private fun stopSound() {
        try { player?.stop() } catch (_: Exception) { }
        try { player?.release() } catch (_: Exception) { }
        player = null
        try { tone?.release() } catch (_: Exception) { }
        tone = null
    }

    private fun roundedBackground(fill: Int, radius: Int, strokeColor: Int? = null): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = radius.toFloat()
        if (strokeColor != null) setStroke(dp(1), strokeColor)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun centeredLp(w: Int, h: Int, top: Int, bottom: Int): LinearLayout.LayoutParams =
        lp(w, h, top, bottom).apply { gravity = Gravity.CENTER_HORIZONTAL }

    private fun lp(w: Int, h: Int, top: Int, bottom: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(w, h).apply { topMargin = dp(top); bottomMargin = dp(bottom) }

}
