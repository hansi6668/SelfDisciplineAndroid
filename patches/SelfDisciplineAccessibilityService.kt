package com.sihan.selfdiscipline

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioFocusRequest
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
    private var protectedBlockPackage: String? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeInstance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // 主动设置事件类型，避免部分 Android/厂商 ROM 使用资源 XML 的事件配置不完整。
        serviceInfo = serviceInfo?.apply {
            eventTypes =
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOWS_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 0
            flags = flags or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = resolveEventPackage(event) ?: return

        // 同一个前台应用会连续产生大量内容变化事件。
        // 只在“真正切换到另一个应用”时触发一次，避免鼓励/拦截界面反复创建导致闪烁。
        if (pkg == currentPackage) {
            return
        }

        currentPackage = pkg

        if (pkg == packageName) {
            stopPromptNow()
            return
        }

        // 拦截页出现后，HOME 事件及其后续窗口变化不应关闭拦截页。
        if (protectedBlockPackage != null && pkg == resolveHomePackage()) {
            return
        }

        evaluatePackage(pkg)
    }

    private fun resolveEventPackage(event: AccessibilityEvent?): String? {
        val direct = event?.packageName?.toString()?.takeIf { it.isNotBlank() && it != "android" }
        if (direct != null) return direct

        return try {
            rootInActiveWindow?.packageName?.toString()?.takeIf {
                it.isNotBlank() && it != "android"
            }
        } catch (_: Exception) {
            null
        }
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
                    return
                }

                protectedBlockPackage = pkg
                showBlockingOverlay(pkg)
                playSound(AppPrefs.blockSound(this))

                // 拦截到以后把目标应用送回桌面，但不再因为后续桌面事件重建/关闭提示。
                performGlobalAction(GLOBAL_ACTION_HOME)
            }

            AppPrefs.Mode.WELCOME -> {
                protectedBlockPackage = null
                showWelcomeOverlay()
                playSound(AppPrefs.welcomeSound(this))
            }

            AppPrefs.Mode.OFF -> stopPromptNow()
        }
    }

    private fun resolveHomePackage(): String? {
        return try {
            packageManager.resolveActivity(
                android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(
                    android.content.Intent.CATEGORY_HOME
                ),
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
            )?.activityInfo?.packageName
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveHomePackage(): String? {
        return try {
            packageManager.resolveActivity(
                android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(
                    android.content.Intent.CATEGORY_HOME
                ),
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
            )?.activityInfo?.packageName
        } catch (_: Exception) {
            null
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
        protectedBlockPackage = null
    }

    private fun openPackage(pkg: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(pkg) ?: return
        launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        try { startActivity(launchIntent) } catch (_: Exception) { }
    }

    private fun playSound(uriString: String?) {
        stopSound()

        requestAudioFocus()

        if (uriString.isNullOrBlank()) {
            try {
                tone = ToneGenerator(AudioManager.STREAM_MUSIC, 90).also {
                    it.startTone(ToneGenerator.TONE_PROP_BEEP2, 260)
                }
                val captured = tone
                handler.postDelayed({
                    if (tone === captured) {
                        try { captured?.release() } catch (_: Exception) { }
                        tone = null
                    }
                }, 350)
            } catch (_: Exception) {
                tone = null
            }
            return
        }

        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            mp.setVolume(1.0f, 1.0f)

            val soundUri = Uri.parse(uriString)
            if (soundUri.scheme == "file") {
                val path = soundUri.path ?: throw IllegalArgumentException("audio path is empty")
                mp.setDataSource(path)
            } else {
                mp.setDataSource(this, soundUri)
            }

            mp.setOnPreparedListener { prepared ->
                if (player === prepared) {
                    try { prepared.start() } catch (_: Exception) { }
                } else {
                    try { prepared.release() } catch (_: Exception) { }
                }
            }
            mp.setOnCompletionListener { completed ->
                if (player === completed) player = null
                try { completed.release() } catch (_: Exception) { }
                abandonAudioFocus()
            }
            mp.setOnErrorListener { failed, _, _ ->
                if (player === failed) player = null
                try { failed.release() } catch (_: Exception) { }
                abandonAudioFocus()
                try {
                    tone = ToneGenerator(AudioManager.STREAM_MUSIC, 90).also {
                        it.startTone(ToneGenerator.TONE_PROP_BEEP2, 220)
                    }
                } catch (_: Exception) { }
                true
            }

            player = mp
            mp.prepareAsync()
        } catch (_: Exception) {
            player = null
            try {
                tone = ToneGenerator(AudioManager.STREAM_MUSIC, 90).also {
                    it.startTone(ToneGenerator.TONE_PROP_BEEP2, 220)
                }
            } catch (_: Exception) { }
        }
    }

    private fun requestAudioFocus() {
        try {
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            if (android.os.Build.VERSION.SDK_INT >= 26) {
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attrs)
                    .setAcceptsDelayedFocusGain(false)
                    .build()
                audioFocusRequest = request
                audioManager.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }
        } catch (_: Exception) { }
    }

    private fun abandonAudioFocus() {
        try {
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (_: Exception) { }
    }

    private fun stopSound() {
        try { player?.stop() } catch (_: Exception) { }
        try { player?.release() } catch (_: Exception) { }
        player = null
        try { tone?.release() } catch (_: Exception) { }
        tone = null
        abandonAudioFocus()
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
