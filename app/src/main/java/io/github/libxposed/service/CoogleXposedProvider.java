// SPDX-License-Identifier: Apache-2.0
// Adapted from libxposed/service's XposedProvider (https://github.com/libxposed/service).
package io.github.libxposed.service;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;

public final class CoogleXposedProvider extends ContentProvider {
    @Override public boolean onCreate() {
        // Coogle runs on Android 15 or later and targets Android R or later.
        RemotePreferences.shouldNotifyCleared = true;
        return true;
    }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        // LSPosed delivers the service from its root daemon.
        if (Binder.getCallingUid() != Process.ROOT_UID) {
            throw new SecurityException("Caller is not authorized to connect to Coogle");
        }
        if (!IXposedService.SEND_BINDER.equals(method) || extras == null) return null;
        IBinder binder = extras.getBinder("binder");
        if (binder == null) return null;
        XposedServiceHelper.onBinderReceived(binder);
        return new Bundle();
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sortOrder) {
        return null;
    }

    @Override public String getType(Uri uri) { return null; }

    @Override public Uri insert(Uri uri, ContentValues values) { return null; }

    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }

    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}
