package com.sihan.selfdiscipline

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var appList: LinearLayout
    private lateinit var serviceStatus: TextView
    private lateinit var statSummary: TextView
    private lateinit var blockedStat: TextView
    private lateinit var totalStat: TextView
    private lateinit var blockEdit: EditText
    private lateinit var blockSoundSummary: TextView
    private lateinit var searchEdit: EditText
    private lateinit var filterGroup: RadioGroup
    private lateinit var filterAll: RadioButton
    private lateinit var filterBlock: RadioButton
    private lateinit var emptyState: TextView

    private val pickBlockSound = 1001
    private var appsCache: List<AppItem> = emptyList()

    private val primary = Color.rgb(54, 92, 245)
    private val success = Color.rgb(45, 157, 99)
    private val background = Color.rgb(246, 248, 252)
    private val card = Color.WHITE
    private val textPrimary = Color.rgb(24, 28, 38)
    private val textSecondary = Color.rgb(105, 111, 124)
    private val softBorder = Color.rgb(232, 235, 241)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = background
        window.navigationBarColor = background
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        buildUi()
        refreshUi()
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun buildUi() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(this@MainActivity.background)
            overScrollMode = View.OVER_SCROLL_NEVER
            isFillViewport = true
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(32))
        }
        scroll.addView(root)
        root.addView(headerCard(), lp(-1, -2, 0, 14))
        root.addView(overviewCard(), lp(-1, -2, 0, 18))
        root.addView(sectionTitle("拦截提示", "进入被拦截应用时立即提醒并阻止继续使用"), lp(-1, -2, 0, 9))
        root.addView(messageCard(), lp(-1, -2, 0, 16))
        root.addView(sectionTitle("提示音", "支持自定义音乐，也可以使用系统短提示音"), lp(-1, -2, 0, 9))
        root.addView(soundCard(), lp(-1, -2, 0, 16))
        root.addView(sectionTitle("应用自律规则", "为每个应用选择你希望它扮演的角色"), lp(-1, -2, 0, 9))
        root.addView(appToolbar(), lp(-1, -2, 0, 10))
        appList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(appList)
        root.addView(aboutCard(), lp(-1, -2, 12, 0))
        setContentView(scroll)
    }

    private fun headerCard(): View {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(21), dp(22), dp(22))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(43, 67, 184), Color.rgb(87, 119, 248))
            ).apply { cornerRadius = dp(26).toFloat() }
        }
        val brandRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val logo = TextView(this).apply {
            text = "自"; textSize = 17f; gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = rounded(Color.argb(50, 255, 255, 255), dp(15), Color.argb(90, 255, 255, 255))
        }
        brandRow.addView(logo, lp(dp(38), dp(38), 0, 0))
        brandRow.addView(TextView(this).apply {
            text = "SELF DISCIPLINE"; textSize = 11f; letterSpacing = 0.18f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.argb(205, 255, 255, 255)); setPadding(dp(10), 0, 0, 0)
        })
        header.addView(brandRow, lp(-1, -2, 0, 16))
        header.addView(TextView(this).apply {
            text = "自律一下"; textSize = 33f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
        }, lp(-1, -2, 0, 4))
        header.addView(TextView(this).apply {
            text = "把想做的事留给自己，把不想做的事交给提醒。"; textSize = 15f
            setTextColor(Color.argb(235, 255, 255, 255))
        }, lp(-1, -2, 0, 17))
        header.addView(TextView(this).apply {
            text = "今天的每一次停顿，都是在保护未来的你。"; textSize = 13f
            setTextColor(Color.argb(225, 245, 248, 255))
            background = rounded(Color.argb(45, 255, 255, 255), dp(16))
            setPadding(dp(12), dp(9), dp(12), dp(9))
        })
        return header
    }

    private fun overviewCard(): View {
        val outer = cardContainer().apply { setPadding(dp(17), dp(16), dp(17), dp(17)) }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(this).apply {
            text = "●"; textSize = 18f; gravity = Gravity.CENTER; setTextColor(success)
            background = rounded(Color.rgb(235, 249, 241), dp(15))
        }, lp(dp(40), dp(40), 0, 0))
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        serviceStatus = TextView(this).apply {
            text = "正在检测监控服务…"; textSize = 17f; setTextColor(textPrimary); typeface = Typeface.DEFAULT_BOLD
        }
        info.addView(serviceStatus)
        statSummary = TextView(this).apply {
            text = "准备开始你的自律规则"; textSize = 12.5f; setTextColor(textSecondary)
        }
        info.addView(statSummary, lp(-1, -2, 2, 0))
        top.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(actionButton("打开设置", primary, true).apply {
            setTextSize(13f); setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener { openAccessibilitySettings() }
        }, lp(dp(94), dp(42), 0, 0))
        outer.addView(top)
        val stats = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(14), 0, 0) }
        stats.addView(statTile("拦截", "0", primary).also { blockedStat = it }, lp(0, dp(64), 0, 0).apply { weight = 1f })
        stats.addView(statTile("鼓励", "0", success).also { welcomeStat = it }, lp(0, dp(64), 7, 0).apply { weight = 1f })
        stats.addView(statTile("应用", "0", Color.rgb(120, 91, 197)).also { totalStat = it }, lp(0, dp(64), 7, 0).apply { weight = 1f })
        outer.addView(stats)
        return outer
    }

    private fun statTile(title: String, value: String, tint: Int): TextView = TextView(this).apply {
        text = "$value\n$title"; textSize = 12f; gravity = Gravity.CENTER; setTextColor(tint)
        typeface = Typeface.DEFAULT_BOLD
        background = rounded(if (tint == success) Color.rgb(238, 250, 244) else Color.rgb(239, 243, 255), dp(17))
        setPadding(dp(8), dp(6), dp(8), dp(6))
    }

    private fun messageCard(): View {
        val outer = cardContainer()
        outer.addView(label("拦截时显示"), lp(-1, -2, 0, 6))
        blockEdit = editBox("例如：先停一下，想清楚再决定。", AppPrefs.blockMessage(this))
        outer.addView(blockEdit, lp(-1, -2, 0, 7))
        outer.addView(actionButton("保存拦截提示", primary, true).apply {
            setOnClickListener {
                AppPrefs.setBlockMessage(this@MainActivity, blockEdit.text.toString().trim().ifEmpty {
                    "先停一下。\n你真的需要现在打开这个应用吗？"
                }); toast("拦截提示已保存")
            }
        }, lp(-1, dp(46), 0, 16))
        return outer
    }

    private fun soundCard(): View {
        val outer = cardContainer()
        outer.addView(soundRow("拦截提示音", "触发拦截时播放；点击关闭或进入后立即停止。", AppPrefs.blockSound(this), primary, pickBlockSound), lp(-1, -2, 0, 14))
        return outer
    }

    private fun soundRow(titleText: String, desc: String, uri: String?, tint: Int, requestCode: Int): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        box.addView(TextView(this).apply {
            text = "♫"; textSize = 19f; gravity = Gravity.CENTER; setTextColor(tint)
            background = rounded(if (tint == success) Color.rgb(235, 249, 241) else Color.rgb(235, 240, 255), dp(14))
        }, lp(dp(42), dp(42), 0, 0))
        val textBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textBox.setPadding(dp(12), 0, dp(8), 0)
        textBox.addView(textView(titleText, 15f, textPrimary, 0, 2).apply { typeface = Typeface.DEFAULT_BOLD })
        val summary = TextView(this).apply {
            text = soundName(uri); textSize = 12f; setTextColor(textSecondary); maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        }
        if (requestCode == pickBlockSound) blockSoundSummary = summary else welcomeSoundSummary = summary
        textBox.addView(summary)
        textBox.addView(textView(desc, 12f, Color.rgb(135, 140, 150), 4, 0))
        box.addView(textBox, LinearLayout.LayoutParams(0, -2, 1f))
        box.addView(actionButton("选择", tint, true).apply {
            setTextSize(13f); setPadding(dp(10), 0, dp(10), 0); setOnClickListener { openAudioPicker(requestCode) }
        }, lp(dp(72), dp(40), 0, 0))
        return box
    }

    private fun appToolbar(): View {
        val outer = cardContainer().apply { setPadding(dp(12), dp(12), dp(12), dp(12)) }
        searchEdit = EditText(this).apply {
            hint = "搜索应用名称"; setHintTextColor(Color.rgb(155, 160, 170)); textSize = 14f
            setSingleLine(true); inputType = InputType.TYPE_CLASS_TEXT; setTextColor(textPrimary)
            setPadding(dp(14), dp(2), dp(14), dp(2)); background = rounded(Color.rgb(248, 249, 252), dp(15), softBorder)
        }
        searchEdit.addTextChangedListener(SimpleTextWatcher { renderApps() })
        outer.addView(searchEdit, lp(-1, dp(44), 0, 10))
        filterGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = rounded(Color.rgb(246, 248, 252), dp(15)); setPadding(dp(3), dp(2), dp(3), dp(2))
        }
        filterAll = filter("全部", textSecondary); filterBlock = filter("拦截", primary)
        filterGroup.addView(filterAll, weightLp()); filterGroup.addView(filterBlock, weightLp())
        filterAll.isChecked = true; filterGroup.setOnCheckedChangeListener { _, _ -> renderApps() }
        outer.addView(filterGroup, lp(-1, dp(43), 0, 0))
        return outer
    }

    private fun aboutCard(): View {
        val outer = cardContainer()
        val title = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        title.addView(TextView(this).apply {
            text = "使用说明"; textSize = 15f; setTextColor(textPrimary); typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, -2, 1f))
        title.addView(TextView(this).apply {
            text = "本地运行"; textSize = 11f; setTextColor(textSecondary)
            background = rounded(Color.rgb(243, 245, 249), dp(15)); setPadding(dp(9), dp(5), dp(9), dp(5))
        })
        outer.addView(title, lp(-1, -2, 0, 7))
        outer.addView(textView(
            "首次使用请在系统“无障碍”设置中开启“自律一下前台应用监控”。服务仅根据前台应用包名执行拦截，不读取应用内部的文字内容。",
            13f, textSecondary, 0, 0
        ))
        return outer
    }

    private fun refreshUi() {
        if (!::serviceStatus.isInitialized) return
        val enabled = isAccessibilityEnabled()
        serviceStatus.text = if (enabled) "自律监控已开启" else "自律监控未开启"
        serviceStatus.setTextColor(if (enabled) success else Color.rgb(207, 61, 72))
        refreshApps()
    }

    private fun refreshApps() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val homePackage = pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
        val excludedPackages = setOfNotNull(packageName, homePackage, "com.android.settings", "com.google.android.packageinstaller", "com.android.packageinstaller")
        appsCache = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .filter { it.activityInfo.packageName !in excludedPackages }
            .distinctBy { it.activityInfo.packageName }
            .map { val ai = it.activityInfo.applicationInfo; AppItem(ai.packageName, ai.loadLabel(pm).toString(), ai.loadIcon(pm)) }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
        updateStats(appsCache); renderApps()
    }

    private fun renderApps() {
        if (!::appList.isInitialized) return
        appList.removeAllViews()
        val keyword = if (::searchEdit.isInitialized) searchEdit.text.toString().trim().lowercase(Locale.getDefault()) else ""
        val selected = if (::filterGroup.isInitialized) filterGroup.checkedRadioButtonId else filterAll.id
        val filterMode = when (selected) { filterBlock.id -> AppPrefs.Mode.BLOCK; else -> null }
        val filtered = appsCache.filter { app ->
            val matchesText = keyword.isBlank() || app.label.lowercase(Locale.getDefault()).contains(keyword)
            val matchesMode = filterMode == null || AppPrefs.mode(this, app.packageName) == filterMode
            matchesText && matchesMode
        }
        if (filtered.isEmpty()) {
            appList.addView(emptyStateView(if (keyword.isBlank()) "还没有符合当前筛选的应用" else "没有找到“$keyword”"))
            return
        }
        filtered.forEach { addAppRow(it) }
    }

    private fun addAppRow(app: AppItem) {
        val outer = cardContainer().apply { setPadding(dp(13), dp(13), dp(13), dp(13)) }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val iconBox = LinearLayout(this).apply { gravity = Gravity.CENTER; background = rounded(Color.rgb(247, 248, 252), dp(15)) }
        iconBox.addView(ImageView(this).apply { setImageDrawable(app.icon) }, LinearLayout.LayoutParams(dp(36), dp(36)))
        top.addView(iconBox, lp(dp(50), dp(50), 0, 0))
        val nameBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(11), 0, dp(8), 0) }
        nameBox.addView(textView(app.label, 16f, textPrimary, 0, 2).apply { typeface = Typeface.DEFAULT_BOLD; maxLines = 1 })
        nameBox.addView(textView(modeDescription(AppPrefs.mode(this, app.packageName)), 12f, textSecondary, 0, 0))
        top.addView(nameBox, LinearLayout.LayoutParams(0, -2, 1f))
        val modeBadge = TextView(this).apply { textSize = 11f; gravity = Gravity.CENTER; setPadding(dp(9), dp(5), dp(9), dp(5)) }
        top.addView(modeBadge, lp(-2, -2, 0, 0))
        outer.addView(top, lp(-1, -2, 0, 10))
        val group = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = rounded(Color.rgb(247, 248, 252), dp(15)); setPadding(dp(3), dp(2), dp(3), dp(2))
        }
        val off = radio("关闭", Color.rgb(104, 111, 124))
        val block = radio("拦截", primary)
        val welcome = radio("鼓励", success)
        group.addView(off, weightLp()); group.addView(block, weightLp()); group.addView(welcome, weightLp())
        when (AppPrefs.mode(this, app.packageName)) {
            AppPrefs.Mode.OFF -> off.isChecked = true
            AppPrefs.Mode.BLOCK -> block.isChecked = true
        }
        updateModeBadge(modeBadge, AppPrefs.mode(this, app.packageName))
        group.setOnCheckedChangeListener { _, checkedId ->
            val mode = if (checkedId == block.id) AppPrefs.Mode.BLOCK else AppPrefs.Mode.OFF
            if (mode == AppPrefs.Mode.OFF) SelfDisciplineAccessibilityService.stopCurrentPrompt()
            AppPrefs.setMode(this, app.packageName, mode)
            updateModeBadge(modeBadge, mode)
            updateStats(appsCache)
            renderApps()
        }
        outer.addView(group); appList.addView(outer, lp(-1, -2, 0, 9))
    }

    private fun modeDescription(mode: AppPrefs.Mode): String = when (mode) {
        AppPrefs.Mode.OFF -> "不提示，不干预"
        AppPrefs.Mode.BLOCK -> "打开时提醒并拦截"
        AppPrefs.Mode.WELCOME -> "打开时欢迎并鼓励"
    }

    private fun updateStats(apps: List<AppItem>) {
        val blocked = apps.count { AppPrefs.mode(this, it.packageName) == AppPrefs.Mode.BLOCK }
        statSummary.text = if (blocked == 0) "从下面选择应用，建立你的第一条自律规则"
        else "已设置 $blocked 个拦截应用"
        if (::blockedStat.isInitialized) {
            blockedStat.text = "$blocked\n拦截"
            totalStat.text = apps.size.toString() + "\n应用"
        }
    }

    private fun refreshStatsOnly() { updateStats(appsCache); renderApps() }

    private fun radio(label: String, tint: Int): RadioButton = RadioButton(this).apply {
        text = label; id = View.generateViewId(); textSize = 13f; setTextColor(textSecondary)
        isAllCaps = false; buttonTintList = ColorStateList.valueOf(tint); gravity = Gravity.CENTER_VERTICAL
    }

    private fun filter(label: String, tint: Int): RadioButton = RadioButton(this).apply {
        text = label; id = View.generateViewId(); textSize = 12.5f; setTextColor(tint); isAllCaps = false
        buttonTintList = ColorStateList.valueOf(tint); gravity = Gravity.CENTER; buttonDrawable = null; setPadding(0, 0, 0, 0)
    }

    private fun weightLp(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, dp(39), 1f)

    private fun updateModeBadge(badge: TextView, mode: AppPrefs.Mode) {
        when (mode) {
            AppPrefs.Mode.OFF -> { badge.text = "关闭"; badge.setTextColor(textSecondary); badge.background = rounded(Color.rgb(241, 243, 247), dp(18)) }
            AppPrefs.Mode.BLOCK -> { badge.text = "拦截"; badge.setTextColor(primary); badge.background = rounded(Color.rgb(235, 240, 255), dp(18)) }
            AppPrefs.Mode.WELCOME -> { badge.text = "鼓励"; badge.setTextColor(success); badge.background = rounded(Color.rgb(235, 249, 241), dp(18)) }
        }
    }

    private fun cardContainer(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(15), dp(16), dp(15))
        background = rounded(card, dp(22), Color.rgb(233, 236, 242)); elevation = dp(2).toFloat()
    }

    private fun emptyStateView(emptyText: String): TextView = TextView(this).apply {
        text = emptyText; textSize = 13f; gravity = Gravity.CENTER; setTextColor(textSecondary)
        background = rounded(Color.WHITE, dp(20), softBorder); setPadding(dp(18), dp(28), dp(18), dp(28))
    }

    private fun editBox(hint: String, value: String): EditText = EditText(this).apply {
        this.hint = hint; setText(value); minLines = 3; gravity = Gravity.TOP; textSize = 15f
        setTextColor(textPrimary); setHintTextColor(Color.rgb(155, 160, 170))
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        setPadding(dp(14), dp(12), dp(14), dp(12)); background = rounded(Color.rgb(248, 249, 252), dp(16), softBorder)
    }

    private fun label(text: String): TextView = textView(text, 13f, textSecondary, 0, 0).apply { typeface = Typeface.DEFAULT_BOLD }

    private fun sectionTitle(title: String, desc: String): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(TextView(this).apply { text = title; textSize = 20f; setTextColor(textPrimary); typeface = Typeface.DEFAULT_BOLD })
        box.addView(TextView(this).apply { text = desc; textSize = 12.5f; setTextColor(textSecondary); setPadding(0, dp(3), 0, 0) })
        return box
    }

    private fun textView(text: String, size: Float, color: Int, top: Int, bottom: Int): TextView = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color)
        if (top != 0 || bottom != 0) setPadding(0, dp(top), 0, dp(bottom))
    }

    private fun actionButton(text: String, color: Int, filled: Boolean): Button = Button(this).apply {
        this.text = text; isAllCaps = false; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; minHeight = 0
        stateListAnimator = null; setTextColor(if (filled) Color.WHITE else color)
        background = if (filled) rounded(color, dp(15)) else rounded(Color.TRANSPARENT, dp(15), color)
    }

    private fun rounded(fill: Int, radius: Int, strokeColor: Int? = null): GradientDrawable = GradientDrawable().apply {
        setColor(fill); cornerRadius = radius.toFloat(); if (strokeColor != null) setStroke(dp(1), strokeColor)
    }

    private fun openAudioPicker(requestCode: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "audio/*"; addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, requestCode)
    }

    @Deprecated("Kept for compatibility with the MVP code path")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
        when (requestCode) {
            pickBlockSound -> { AppPrefs.setBlockSound(this, uri.toString()); if (::blockSoundSummary.isInitialized) blockSoundSummary.text = soundName(uri.toString()) }
        }
        toast("提示音已保存")
    }

    private fun soundName(uriString: String?): String {
        if (uriString.isNullOrBlank()) return "未选择，使用系统短提示音"
        return try {
            contentResolver.query(Uri.parse(uriString), arrayOf("_display_name"), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else "已选择自定义音频"
            } ?: "已选择自定义音频"
        } catch (_: Exception) { "已选择自定义音频" }
    }

    private fun openAccessibilitySettings() { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }

    private fun isAccessibilityEnabled(): Boolean {
        val expected = ComponentName(this, SelfDisciplineAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun lp(width: Int, height: Int, top: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(width, height).apply { topMargin = dp(top); bottomMargin = dp(bottom) }

    data class AppItem(val packageName: String, val label: String, val icon: Drawable)

    private class SimpleTextWatcher(private val changed: () -> Unit) : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = changed()
        override fun afterTextChanged(s: android.text.Editable?) = Unit
    }

}
