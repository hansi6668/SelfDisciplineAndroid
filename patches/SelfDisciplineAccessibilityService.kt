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
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin
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
    private var melodyTrack: AudioTrack? = null
    private var welcomedPackage: String? = null

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
        val type = event?.eventType ?: return
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            return
        }

        val pkg = resolveEventPackage(event) ?: return
        val homePackage = resolveHomePackage()

        // 拦截模式不做“同包去重”：只要目标 App 成为前台且拦截层不存在，就立即恢复。
        if (protectedBlockPackage != null) {
            if (pkg == protectedBlockPackage &&
                (currentOverlay == null || !currentOverlay!!.isAttachedToWindow)) {
                if (tempAllowedPackage == pkg &&
                    System.currentTimeMillis() < tempAllowedUntil) {
                    return
                }

                showBlockingOverlay(pkg)
                playSound(AppPrefs.blockSound(this), true)
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
            return
        }

        // 自律一下自己的界面不触发规则。
        if (pkg == packageName) {
            return
        }

        // 回到桌面后允许下一次重新触发。
        if (homePackage != null && pkg == homePackage) {
            currentPackage = null
            welcomedPackage = null
            return
        }

        // 系统窗口不改变当前真实 App。
        if (pkg == "android" ||
            pkg == "com.android.systemui" ||
            pkg == "com.google.android.permissioncontroller" ||
            pkg == "com.android.permissioncontroller") {
            return
        }

        val mode = AppPrefs.mode(this, pkg)

        if (mode == AppPrefs.Mode.BLOCK) {
            if (tempAllowedPackage == pkg &&
                System.currentTimeMillis() < tempAllowedUntil) {
                return
            }

            // 只要拦截层不存在，就认为这次进入需要拦截。
            if (currentOverlay == null || !currentOverlay!!.isAttachedToWindow) {
                stopSound()
                protectedBlockPackage = pkg
                currentPackage = pkg
                showBlockingOverlay(pkg)
                playSound(AppPrefs.blockSound(this), true)
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
            return
        }

        // 鼓励只在从其他 App 切换进来时触发一次。
        if (pkg == currentPackage) return

        stopPromptNow()
        currentPackage = pkg
        evaluatePackage(pkg)
    }

    private fun evaluatePackage(pkg: String) {
        when (AppPrefs.mode(this, pkg)) {
            AppPrefs.Mode.BLOCK -> {
                if (tempAllowedPackage == pkg &&
                    System.currentTimeMillis() < tempAllowedUntil) {
                    return
                }

                protectedBlockPackage = pkg
                showBlockingOverlay(pkg)
                playSound(AppPrefs.blockSound(this), true)
                performGlobalAction(GLOBAL_ACTION_HOME)
            }

            AppPrefs.Mode.WELCOME -> {
                protectedBlockPackage = null
                welcomedPackage = pkg
                showWelcomeOverlay()
                playSound(AppPrefs.welcomeSound(this), false)
            }

            AppPrefs.Mode.OFF -> Unit
        }
    }


