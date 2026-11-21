// Material component conventions follow tiann/KernelSU (GPL-3.0); see NOTICE.txt.
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.zopulus.coogle.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material.icons.rounded.PhotoLibrary
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.zopulus.coogle.BuildConfig
import io.github.zopulus.coogle.FeaturePolicy
import io.github.zopulus.coogle.R
import kotlinx.coroutines.delay
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.anim.COUIMoveEaseEasing
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.Card as CouiCard
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.CompleteStateDefaults
import io.github.suqi8.coui.kmp.basic.Icon as CouiIcon
import io.github.suqi8.coui.kmp.basic.IconButton as CouiIconButton
import io.github.suqi8.coui.kmp.basic.Scaffold as CouiScaffold
import io.github.suqi8.coui.kmp.basic.SwitchDefaults as CouiSwitchDefaults
import io.github.suqi8.coui.kmp.basic.TagView
import io.github.suqi8.coui.kmp.basic.TagViewDefaults
import io.github.suqi8.coui.kmp.basic.Text as CouiText
import io.github.suqi8.coui.kmp.basic.TopAppBar as CouiTopAppBar
import io.github.suqi8.coui.kmp.basic.cardListPositionOf
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Back
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.PreferenceCategory
import io.github.suqi8.coui.kmp.preference.PreferenceCategoryMarginStartType
import io.github.suqi8.coui.kmp.preference.SwitchLoadingPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import io.github.suqi8.coui.kmp.layout.DialogDefaults
import io.github.suqi8.coui.kmp.window.WindowDialog

private const val LENS_SETTINGS_TITLE = "Google 智能镜头"

@Composable
fun CoogleScreen(
    state: CoogleState,
    savingKeys: Set<String>,
    style: UiStyle,
    onSetting: (String, Boolean) -> Unit,
    onAssistant: () -> Unit,
    onIconVisibility: (Boolean) -> Unit,
    onStatus: () -> Unit,
    onAbout: () -> Unit,
    onDialogConfirm: () -> Unit,
    onDialogDismiss: () -> Unit,
    onRestart: () -> Unit,
    onUiStyle: (UiStyle) -> Unit
) {
    var lensSettingsOpened by rememberSaveable { mutableStateOf(false) }
    val homeScrollState = rememberScrollState()
    val restartEnabled = state.connected == true && !state.busy && !state.restarting && !state.scopePending && savingKeys.isEmpty()
    val closeLensSettings = { lensSettingsOpened = false }
    BackHandler(enabled = lensSettingsOpened && state.dialog == DialogKind.NONE, onBack = closeLensSettings)
    CoogleTheme(style) {
        val materialMotion = MaterialTheme.motionScheme
        AnimatedContent(
            targetState = lensSettingsOpened,
            transitionSpec = {
                val direction = if (targetState) AnimatedContentTransitionScope.SlideDirection.Start
                    else AnimatedContentTransitionScope.SlideDirection.End
                val position = if (style == UiStyle.COUI) tween<IntOffset>(285, easing = COUIMoveEaseEasing)
                    else materialMotion.defaultSpatialSpec<IntOffset>()
                val opacity = if (style == UiStyle.COUI) tween<Float>(165) else materialMotion.fastEffectsSpec<Float>()
                (slideIntoContainer(direction, position) { it / 5 } + fadeIn(opacity))
                    .togetherWith(slideOutOfContainer(direction, position) { it / 5 } + fadeOut(opacity))
            },
            label = "settings destination"
        ) { showLensSettings ->
            if (showLensSettings) {
                LensSettingsContent(state, savingKeys, onSetting, restartEnabled, onRestart, closeLensSettings)
            } else {
                CoogleContent(state, savingKeys, onSetting, onAssistant, onIconVisibility, onStatus,
                    onAbout, restartEnabled, onRestart, onUiStyle, { lensSettingsOpened = true }, homeScrollState)
            }
        }
        CoogleDialog(state, savingKeys.isNotEmpty(), onDialogConfirm, onDialogDismiss)
    }
}

@Composable
private fun CoogleContent(
    state: CoogleState,
    savingKeys: Set<String>,
    onSetting: (String, Boolean) -> Unit,
    onAssistant: () -> Unit,
    onIconVisibility: (Boolean) -> Unit,
    onStatus: () -> Unit,
    onAbout: () -> Unit,
    restartEnabled: Boolean,
    onRestart: () -> Unit,
    onUiStyle: (UiStyle) -> Unit,
    onLensSettings: () -> Unit,
    scrollState: ScrollState
) {
    val coui = LocalUiStyle.current == UiStyle.COUI
    SettingsScaffold(restartEnabled, onRestart) { padding ->
        val scrollFeedback = if (coui) Modifier.scrollEndHaptic() else Modifier
        Column(Modifier.padding(padding).consumeWindowInsets(padding)
            .then(scrollFeedback).verticalScroll(scrollState).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(12.dp))
            val statusTitle = when (state.connected) { true -> "已激活"; false -> "未激活"; null -> "正在连接…" }
            val statusIcon = when (state.connected) {
                true -> Icons.Rounded.CheckCircle
                false -> Icons.Rounded.HighlightOff
                null -> Icons.Rounded.Extension
            }
            if (coui) {
                val colors = COUITheme.colorScheme
                val tint = when (state.connected) {
                    true -> CompleteStateDefaults.SuccessColor
                    false -> colors.error
                    null -> colors.labelSecondary
                }
                val couiStatusIcon = if (state.connected == true)
                    ImageVector.vectorResource(R.drawable.ic_status_complete) else statusIcon
                CouiCard(modifier = Modifier.fillMaxWidth()) {
                    BasicComponent(
                        title = statusTitle,
                        summary = state.companionSummary,
                        startAction = { CouiIcon(couiStatusIcon, null, Modifier.size(24.dp), tint = tint) },
                        endActions = {
                            TagView("API 102", colors = TagViewDefaults.tagViewColors(
                                containerColor = colors.fillSolid,
                                contentColor = colors.labelPrimary
                            ))
                        },
                        cardListPosition = CardListPosition.Full,
                        onClick = onStatus
                    )
                }
            } else {
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
                            Icon(statusIcon, contentDescription = null)
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
                            Text(statusTitle, style = MaterialTheme.typography.titleMediumEmphasized)
                        }
                    )
                }
            }
            val available = state.canChangeFeature(FeaturePolicy.NAVIGATION, true)
            val hey = state.features[FeaturePolicy.HEY_GOOGLE]
            Group("数字助理") {
                SettingRow(0, 2, Icons.Rounded.RecordVoiceOver, "默认数字助理", state.assistant, onClick = onAssistant)
                ToggleRow(1, 2, Icons.Rounded.Mic, "Hey Google", state.heyDescription,
                    checked = hey, enabled = state.canChangeFeature(FeaturePolicy.HEY_GOOGLE, hey != true),
                    onChange = { onSetting(FeaturePolicy.HEY_GOOGLE, it) }, saving = FeaturePolicy.HEY_GOOGLE in savingKeys)
            }
            Group("系统设置") {
                SettingRow(0, 7, ImageVector.vectorResource(R.drawable.action_lens_color), LENS_SETTINGS_TITLE,
                    "补全Google智能镜头的功能", onClick = onLensSettings)
                ToggleRow(1, 7, Icons.Rounded.TouchApp, "系统导航方式", state.navigationDescription,
                    state.features[FeaturePolicy.NAVIGATION], available, { onSetting(FeaturePolicy.NAVIGATION, it) }, FeaturePolicy.NAVIGATION in savingKeys)
                ToggleRow(2, 7, Icons.Rounded.Search, "Google搜索框", "在桌面底部显示 Google 搜索框",
                    state.features[FeaturePolicy.SEARCH_BAR], available, { onSetting(FeaturePolicy.SEARCH_BAR, it) }, FeaturePolicy.SEARCH_BAR in savingKeys)
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
                UiStyleRow(0, 3, onUiStyle)
                ToggleRow(1, 3, Icons.Rounded.VisibilityOff, "隐藏桌面图标", "隐藏后，可从 LSPosed 中打开",
                    state.iconHidden, true, onIconVisibility)
                SettingRow(2, 3, Icons.Rounded.Info, "关于 Coogle", "zopulus · ${BuildConfig.VERSION_NAME}", onClick = onAbout)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LensSettingsContent(
    state: CoogleState,
    savingKeys: Set<String>,
    onSetting: (String, Boolean) -> Unit,
    restartEnabled: Boolean,
    onRestart: () -> Unit,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    val scrollFeedback = if (LocalUiStyle.current == UiStyle.COUI) Modifier.scrollEndHaptic() else Modifier
    SettingsScaffold(restartEnabled, onRestart, onBack) { padding ->
        Column(Modifier.padding(padding).consumeWindowInsets(padding)
            .then(scrollFeedback).verticalScroll(scrollState).padding(horizontal = 16.dp)) {
            Group(null) {
                LensToggle(0, FeaturePolicy.LENS, ImageVector.vectorResource(R.drawable.lens_talk),
                    "补全说话和创建", "启用智能镜头中的「说话」和「创建」",
                    state, savingKeys, onSetting)
                LensToggle(1, FeaturePolicy.ACTION_BUTTON_LENS, ControlIcons.ActionButton,
                    "快捷键智能镜头", "将快捷键中的「AI 拍一拍」替换为智能镜头",
                    state, savingKeys, onSetting)
                LensToggle(2, FeaturePolicy.GALLERY_LENS, Icons.Rounded.PhotoLibrary,
                    "相机相册智能镜头", "启用相机和相册的智能镜头",
                    state, savingKeys, onSetting)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LensToggle(index: Int, key: String, icon: ImageVector, title: String, summary: String,
    state: CoogleState, savingKeys: Set<String>, onSetting: (String, Boolean) -> Unit) {
    val checked = state.features[key]
    ToggleRow(index, 3, icon, title, summary, checked, state.canChangeFeature(key, checked != true),
        { onSetting(key, it) }, key in savingKeys)
}

@Composable
private fun SettingsScaffold(
    restartEnabled: Boolean,
    onRestart: () -> Unit,
    onBack: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    val title = if (onBack != null) LENS_SETTINGS_TITLE else "Coogle"
    val subtitle = if (onBack != null) "" else "让国行版ColorOS更加Google"
    if (LocalUiStyle.current == UiStyle.COUI) {
        val bar = COUIScrollBehavior()
        CouiScaffold(
            modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                CouiTopAppBar(
                    title = title,
                    subtitle = subtitle,
                    scrollBehavior = bar,
                    navigationIcon = {
                        if (onBack != null) CouiIconButton(onClick = onBack) {
                            CouiIcon(COUIIcons.Back, "返回", Modifier.size(24.dp))
                        }
                    },
                    actions = {
                        CouiIconButton(onClick = onRestart, enabled = restartEnabled) {
                            val colors = COUITheme.colorScheme
                            CouiIcon(ControlIcons.Restart, "重启作用域", Modifier.size(24.dp),
                                tint = if (restartEnabled) colors.labelPrimary else colors.disabledNeutral)
                        }
                    }
                )
            },
            content = content
        )
        return
    }
    val bar = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val background = MaterialTheme.colorScheme.surfaceContainer
    Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        containerColor = background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TwoRowsTopAppBar(
                // Match KernelSU's LargeFlexibleTopAppBar title tokens.
                title = { expanded -> Text(title, style = if (expanded) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleLarge) },
                subtitle = if (subtitle.isEmpty()) null else { expanded ->
                    if (expanded) Text(subtitle, style = MaterialTheme.typography.titleMedium)
                },
                collapsedHeight = TopAppBarDefaults.LargeAppBarCollapsedHeight,
                expandedHeight = if (onBack == null) TopAppBarDefaults.LargeFlexibleAppBarWithSubtitleExpandedHeight
                    else TopAppBarDefaults.LargeAppBarExpandedHeight,
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) {
                        MaterialControlIcon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onRestart, enabled = restartEnabled) {
                        MaterialControlIcon(ControlIcons.Restart, "重启作用域", restartEnabled)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = background, scrolledContainerColor = background),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = bar
            )
        },
        content = content
    )
}

@Composable
private fun MaterialControlIcon(vector: ImageVector, description: String? = null, enabled: Boolean = true) {
    Icon(vector, contentDescription = description, modifier = Modifier.size(24.dp),
        tint = MaterialTheme.colorScheme.secondary.copy(alpha = if (enabled) 1f else 0.38f))
}

@Composable
private fun Group(title: String?, content: @Composable () -> Unit) {
    val coui = LocalUiStyle.current == UiStyle.COUI
    Column(Modifier.padding(top = if (coui) 16.dp else 24.dp)) {
        if (coui) {
            // The page already provides the outer 16 dp inset.
            if (title != null) PreferenceCategory(title, marginStartType = PreferenceCategoryMarginStartType.Small)
            CouiCard(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        } else {
            if (title != null) Text(title, Modifier.padding(start = 16.dp, bottom = 8.dp),
                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
        }
    }
}

@Composable
private fun SettingRow(index: Int, count: Int, icon: ImageVector, title: String, summary: String, onClick: () -> Unit) {
    if (LocalUiStyle.current == UiStyle.COUI) {
        ArrowPreference(
            title = title, summary = summary,
            startAction = { CouiRowIcon(icon) },
            cardListPosition = cardListPositionOf(index, count),
            onClick = onClick
        )
    } else {
        MaterialSettingRow(index, count, icon, title, summary, onClick)
    }
}

@Composable
internal fun MaterialSettingRow(index: Int, count: Int, icon: ImageVector, title: String, summary: String,
    onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }) {
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
        trailingContent = trailing,
        supportingContent = { Text(summary) },
        content = { Text(title) }
    )
}

@Composable
internal fun CouiRowIcon(icon: ImageVector, enabled: Boolean = true) {
    val colors = COUITheme.colorScheme
    CouiIcon(icon, null, Modifier.size(24.dp), tint = if (enabled) colors.primary else colors.disabledNeutral)
}

@Composable
private fun ToggleRow(index: Int, count: Int, icon: ImageVector, title: String, summary: String,
    checked: Boolean?, enabled: Boolean, onChange: (Boolean) -> Unit, saving: Boolean = false) {
    // Brief commits keep the row stable; interaction is blocked for the entire save.
    var showSaving by remember(saving) { mutableStateOf(false) }
    LaunchedEffect(saving) {
        if (saving) {
            delay(250)
            showSaving = true
        }
    }
    val shownSummary = if (showSaving) "正在保存…" else summary
    val rowEnabled = enabled && checked != null && !showSaving
    val toggle = { if (checked != null && !saving) onChange(!checked) }
    val semantics = Modifier.semantics {
        role = Role.Switch
        if (saving || !rowEnabled) disabled()
        if (checked != null) toggleableState = ToggleableState(checked)
        stateDescription = if (saving) "正在保存" else
            when (checked) { true -> "已开启"; false -> "已关闭"; null -> "正在读取" }
    }
    if (LocalUiStyle.current == UiStyle.COUI) {
        if (checked == null) {
            BasicComponent(
                modifier = semantics,
                title = title,
                summary = summary,
                startAction = { CouiRowIcon(icon, enabled = false) },
                endActions = {
                    Box(Modifier.size(CouiSwitchDefaults.TrackWidth, CouiSwitchDefaults.TrackHeight)
                        .background(COUITheme.colorScheme.secondaryContainer, RoundedCornerShape(50)))
                },
                cardListPosition = cardListPositionOf(index, count),
                enabled = false
            )
        } else {
            SwitchLoadingPreference(
                checked = checked,
                onCheckedChange = { if (!saving) onChange(it) },
                title = title,
                modifier = semantics,
                isLoading = showSaving,
                summary = shownSummary,
                startAction = { CouiRowIcon(icon, enabled) },
                cardListPosition = cardListPositionOf(index, count),
                enabled = enabled
            )
        }
        return
    }
    MaterialSettingRow(index, count, icon, title, shownSummary, toggle, rowEnabled, semantics) {
        if (checked == null) {
            Box(Modifier.size(width = 52.dp, height = 32.dp).background(
                MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(16.dp)))
            return@MaterialSettingRow
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled && !showSaving,
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
private fun CoogleDialog(current: CoogleState, saving: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val coui = LocalUiStyle.current == UiStyle.COUI
    val visible = current.dialog != DialogKind.NONE
    var closingContent by remember { mutableStateOf<CoogleState?>(null) }
    val state = if (visible) current else if (coui) closingContent ?: return else return
    if (coui && visible) SideEffect { closingContent = current }
    val title = when (state.dialog) {
        DialogKind.STATUS -> "模块状态"
        DialogKind.RESTART -> "重启作用域"
        DialogKind.INSTALLED -> "安装完成"
        DialogKind.HIDE_ICON -> "隐藏桌面图标？"
        DialogKind.ROOT_REQUIRED -> "需要 Root 授权"
        else -> "操作未完成"
    }
    val message = when (state.dialog) {
        DialogKind.STATUS -> "在 LSPosed 中启用 Coogle 并勾选推荐作用域。更改设置后，重启对应作用域生效。\n\n附属模块提供 Google 系统应用、系统级 Voice Match 组件及圈定即搜实时翻译配置。"
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
    val confirmEnabled = !state.busy && !state.restarting &&
        (state.dialog != DialogKind.RESTART || !saving) &&
        (state.dialog != DialogKind.STATUS || (state.connected == true && !state.scopePending && !state.companionChecking))
    val dismiss = when (state.dialog) {
        DialogKind.STATUS -> "关闭"
        DialogKind.RESTART, DialogKind.HIDE_ICON -> "取消"
        else -> null
    }
    if (coui) {
        val bodyHeight = minOf(220.dp, LocalWindowInfo.current.containerDpSize.height * 0.35f)
        WindowDialog(
            show = visible,
            title = title,
            onDismissRequest = { if (visible) onDismiss() },
            onDismissFinished = { closingContent = null }
        ) {
            CouiText(message, Modifier.fillMaxWidth().heightIn(max = bodyHeight)
                .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
                color = DialogDefaults.summaryColor(),
                style = COUITheme.textStyles.bodyS)
            DialogButtonBar(
                negative = dismiss?.let { DialogButtonBarAction(it, onClick = { if (visible) onDismiss() }) },
                positive = DialogButtonBarAction(confirm, enabled = confirmEnabled, onClick = { if (visible) onConfirm() })
            )
        }
        return
    }
    val icon = when (state.dialog) {
        DialogKind.INSTALLED -> Icons.Outlined.Extension
        DialogKind.RESTART -> ControlIcons.Restart
        DialogKind.HIDE_ICON -> Icons.Rounded.VisibilityOff
        else -> Icons.Rounded.Info
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        icon = {
            if (state.dialog == DialogKind.RESTART) MaterialControlIcon(icon) else Icon(icon, null)
        },
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(message)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmEnabled) { Text(confirm) }
        },
        dismissButton = dismiss?.let { label -> { TextButton(onClick = onDismiss) { Text(label) } } }
    )
}
