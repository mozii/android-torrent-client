package com.github.axet.torrentclient.app;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.torrentclient.R;

import java.text.SimpleDateFormat;
import java.util.Date;

import go.libtorrent.Libtorrent;

public class MainApplication extends Application {
    final String TAG = MainApplication.class.getSimpleName();

    public static final String PREFERENCE_STORAGE = "storage_path";
    public static final String PREFERENCE_THEME = "theme";
    public static final String PREFERENCE_ANNOUNCE = "announces_list";
    public static final String PREFERENCE_START = "start_at_boot";
    public static final String PREFERENCE_WIFI = "wifi";
    public static final String PREFERENCE_LAST_PATH = "lastpath";
    public static final String PREFERENCE_DIALOG = "dialog";
    public static final String PREFERENCE_RUN = "run";

    public static final String NA = "N/A";

    public static final String SAVE_STATE = MainApplication.class.getName() + ".SAVE_STATE";

    Storage storage;

    SaveState savestate;

    class SaveState extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive" + intent);
            if (intent.getAction().equals(SAVE_STATE)) {
                Storage s = getStorage();
                if (s != null)
                    s.save();
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");

        PreferenceManager.setDefaultValues(this, R.xml.pref_general, false);

        Context context = this;
        context.setTheme(getUserTheme());
    }

    public void create() {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor edit =  shared.edit();
        edit.putBoolean(PREFERENCE_RUN, true);
        edit.commit();

        if (storage == null) {
            storage = new Storage(this);
            storage.create();
        }
        if (savestate == null) {
            savestate = new SaveState();
            IntentFilter filter = new IntentFilter();
            filter.addAction(SAVE_STATE);
            filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
            filter.addAction(Intent.ACTION_MY_PACKAGE_REPLACED);
            filter.addAction(Intent.ACTION_PACKAGE_RESTARTED);
            registerReceiver(savestate, filter);
        }
    }

    public void close() {
        if (storage != null) {
            storage.close();
            storage = null;
        }
        if (savestate != null) {
            unregisterReceiver(savestate);
            savestate = null;
        }
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor edit =  shared.edit();
        edit.putBoolean(PREFERENCE_RUN, false);
        edit.commit();
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        Log.d(TAG, "onTerminate");
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Log.d(TAG, "onLowMemory");
    }


    public static String onTrimString(int level) {
        switch (level) {
            case TRIM_MEMORY_COMPLETE:
                return "TRIM_MEMORY_COMPLETE";
            case TRIM_MEMORY_MODERATE:
                return "TRIM_MEMORY_MODERATE";
            case TRIM_MEMORY_BACKGROUND:
                return "TRIM_MEMORY_BACKGROUND";
            case TRIM_MEMORY_UI_HIDDEN:
                return "TRIM_MEMORY_UI_HIDDEN";
            case TRIM_MEMORY_RUNNING_CRITICAL:
                return "TRIM_MEMORY_RUNNING_CRITICAL";
            case TRIM_MEMORY_RUNNING_LOW:
                return "TRIM_MEMORY_RUNNING_LOW";
            case TRIM_MEMORY_RUNNING_MODERATE:
                return "TRIM_MEMORY_RUNNING_MODERATE";
        }
        return "unknown";
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        Log.d(TAG, "onTrimMemory: " + onTrimString(level));
    }

    public static int getTheme(Context context, int light, int dark) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String theme = shared.getString(PREFERENCE_THEME, "");
        if (theme.equals("Theme_Dark")) {
            return dark;
        } else {
            return light;
        }
    }

    public static int getActionbarColor(Context context) {
        int colorId = MainApplication.getTheme(context, R.attr.colorPrimary, R.attr.secondBackground);
        int color = ThemeUtils.getThemeColor(context, colorId);
        return color;
    }

    public int getUserTheme() {
        return getTheme(this, R.style.AppThemeLight, R.style.AppThemeDark);
    }

    static public String formatTime(int tt) {
        return String.format("%02d", tt);
    }

    public String formatLeft(int diff) {
        String str = "";

        int diffSeconds = (int) (diff / 1000 % 60);
        int diffMinutes = (int) (diff / (60 * 1000) % 60);
        int diffHours = (int) (diff / (60 * 60 * 1000) % 24);
        int diffDays = (int) (diff / (24 * 60 * 60 * 1000));

        if (diffDays > 0) {
            str = getResources().getQuantityString(R.plurals.days, diffDays, diffDays);
        } else if (diffHours > 0) {
            str = getResources().getQuantityString(R.plurals.hours, diffHours, diffHours);
        } else if (diffMinutes > 0) {
            str = getResources().getQuantityString(R.plurals.minutes, diffMinutes, diffMinutes);
        } else if (diffSeconds > 0) {
            str = getResources().getQuantityString(R.plurals.seconds, diffSeconds, diffSeconds);
        }

        return str;
    }

    public static String formatFree(long free, long d, long u) {
        return String.format("%s free · ↓ %s · ↑ %s", MainApplication.formatSize(free), MainApplication.formatSize(d) + "/s", MainApplication.formatSize(u) + "/s");
    }

    public static String formatSize(long s) {
        if (s > 0.1 * 1024 * 1024 * 1024) {
            float f = s / 1024f / 1024f / 1024f;
            return String.format("%.1f GB", f);
        } else if (s > 0.1 * 1024 * 1024) {
            float f = s / 1024f / 1024f;
            return String.format("%.1f MB", f);
        } else {
            float f = s / 1024f;
            return String.format("%.1f kb", f);
        }
    }

    static public String formatDuration(long diff) {
        int diffMilliseconds = (int) (diff % 1000);
        int diffSeconds = (int) (diff / 1000 % 60);
        int diffMinutes = (int) (diff / (60 * 1000) % 60);
        int diffHours = (int) (diff / (60 * 60 * 1000) % 24);
        int diffDays = (int) (diff / (24 * 60 * 60 * 1000));

        String str = "";

        if (diffDays > 0)
            str = diffDays + "d " + formatTime(diffHours) + ":" + formatTime(diffMinutes) + ":" + formatTime(diffSeconds);
        else if (diffHours > 0)
            str = formatTime(diffHours) + ":" + formatTime(diffMinutes) + ":" + formatTime(diffSeconds);
        else
            str = formatTime(diffMinutes) + ":" + formatTime(diffSeconds);

        return str;
    }

    static public void setText(View v, String text) {
        TextView t = (TextView) v;
        if (text.isEmpty()) {
            t.setEnabled(false);
            t.setText(NA);
        } else {
            t.setEnabled(true);
            t.setText(text);
        }
    }

    static public void setDate(View v, long d) {
        String s = formatDate(d);
        setText(v, s);
    }

    public static String formatDate(long d) {
        if (d == 0)
            return "";

        SimpleDateFormat s = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        return s.format(new Date(d / 1000000));
    }

    public Storage getStorage() {
        return storage;
    }

}
