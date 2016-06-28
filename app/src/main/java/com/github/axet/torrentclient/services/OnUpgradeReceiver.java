package com.github.axet.torrentclient.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.github.axet.torrentclient.activities.BootActivity;
import com.github.axet.torrentclient.app.MainApplication;

/**
 * http://stackoverflow.com/questions/2133986
 */
public class OnUpgradeReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        boolean start = shared.getBoolean(MainApplication.PREFERENCE_START, false);

        if (start)
            BootActivity.createApplication(context);
    }
}
