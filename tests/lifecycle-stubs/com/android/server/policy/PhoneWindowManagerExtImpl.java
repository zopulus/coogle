package com.android.server.policy;

import android.content.Context;

public final class PhoneWindowManagerExtImpl {
    public Context mContext = new Context();
    public int mCurrentUserId = 11;
    public boolean mPowerButtonRedefineFeatureEnabled;
    public boolean mIsOneplusExp;
    public static int sSosPowerKeyDownCount = 5;
    public boolean isCameraGestureEnabled() {
        return mIsOneplusExp && mPowerButtonRedefineFeatureEnabled && sSosPowerKeyDownCount != 3;
    }
}
