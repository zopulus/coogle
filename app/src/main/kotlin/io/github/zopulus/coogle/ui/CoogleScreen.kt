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
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SettingsSuggest
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.zopulus.coogle.BuildConfig
import io.github.zopulus.coogle.FeaturePolicy

@Composable
fun CoogleScreen(
    state: CoogleState,
    onSetting: (String, Boolean) -> Unit,
    onAssistant: () -> Unit,
    onIconVisibility: (Boolean) -> Unit,
    onStatus: () -> Unit,
    onAbout: () -> Unit,
    onDialogConfirm: () -> Unit,
    onDialogDismiss: () -> Unit
) {
    val bar = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Coogle") },
                subtitle = { Text("让国行版ColorOS更加Google") },
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
            val statusColor = if (state.connected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.errorContainer
            val statusContentColor = MaterialTheme.colorScheme.contentColorFor(statusColor)
            Surface(modifier = Modifier.fillMaxWidth(), color = statusColor,
                contentColor = statusContentColor, shape = MaterialTheme.shapes.large,
                onClick = onStatus) {
                ListItem(
                    leadingContent = {
                        Icon(if (state.connected) Icons.Rounded.CheckCircle else Icons.Rounded.HighlightOff,
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
                        Text("版本 ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
                    },
                    verticalAlignment = Alignment.CenterVertically,
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent,
                        contentColor = statusContentColor, leadingContentColor = statusContentColor,
                        trailingContentColor = statusContentColor,
                        supportingContentColor = statusContentColor.copy(alpha = 0.7f)),
                    elevation = ListItemDefaults.elevation(),
                    content = {
                        Text(if (state.connected) "已激活" else "未激活",
                            style = MaterialTheme.typography.titleMediumEmphasized)
                    }
                )
            }
            val available = state.connected
            Group("数字助理") {
                SettingRow(0, 4, Icons.Rounded.RecordVoiceOver, "默认数字助理", state.assistant, onClick = onAssistant)
                ToggleRow(1, 4, Icons.Rounded.Mic, "开启 Hey Google", state.companion.summary,
                    checked = state.features[FeaturePolicy.HEY_GOOGLE] == true, enabled = available && !state.busy,
                    onChange = { onSetting(FeaturePolicy.HEY_GOOGLE, it) })
                ToggleRow(2, 4, Icons.Rounded.CenterFocusStrong, "Google智能镜头", "补全Google智能镜头功能",
                    state.features[FeaturePolicy.LENS] == true, available, { onSetting(FeaturePolicy.LENS, it) })
                ToggleRow(3, 4, Icons.Rounded.PowerSettingsNew, "电源键长按原生小布", "电源键长按走 ColorOS 原生小布通道并保留完整动效，圈定即搜与角落手势不受影响；需重启系统界面作用域",
                    state.features[FeaturePolicy.NATIVE_POWER_ASSISTANT] == true, available, { onSetting(FeaturePolicy.NATIVE_POWER_ASSISTANT, it) })
            }
            Group("系统设置") {
                ToggleRow(0, 6, Icons.Rounded.TouchApp, "系统导航方式", "补全设置中的导航方式",
                    state.features[FeaturePolicy.NAVIGATION] == true, available, { onSetting(FeaturePolicy.NAVIGATION, it) })
                ToggleRow(1, 6, Icons.Rounded.Search, "Google搜索框", "在桌面底部显示 Google 搜索框",
                    state.features[FeaturePolicy.SEARCH_BAR] == true, available, { onSetting(FeaturePolicy.SEARCH_BAR, it) })
                ToggleRow(2, 6, Icons.Rounded.AccountBalanceWallet, "Google钱包", "启用双击电源键呼出Google钱包，需将Google钱包设置为默认付款应用",
                    state.features[FeaturePolicy.WALLET] == true, available, { onSetting(FeaturePolicy.WALLET, it) })
                ToggleRow(3, 6, Icons.Rounded.DashboardCustomize, "快捷入口与控制中心", "补全锁屏快捷入口与下拉控制中心的原生开关",
                    state.features[FeaturePolicy.QUICK_ENTRIES] == true, available, { onSetting(FeaturePolicy.QUICK_ENTRIES, it) })
                ToggleRow(4, 6, Icons.Rounded.LockOpen, "移除 GMS 使用限制", "移除国行 GMS 限制标记",
                    state.features[FeaturePolicy.GMS] == true, available, { onSetting(FeaturePolicy.GMS, it) })
                ToggleRow(5, 6, Icons.Rounded.SettingsSuggest, "设置底部显示 Google", "强制显示设置底部Google",
                    state.features[FeaturePolicy.GOOGLE_ENTRY] == true, available, { onSetting(FeaturePolicy.GOOGLE_ENTRY, it) })
            }
            Group("其他设置") {
                ToggleRow(0, 2, Icons.Rounded.VisibilityOff, "隐藏桌面图标", "隐藏后，可从 LSPosed 打开",
                    state.iconHidden, true, onIconVisibility)
                SettingRow(1, 2, Icons.Rounded.Info, "关于 Coogle", "zopulus · ${BuildConfig.VERSION_NAME}", onClick = onAbout)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    CoogleDialog(state, onDialogConfirm, onDialogDismiss)
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
    checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(index, count, icon, title, summary, { onChange(!checked) }, enabled,
        Modifier.semantics { role = Role.Switch; stateDescription = if (checked) "已开启" else "已关闭" }) {
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
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
private fun CoogleDialog(state: CoogleState, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    if (state.dialog == DialogKind.NONE) return
    val title = when (state.dialog) {
        DialogKind.STATUS -> "模块状态"
        DialogKind.INSTALL -> "安装附属模块"
        DialogKind.DISABLED -> "附属模块已禁用"
        DialogKind.INSTALLED -> "安装完成"
        DialogKind.HIDE_ICON -> "隐藏桌面图标？"
        else -> "操作未完成"
    }
    val message = when (state.dialog) {
        DialogKind.STATUS -> "在 LSPosed 中启用 Coogle 并勾选推荐作用域。更改设置后，重启对应作用域生效。\n\n系统兼容性仅检查 ColorOS 17。"
        DialogKind.INSTALL -> "Hey Google 需要 Coogle 附属模块。安装会请求 Root 授权。\n\n首次安装附属模块后，需要重启手机完成挂载，再在 Google 设置中完成 Voice Match。"
        DialogKind.DISABLED -> "请在 KernelSU 中启用 Coogle 附属模块，然后重新开启 Hey Google。"
        DialogKind.INSTALLED -> "Coogle 附属模块已安装，Hey Google 开关已开启。\n\n请重启手机完成附属模块挂载，再在 Google 设置中完成 Voice Match。"
        DialogKind.HIDE_ICON -> "隐藏后，仍可从 LSPosed 的 Coogle 模块设置入口打开应用。"
        else -> state.dialogText
    }
    val confirm = when (state.dialog) {
        DialogKind.INSTALL -> "安装"
        DialogKind.HIDE_ICON -> "隐藏"
        else -> "知道了"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        icon = { Icon(if (state.dialog in setOf(DialogKind.INSTALL, DialogKind.INSTALLED, DialogKind.DISABLED))
            Icons.Rounded.Extension else if (state.dialog == DialogKind.HIDE_ICON) Icons.Rounded.VisibilityOff else Icons.Rounded.Info, null) },
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(message)
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = if (state.dialog in setOf(DialogKind.INSTALL, DialogKind.HIDE_ICON)) {
            { TextButton(onClick = onDismiss) { Text("取消") } }
        } else null
    )
}
