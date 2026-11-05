package com.android.server.voiceinteraction;
public class VoiceInteractionManagerService {
    public void onUserUnlocking(Object user) {}
    public static class VoiceInteractionManagerServiceStub {
        public void systemRunning() {}
        public static class RoleObserver {
            public void onRoleHoldersChanged(String role, Object user) {}
        }
    }
}
