package io.github.zopulus.coogle.ui

import io.github.zopulus.coogle.FeaturePolicy

enum class CompanionState(val summary: String) {
    UNKNOWN("附属模块：尚未检测"),
    MISSING("附属模块：未安装"),
    DISABLED("附属模块：已安装未启用"),
    UPDATE_REQUIRED("附属模块：需要更新"),
    READY("附属模块：已安装并启用"),
    PENDING("附属模块：已安装请重启设备")
}

enum class DialogKind { NONE, STATUS, INSTALLED, RESTART, HIDE_ICON, ERROR, ROOT_REQUIRED }

data class CoogleState(
    val connected: Boolean? = null,
    val features: Map<String, Boolean> = emptyMap(),
    val busy: Boolean = false,
    val restarting: Boolean = false,
    val scopePending: Boolean = false,
    val assistant: String = "当前数字助理：读取中…",
    val companion: CompanionState = CompanionState.UNKNOWN,
    val companionChecking: Boolean = false,
    val companionError: String = "",
    val iconHidden: Boolean = false,
    val dialog: DialogKind = DialogKind.NONE,
    val dialogText: String = "",
) {
    fun beginCompanionInstall(): CoogleState = copy(busy = true, dialog = DialogKind.NONE)
    val navigationDescription: String get() = if (companion == CompanionState.MISSING)
        "补全设置中的导航方式，圈定即搜实时翻译功能需点击激活状态卡片安装附属模块" else "补全设置中的导航方式"
    val heyAvailable: Boolean get() = companion == CompanionState.READY || companion == CompanionState.PENDING
    fun canChangeFeature(key: String, enabled: Boolean): Boolean =
        connected == true && !scopePending && !restarting &&
            (key != FeaturePolicy.HEY_GOOGLE || (!busy && (!enabled || heyAvailable)))

    val heyDescription: String get() = if (companion == CompanionState.UNKNOWN || companion == CompanionState.MISSING)
        "补全系统级 Voice Match 功能，需点击激活状态卡片安装附属模块" else "补全系统级 Voice Match 功能"
    val companionSummary: String get() = if (companionError.isNotEmpty())
        "检测失败，$companionError" else companion.summary
    companion object {
        val featureKeys = listOf(FeaturePolicy.HEY_GOOGLE, FeaturePolicy.GMS, FeaturePolicy.GOOGLE_ENTRY,
            FeaturePolicy.NAVIGATION, FeaturePolicy.SEARCH_BAR, FeaturePolicy.LENS,
            FeaturePolicy.ACTION_BUTTON_LENS, FeaturePolicy.WALLET, FeaturePolicy.QUICK_ENTRIES)
    }
}
