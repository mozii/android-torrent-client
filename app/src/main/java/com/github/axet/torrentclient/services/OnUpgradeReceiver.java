package com.github.axet.torrentclient.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.github.axet.torrentclient.activities.BootActivity;
import com.github.axet.torrentclient.app.MainApplication;

/**
 * http://stackoverflow.com/questions/2133986
 */
public class OnUpgradeReceiver extends BroadcastReceiver {
    String TAG = OnUpgradeReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive");

        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);

        if (shared.getBoolean(MainApplication.PREFERENCE_RUN, false)) {
            Log.d(TAG, "Restart Application");
            BootActivity.createApplication(context);
            return;
        }
    }
}
