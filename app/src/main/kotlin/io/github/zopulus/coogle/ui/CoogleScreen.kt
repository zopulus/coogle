// Material component conventions follow tiann/KernelSU (GPL-3.0); see NOTICE.txt.
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.zopulus.coogle.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.HighlightOff
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SettingsSuggest
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TwoRowsTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.zopulus.coogle.BuildConfig
import io.github.zopulus.coogle.FeaturePolicy
import io.github.zopulus.coogle.R

@Composable
fun CoogleScreen(
    state: CoogleState,
    savingKeys: Set<String>,
    onSetting: (String, Boolean) -> Unit,
    onAssistant: () -> Unit,
    onIconVisibility: (Boolean) -> Unit,
    onStatus: () -> Unit,
    onAbout: () -> Unit,
    onDialogConfirm: () -> Unit,
    onDialogDismiss: () -> Unit,
    onRestart: () -> Unit
) {
    val bar = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TwoRowsTopAppBar(
                // Match KernelSU's LargeFlexibleTopAppBar title tokens; only the expanded row has a subtitle.
                title = { expanded -> Text("Coogle", style = if (expanded) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleLarge) },
                subtitle = { expanded -> if (expanded) Text("让国行版ColorOS更加Google", style = MaterialTheme.typography.titleMedium) },
                collapsedHeight = TopAppBarDefaults.LargeAppBarCollapsedHeight,
                expandedHeight = TopAppBarDefaults.LargeFlexibleAppBarWithSubtitleExpandedHeight,
                actions = {
                    val enabled = state.connected == true && !state.busy && !state.restarting && !state.scopePending && savingKeys.isEmpty()
                    IconButton(onClick = onRestart, enabled = enabled) {
                        ControlIcon(ControlIcons.Restart, "重启作用域", enabled)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = bar
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(12.dp))
            // Same Surface, ListItem and StatusTag geometry as KernelSU HomeMaterial.
            val statusColor = when (state.connected) {
                true -> MaterialTheme.colorScheme.secondaryContainer
                false -> MaterialTheme.colorScheme.errorContainer
                null -> MaterialTheme.colorScheme.surfaceContainerHigh
            }
            val statusContentColor = MaterialTheme.colorScheme.contentColorFor(statusColor)
            Surface(modifier = Modifier.fillMaxWidth(), color = statusColor,
                contentColor = statusContentColor, shape = MaterialTheme.shapes.large,
                onClick = onStatus) {
                ListItem(
                    leadingContent = {
                        Icon(when (state.connected) {
                            true -> Icons.Rounded.CheckCircle
                            false -> Icons.Rounded.HighlightOff
                            null -> Icons.Rounded.Extension
                        },
                            contentDescription = null)
                    },
                    trailingContent = {
                        Box(Modifier.padding(end = 4.dp).background(
                            MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))) {
                            Text("API 102", Modifier.padding(vertical = 2.dp, horizontal = 4.dp),
                                style = MaterialTheme.typography.labelSmallEmphasized,
                                color = MaterialTheme.colorScheme.onPrimary)
                        }
                    },
                    overlineContent = null,
                    supportingContent = {
                        Text(state.companionSummary, style = MaterialTheme.typography.bodyMedium)
                    },
                    verticalAlignment = Alignment.CenterVertically,
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent,
                        contentColor = statusContentColor, leadingContentColor = statusContentColor,
                        trailingContentColor = statusContentColor,
                        supportingContentColor = statusContentColor.copy(alpha = 0.7f)),
                    elevation = ListItemDefaults.elevation(),
                    content = {
                        Text(when (state.connected) { true -> "已激活"; false -> "未激活"; null -> "正在连接…" },
                            style = MaterialTheme.typography.titleMediumEmphasized)
                    }
                )
            }
            val available = state.connected == true && !state.scopePending && !state.restarting
            Group("数字助理") {
                SettingRow(0, 3, Icons.Rounded.RecordVoiceOver, "默认数字助理", state.assistant, onClick = onAssistant)
                ToggleRow(1, 3, Icons.Rounded.Mic, "Hey Google", state.heyDescription,
                    checked = state.features[FeaturePolicy.HEY_GOOGLE], enabled = available && !state.busy && state.heyAvailable,
                    onChange = { onSetting(FeaturePolicy.HEY_GOOGLE, it) }, saving = FeaturePolicy.HEY_GOOGLE in savingKeys)
                ToggleRow(2, 3, ImageVector.vectorResource(R.drawable.action_lens_color), "Google智能镜头", "补全Google智能镜头功能",
                    state.features[FeaturePolicy.LENS], available, { onSetting(FeaturePolicy.LENS, it) }, FeaturePolicy.LENS in savingKeys)
            }
            Group("系统设置") {
                ToggleRow(0, 7, Icons.Rounded.TouchApp, "系统导航方式", state.navigationDescription,
                    state.features[FeaturePolicy.NAVIGATION], available, { onSetting(FeaturePolicy.NAVIGATION, it) }, FeaturePolicy.NAVIGATION in savingKeys)
                ToggleRow(1, 7, Icons.Rounded.Search, "Google搜索框", "在桌面底部显示 Google 搜索框",
                    state.features[FeaturePolicy.SEARCH_BAR], available, { onSetting(FeaturePolicy.SEARCH_BAR, it) }, FeaturePolicy.SEARCH_BAR in savingKeys)
                ToggleRow(2, 7, ControlIcons.ActionButton, "快捷键智能镜头", "将快捷键中AI拍一拍替换为智能镜头",
                    state.features[FeaturePolicy.ACTION_BUTTON_LENS], available, { onSetting(FeaturePolicy.ACTION_BUTTON_LENS, it) }, FeaturePolicy.ACTION_BUTTON_LENS in savingKeys)
                ToggleRow(3, 7, Icons.Rounded.AccountBalanceWallet, "Google钱包", "息屏时双击电源键呼出Google钱包",
                    state.features[FeaturePolicy.WALLET], available, { onSetting(FeaturePolicy.WALLET, it) }, FeaturePolicy.WALLET in savingKeys)
                ToggleRow(4, 7, Icons.Rounded.DashboardCustomize, "快捷入口与控制中心", "补全锁屏快捷入口与下拉控制中心的原生开关",
                    state.features[FeaturePolicy.QUICK_ENTRIES], available, { onSetting(FeaturePolicy.QUICK_ENTRIES, it) }, FeaturePolicy.QUICK_ENTRIES in savingKeys)
                ToggleRow(5, 7, Icons.Rounded.LockOpen, "移除 GMS 使用限制", "移除国行 GMS 限制标记",
                    state.features[FeaturePolicy.GMS], available, { onSetting(FeaturePolicy.GMS, it) }, FeaturePolicy.GMS in savingKeys)
                ToggleRow(6, 7, Icons.Rounded.SettingsSuggest, "设置底部显示 Google", "强制显示设置底部Google",
                    state.features[FeaturePolicy.GOOGLE_ENTRY], available, { onSetting(FeaturePolicy.GOOGLE_ENTRY, it) }, FeaturePolicy.GOOGLE_ENTRY in savingKeys)
            }
            Group("其他设置") {
                ToggleRow(0, 2, Icons.Rounded.VisibilityOff, "隐藏桌面图标", "隐藏后，可从 LSPosed 中打开",
                    state.iconHidden, true, onIconVisibility)
                SettingRow(1, 2, Icons.Rounded.Info, "关于 Coogle", "zopulus · ${BuildConfig.VERSION_NAME}", onClick = onAbout)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    CoogleDialog(state, savingKeys.isNotEmpty(), onDialogConfirm, onDialogDismiss)
}

@Composable
private fun ControlIcon(vector: ImageVector, description: String? = null, enabled: Boolean = true) {
    Icon(vector, contentDescription = description, modifier = Modifier.size(24.dp),
        tint = MaterialTheme.colorScheme.secondary.copy(alpha = if (enabled) 1f else 0.38f))
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 24.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), content = { content() })
    }
}

@Composable
private fun SettingRow(index: Int, count: Int, icon: ImageVector, title: String, summary: String,
    onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null) {
    SegmentedListItem(
        modifier = modifier.fillMaxWidth(),
        shapes = ListItemDefaults.segmentedShapes(index, count).let {
            if (count == 1) it.copy(shape = MaterialTheme.shapes.large) else it
        },
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceBright,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceBright,
            supportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant),
        enabled = enabled,
        onClick = onClick,
        verticalAlignment = Alignment.CenterVertically,
        leadingContent = { Icon(icon, null, Modifier.size(24.dp)) },
        trailingContent = trailing ?: { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
        supportingContent = { Text(summary) },
        content = { Text(title) }
    )
}

@Composable
private fun ToggleRow(index: Int, count: Int, icon: ImageVector, title: String, summary: String,
    checked: Boolean?, enabled: Boolean, onChange: (Boolean) -> Unit, saving: Boolean = false) {
    SettingRow(index, count, icon, title, if (saving) "正在保存…" else summary,
        { if (checked != null) onChange(!checked) }, enabled && checked != null && !saving,
        Modifier.semantics { role = Role.Switch; stateDescription = if (saving) "正在保存" else
            when (checked) { true -> "已开启"; false -> "已关闭"; null -> "正在读取" } }) {
        if (checked == null) {
            Box(Modifier.size(width = 52.dp, height = 32.dp).background(
                MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(16.dp)))
            return@SettingRow
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled && !saving,
            thumbContent = if (checked || enabled) {
                { Icon(if (checked) Icons.Rounded.Check else Icons.Rounded.Close, null, Modifier.size(SwitchDefaults.IconSize)) }
            } else null,
            colors = SwitchDefaults.colors(
                checkedIconColor = MaterialTheme.colorScheme.primary,
                uncheckedIconColor = MaterialTheme.colorScheme.surfaceContainerHighest)
        )
    }
}

@Composable
private fun CoogleDialog(state: CoogleState, saving: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    if (state.dialog == DialogKind.NONE) return
    val title = when (state.dialog) {
        DialogKind.STATUS -> "模块状态"
        DialogKind.RESTART -> "重启作用域"
        DialogKind.INSTALLED -> "安装完成"
        DialogKind.HIDE_ICON -> "隐藏桌面图标？"
        DialogKind.ROOT_REQUIRED -> "需要 Root 授权"
        else -> "操作未完成"
    }
    val message = when (state.dialog) {
        DialogKind.STATUS -> "在 LSPosed 中启用 Coogle 并勾选推荐作用域。更改设置后，重启对应作用域生效。\n\n附属模块提供 Google 系统应用、系统级 Voice Match 组件及圈定即搜实时翻译配置。\n\n${state.companionSummary}"
        DialogKind.RESTART -> "重启目标作用域会关闭 LSPosed 中已勾选的应用，系统界面可能短暂重载。之后请重新打开对应应用。\n\n系统框架及 Hey Google 组件配置需要重启设备才能生效。操作会请求 Root 授权。"
        DialogKind.INSTALLED -> if (state.companion == CompanionState.DISABLED)
            "附属模块已安装，但尚未启用。请在 KernelSU 中启用附属模块后重启设备。"
            else if (state.features[FeaturePolicy.HEY_GOOGLE] == true)
            "附属模块已安装，请重启设备完成挂载，再在 Google 设置中开启 Voice Match。"
            else "附属模块已安装，请重启设备完成挂载。"
        DialogKind.HIDE_ICON -> "隐藏后，仍可从 LSPosed 的 Coogle 模块设置入口打开应用。"
        else -> state.dialogText
    }
    val confirm = when (state.dialog) {
        DialogKind.STATUS -> "安装附属模块"
        DialogKind.RESTART -> "重启目标作用域"
        DialogKind.HIDE_ICON -> "隐藏"
        else -> "知道了"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        icon = {
            if (state.dialog == DialogKind.INSTALLED) Icon(Icons.Outlined.Extension, null)
            else if (state.dialog == DialogKind.RESTART) ControlIcon(ControlIcons.Restart)
            else Icon(if (state.dialog == DialogKind.HIDE_ICON) Icons.Rounded.VisibilityOff else Icons.Rounded.Info, null)
        },
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(message)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !state.busy && !state.restarting &&
                (state.dialog != DialogKind.RESTART || !saving) &&
                (state.dialog != DialogKind.STATUS || (state.connected == true && !state.scopePending && !state.companionChecking))) { Text(confirm) }
        },
        dismissButton = if (state.dialog in setOf(DialogKind.STATUS, DialogKind.RESTART, DialogKind.HIDE_ICON)) {
            { TextButton(onClick = onDismiss) { Text(if (state.dialog == DialogKind.STATUS) "关闭" else "取消") } }
        } else null
    )
}
