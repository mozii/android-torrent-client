package com.github.axet.torrentclient.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.RemoteViews;

import com.github.axet.torrentclient.R;
import com.github.axet.torrentclient.activities.MainActivity;
import com.github.axet.torrentclient.app.MainApplication;
import com.github.axet.torrentclient.app.Storage;

public class TorrentService extends Service {
    public static final String TAG = TorrentService.class.getSimpleName();

    public static final int NOTIFICATION_TORRENT_ICON = 1;

    public static String TITLE = "TITLE";

    public static String UPDATE_NOTIFY = TorrentService.class.getCanonicalName() + ".UPDATE_NOTIFY";
    public static String SHOW_ACTIVITY = TorrentService.class.getCanonicalName() + ".SHOW_ACTIVITY";
    public static String PAUSE_BUTTON = TorrentService.class.getCanonicalName() + ".PAUSE_BUTTON";

    TorrentReceiver receiver;

    public class TorrentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(UPDATE_NOTIFY)) {
                showNotificationAlarm(true, intent.getStringExtra(TITLE));
            }
            if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                // showRecordingActivity();
            }
            if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                // do nothing. do not annoy user. he will see alarm screen on next screen on event.
            }
        }
    }

    public static void startService(Context context, String title) {
        Intent i = new Intent(context, TorrentService.class).setAction(UPDATE_NOTIFY)
                .putExtra(TITLE, title);
        context.startService(i);
    }

    public static void updateNotify(Context context, String title) {
        Intent i = new Intent(UPDATE_NOTIFY).putExtra(TITLE, title);
        context.sendBroadcast(i);
    }

    public static void stopService(Context context) {
        context.stopService(new Intent(context, TorrentService.class));
    }

    public TorrentService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");

        receiver = new TorrentReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(UPDATE_NOTIFY);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(receiver, filter);

        startForeground(NOTIFICATION_TORRENT_ICON, buildNotification(""));
    }

    MainApplication getApp() {
        return ((MainApplication) getApplication());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");

        if (intent != null) {
            String a = intent.getAction();
            if (a != null) {
                if (a.equals(PAUSE_BUTTON)) {
                }
                if (a.equals(UPDATE_NOTIFY)) {
                    showNotificationAlarm(true, intent.getStringExtra(TITLE));
                }
                if (a.equals(SHOW_ACTIVITY)) {
                    MainActivity.startActivity(this);
                }
            }
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public class Binder extends android.os.Binder {
        public TorrentService getService() {
            return TorrentService.this;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestory");

        sendBroadcast(new Intent(MainApplication.SAVE_STATE));

        stopForeground(false);

        showNotificationAlarm(false, null);

        unregisterReceiver(receiver);
    }

    Notification buildNotification(String title) {
        PendingIntent main = PendingIntent.getService(this, 0,
                new Intent(this, TorrentService.class).setAction(SHOW_ACTIVITY),
                PendingIntent.FLAG_UPDATE_CURRENT);

//        PendingIntent pe = PendingIntent.getService(this, 0,
//                new Intent(this, TorrentService.class).setAction(PAUSE_BUTTON),
//                PendingIntent.FLAG_UPDATE_CURRENT);

        RemoteViews view = new RemoteViews(getPackageName(), MainApplication.getTheme(getBaseContext(),
                R.layout.notifictaion_recording_light,
                R.layout.notifictaion_recording_dark));

        //boolean pause = false;//getStorage().isPause();

        view.setOnClickPendingIntent(R.id.status_bar_latest_event_content, main);
        view.setTextViewText(R.id.notification_text, title);
        //view.setOnClickPendingIntent(R.id.notification_pause, pe);
        //view.setImageViewResource(R.id.notification_pause, pause ? R.drawable.play : R.drawable.pause);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setOngoing(true)
                .setContentTitle("Torrent Client")
                .setSmallIcon(R.drawable.ic_application_icon)
                .setContent(view);

        if (Build.VERSION.SDK_INT >= 21)
            builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        return builder.build();
    }

    public void showNotificationAlarm(boolean show, String title) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (!show) {
            notificationManager.cancel(NOTIFICATION_TORRENT_ICON);
        } else {
            notificationManager.notify(NOTIFICATION_TORRENT_ICON, buildNotification(title));
        }
    }
}

