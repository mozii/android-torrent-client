package com.github.axet.torrentclient.app;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.torrentclient.R;

public class MainApplication extends Application {
    public static final String PREFERENCE_STORAGE = "storage_path";
    public static final String PREFERENCE_THEME = "theme";

    @Override
    public void onCreate() {
        super.onCreate();

        PreferenceManager.setDefaultValues(this, R.xml.pref_general, false);

        Context context = this;
        context.setTheme(getUserTheme());
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

}
