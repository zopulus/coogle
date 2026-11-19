package io.github.zopulus.coogle;

import io.github.zopulus.coogle.ui.*;
import java.util.Map;

public final class SettingsUiStateTest {
    public static void main(String[] args) {
        CoogleState initial = new CoogleState();
        check(CoogleState.Companion.getFeatureKeys().contains(FeaturePolicy.ACTION_BUTTON_LENS), "Shortcut switch absent from UI state");
        for (CompanionState status : CompanionState.values()) {
            boolean installHint = state(status, false, "").getNavigationDescription().contains("安装附属模块");
            check(installHint == (status == CompanionState.MISSING), "Navigation installation hint disagrees with companion state: " + status);
        }
        check(initial.getConnected() == null && initial.getFeatures().isEmpty(), "Initial UI claimed inactive or displayed guessed switch values");
        for (CompanionState status : new CompanionState[]{CompanionState.MISSING, CompanionState.DISABLED, CompanionState.PENDING, CompanionState.READY}) {
            String normal = state(status, false, "").getCompanionSummary();
            check(normal.equals(status.getSummary()), "Normal installation summary altered");
            check(state(status, true, "").getCompanionSummary().equals(normal), "Background refresh changed the card summary");
            check(state(status, false, "操作超时，请重试。").getCompanionSummary().equals("检测失败，操作超时，请重试。"), "Detection error concealed or old status leaked");
            check(state(status, false, "操作超时，请重试。").getCompanion() == status, "Detection failure altered the last known installation state");
            CoogleState installing = new CoogleState(true, Map.of(), true, false, false,
                    "", status, false, "", false, DialogKind.NONE, "");
            check(installing.getCompanionSummary().equals(normal), "Installation replaced the last known status");
        }
        check(state(CompanionState.UNKNOWN, false, "无法读取附属模块状态").getCompanionSummary().equals("检测失败，无法读取附属模块状态"), "Unknown failure reported missing");
        for (CompanionState status : CompanionState.values()) {
            CoogleState denied = state(status, false, "请在 KernelSU 中授予 Root 权限后重试。");
            CoogleState retry = denied.beginCompanionInstall();
            check(retry.getBusy() && retry.getDialog() == DialogKind.NONE, "Installation retry did not start");
            check(retry.getCompanionSummary().equals(denied.getCompanionSummary()), "Unconfirmed installation restored a cached successful status");
            check(retry.getCompanion() == status, "Installation retry invented a new installation result");
            check(state(status, true, denied.getCompanionError()).getCompanionSummary().equals(denied.getCompanionSummary()), "Pending detection erased its previous failure");
        }
        check(CompanionState.DISABLED.getSummary().equals("附属模块：已安装未启用"), "Installed disabled module reported missing");
        check(!state(CompanionState.MISSING, false, "").getHeyAvailable(), "Missing support enabled Hey Google toggle");
        check(state(CompanionState.MISSING, false, "").getHeyDescription().equals("补全系统级 Voice Match 功能，需点击激活状态卡片安装附属模块"), "Missing support install instruction changed");
        check(state(CompanionState.READY, false, "").getHeyAvailable() && state(CompanionState.PENDING, false, "").getHeyAvailable(), "Installed support cannot configure Hey Google");
        check(state(CompanionState.READY, false, "").getHeyDescription().equals("补全系统级 Voice Match 功能"), "Hey Google description changed");
        check(!state(CompanionState.DISABLED, false, "").getHeyAvailable(), "Disabled support enabled Hey Google toggle");
        CoogleState disabled = state(CompanionState.DISABLED, false, "");
        check(disabled.canChangeFeature(FeaturePolicy.HEY_GOOGLE, false), "Unavailable support blocked turning Hey Google off");
        check(!disabled.canChangeFeature(FeaturePolicy.HEY_GOOGLE, true), "Unavailable support allowed turning Hey Google on");
        check(disabled.canChangeFeature(FeaturePolicy.LENS, true), "Companion state blocked an independent feature");
        System.out.println("PASS: neutral startup, four installation states, cache, background refresh and failure preservation");
    }
    private static CoogleState state(CompanionState status, boolean checking, String error) {
        return new CoogleState(true, Map.of(FeaturePolicy.HEY_GOOGLE, false), false, false, false,
                "", status, checking, error, false, DialogKind.NONE, "");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
