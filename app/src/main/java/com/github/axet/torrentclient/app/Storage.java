package com.github.axet.torrentclient.app;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.util.Base64;
import android.util.Log;

import com.github.axet.torrentclient.services.TorrentService;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;

import go.libtorrent.Libtorrent;

public class Storage {
    public static final String TAG = Storage.class.getSimpleName();

    public static final String TORRENTS = "torrents";
    public static final String[] PERMISSIONS = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};

    Context context;

    SpeedInfo downloaded = new SpeedInfo();
    SpeedInfo uploaded = new SpeedInfo();

    ArrayList<Torrent> torrents = new ArrayList<>();

    ArrayList<Torrent> pause = new ArrayList<>();

    Handler handler;

    Runnable refresh;

    BroadcastReceiver wifiReciver;

    public static class Torrent {
        public long t;
        public String path;

        SpeedInfo downloaded = new SpeedInfo();
        SpeedInfo uploaded = new SpeedInfo();

        public Torrent(long t, String path) {
            this.t = t;
            this.path = path;
        }

        public String name() {
            String name = Libtorrent.TorrentName(t);
            // can be empy for magnet links, show hash instead
            if (name.isEmpty()) {
                name = Libtorrent.TorrentHash(t);
            }
            return name;
        }

        public void start() {
            if (!Libtorrent.StartTorrent(t))
                throw new RuntimeException(Libtorrent.Error());
            Libtorrent.StatsInfo b = Libtorrent.TorrentStats(t);
            downloaded.start(b.getDownloaded());
            uploaded.start(b.getUploaded());
        }

        public void update() {
            Libtorrent.StatsInfo b = Libtorrent.TorrentStats(t);
            downloaded.step(b.getDownloaded());
            uploaded.step(b.getUploaded());
        }

        public void stop() {
            Libtorrent.StopTorrent(t);
            Libtorrent.StatsInfo b = Libtorrent.TorrentStats(t);
            downloaded.end(b.getDownloaded());
            uploaded.end(b.getUploaded());
        }

        // "Left: 5m 30s · ↓ 1.5Mb/s · ↑ 0.6Mb/s"
        public String status(Context context) {
            String str = "";

            switch (Libtorrent.TorrentStatus(t)) {
                case Libtorrent.StatusPaused:
                    // str += "Paused";
                    if (Libtorrent.InfoTorrent(t))
                        str += MainApplication.formatSize(Libtorrent.TorrentBytesLength(t)) + " · ";

                    str += "↓ " + MainApplication.formatSize(0) + "/s";
                    str += " · ↑ " + MainApplication.formatSize(0) + "/s";
                    break;
                case Libtorrent.StatusSeeding:
                    // str += "Seeding";
                    if (Libtorrent.InfoTorrent(t))
                        str += MainApplication.formatSize(Libtorrent.TorrentBytesLength(t)) + " · ";

                    str += "↓ " + MainApplication.formatSize(downloaded.getCurrentSpeed()) + "/s";
                    str += " · ↑ " + MainApplication.formatSize(uploaded.getCurrentSpeed()) + "/s";
                    break;
                case Libtorrent.StatusDownloading:
                    long c = 0;
                    if (Libtorrent.InfoTorrent(t))
                        c = Libtorrent.TorrentPendingBytesLength(t) - Libtorrent.TorrentPendingBytesCompleted(t);
                    int a = downloaded.getAverageSpeed();
                    if (c > 0 && a > 0) {
                        long diff = c * 1000 / a;
                        str += "" + ((MainApplication) context.getApplicationContext()).formatDuration(diff) + "";
                    } else {
                        str += "∞";
                    }
                    str += " · ↓ " + MainApplication.formatSize(downloaded.getCurrentSpeed()) + "/s";
                    str += " · ↑ " + MainApplication.formatSize(uploaded.getCurrentSpeed()) + "/s";
                    break;
            }

            return str.trim();
        }

        public static int getProgress(long t) {
            if (Libtorrent.InfoTorrent(t)) {
                long p = Libtorrent.TorrentPendingBytesLength(t);
                if (p == 0)
                    return 0;
                return (int) (Libtorrent.TorrentPendingBytesCompleted(t) * 100 / p);
            }
            return 0;
        }

        public int getProgress() {
            return getProgress(t);
        }

        public boolean isDownloading() {
            if (Libtorrent.TorrentActive(t)) {
                if (Libtorrent.InfoTorrent(t)) {
                    return Libtorrent.TorrentBytesCompleted(t) < Libtorrent.TorrentBytesLength(t);
                }
                return true;
            } else {
                return false;
            }
        }
    }

    public Storage(Context context) {
        Log.d(TAG, "Storage.Close");

        this.context = context;

        handler = new Handler(context.getMainLooper());
    }

    public MainApplication getApp() {
        return (MainApplication) context.getApplicationContext();
    }

    public void update() {
        Libtorrent.BytesInfo b = Libtorrent.Stats();

        downloaded.step(b.getDownloaded());
        uploaded.step(b.getUploaded());
    }

    public void updateHeader() {
        String header = formatHeader();
        header += "\n";
        for (int i = 0; i < count(); i++) {
            Storage.Torrent t = torrent(i);
            if (Libtorrent.TorrentActive(t.t)) {
                header += "(" + t.getProgress() + "%) ";
            }
        }
        TorrentService.updateNotify(context, header);
    }

    public void load() {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        int count = shared.getInt("TORRENT_COUNT", 0);
        for (int i = 0; i < count; i++) {
            String path = shared.getString("TORRENT_" + i + "_PATH", "");

            if (path.isEmpty())
                path = getStoragePath().getPath();

            String state = shared.getString("TORRENT_" + i + "_STATE", "");

            int status = shared.getInt("TORRENT_" + i + "_STATUS", 0);

            byte[] b = Base64.decode(state, Base64.DEFAULT);

            long t = Libtorrent.LoadTorrent(path, b);
            if (t == -1) {
                Log.d(TAG, Libtorrent.Error());
                continue;
            }
            Torrent tt = new Torrent(t, path);
            torrents.add(tt);

            if (status != Libtorrent.StatusPaused) {
                start(tt);
            }
        }
    }

    public void save() {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor edit = shared.edit();
        edit.putInt("TORRENT_COUNT", torrents.size());
        for (int i = 0; i < torrents.size(); i++) {
            Torrent t = torrents.get(i);
            byte[] b = Libtorrent.SaveTorrent(t.t);
            String state = Base64.encodeToString(b, Base64.DEFAULT);
            edit.putInt("TORRENT_" + i + "_STATUS", status(t));
            edit.putString("TORRENT_" + i + "_STATE", state);
            edit.putString("TORRENT_" + i + "_PATH", t.path);
        }
        edit.commit();
    }

    public void create() {
        TorrentService.startService(context, formatHeader());

        if (!Libtorrent.Create()) {
            throw new RuntimeException(Libtorrent.Error());
        }

        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        Libtorrent.SetDefaultAnnouncesList(shared.getString(MainApplication.PREFERENCE_ANNOUNCE, ""));

        IntentFilter wifiFilter = new IntentFilter();
        wifiFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        wifiReciver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                if (action.equals(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)) {
                    SupplicantState state = intent.getParcelableExtra(WifiManager.EXTRA_NEW_STATE);
                    if (SupplicantState.isValidState(state) && state == SupplicantState.COMPLETED) {
                        resume();
                    } else {
                        pause();
                    }
                }
            }
        };
        context.registerReceiver(wifiReciver, wifiFilter);

        downloaded.start(0);
        uploaded.start(0);

        load();

        refresh();
    }

    void refresh() {
        if (refresh != null)
            handler.removeCallbacks(refresh);

        refresh = new Runnable() {
            @Override
            public void run() {
                updateHeader();
                handler.postDelayed(refresh, 1000);
            }
        };
        refresh.run();
    }

    public void close() {
        Log.d(TAG, "Storage.Close");

        save();

        torrents.clear();

        Libtorrent.Close();

        if (refresh != null) {
            handler.removeCallbacks(refresh);
            refresh = null;
        }

        if (wifiReciver != null) {
            context.unregisterReceiver(wifiReciver);
            wifiReciver = null;
        }

        TorrentService.stopService(context);
    }

    public void add(Torrent t) {
        torrents.add(t);

        save();
    }

    public int count() {
        return torrents.size();
    }

    public Torrent torrent(int i) {
        return torrents.get(i);
    }

    public void remove(Torrent t) {
        torrents.remove(t);

        save();
    }

    public boolean permitted(String[] ss) {
        for (String s : ss) {
            if (ContextCompat.checkSelfPermission(context, s) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public File getLocalStorage() {
        return new File(context.getApplicationInfo().dataDir, TORRENTS);
    }

    public boolean isLocalStorageEmpty() {
        return getLocalStorage().listFiles().length == 0;
    }

    public boolean isExternalStoragePermitted() {
        return permitted(PERMISSIONS);
    }

    public File getStoragePath() {
        if (permitted(PERMISSIONS)) {
            return getPrefStorage();
        } else {
            return getLocalStorage();
        }
    }

    File getPrefStorage() {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String path = shared.getString(MainApplication.PREFERENCE_STORAGE, "");
        return new File(path);
    }

    public void migrateLocalStorage() {
        File l = getLocalStorage();
        File t = getStoragePath();

        // if we are local return
        if (l.equals(t))
            return;

        // we are not local

        File[] ff = l.listFiles();
        if (ff != null && ff.length > 0) {
            migrateTorrents();
            migrateFiles();
        }
    }

    void migrateTorrents() {
        File l = getLocalStorage();
        File t = getStoragePath();

        boolean touch = false;
        // migrate torrents, then migrate download data
        for (int i = 0; i < torrents.size(); i++) {
            Torrent torrent = torrents.get(i);

            if (torrent.path.startsWith(l.getPath())) {
                Libtorrent.StopTorrent(torrent.t);
                String name = Libtorrent.TorrentName(torrent.t);
                File f = new File(torrent.path, name);
                File tt = getNextFile(t, f);
                if (f.exists()) {
                    touch = true;
                    move(f, tt);
                    // target name changed update torrent meta or pause it
                    if (!tt.getName().equals(name)) {
                        // TODO replace with rename when it will be impelemented
                        //Libtorrent.TorrentFileRename(torrent.t, 0, tt.getName());
                    }
                    torrent.path = t.getPath();
                }
            }
        }

        if (touch) {
            save();

            for (Torrent torrent : torrents) {
                Libtorrent.RemoveTorrent(torrent.t);
            }

            torrents.clear();

            load();
        }
    }

    void migrateFiles() {
        File l = getLocalStorage();
        File t = getStoragePath();

        File[] ff = l.listFiles();

        if (ff != null) {
            for (File f : ff) {
                File tt = getNextFile(t, f);
                move(f, tt);
            }
        }
    }

    public static String getNameNoExt(File f) {
        String fileName = f.getName();

        int i = fileName.lastIndexOf('.');
        if (i > 0) {
            fileName = fileName.substring(0, i);
        }
        return fileName;
    }

    public static String getExt(File f) {
        String fileName = f.getName();

        int i = fileName.lastIndexOf('.');
        if (i > 0) {
            return fileName.substring(i + 1);
        }
        return "";
    }

    File getNextFile(File parent, File f) {
        String fileName = f.getName();

        String extension = "";

        int i = fileName.lastIndexOf('.');
        if (i > 0) {
            extension = fileName.substring(i + 1);
            fileName = fileName.substring(0, i);
        }

        return getNextFile(parent, fileName, extension);
    }

    File getNextFile(File parent, String name, String ext) {
        String fileName;
        if (ext.isEmpty())
            fileName = name;
        else
            fileName = String.format("%s.%s", name, ext);

        File file = new File(parent, fileName);

        int i = 1;
        while (file.exists()) {
            if (ext.isEmpty())
                fileName = String.format("%s (%d)", name, i);
            else
                fileName = String.format("%s (%d).%s", name, i, ext);
            file = new File(parent, fileName);
            i++;
        }

//        try {
//            file.createNewFile();
//        } catch (IOException e) {
//            throw new RuntimeException("Unable to create: " + file, e);
//        }

        return file;
    }

    public long getFree(File f) {
        while (!f.exists())
            f = f.getParentFile();

        StatFs fsi = new StatFs(f.getPath());
        if (Build.VERSION.SDK_INT < 18)
            return fsi.getBlockSize() * fsi.getAvailableBlocks();
        else
            return fsi.getBlockSizeLong() * fsi.getAvailableBlocksLong();
    }

    public FileOutputStream open(File f) {
        File tmp = f;
        File parent = tmp.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) {
            throw new RuntimeException("unable to create: " + parent);
        }
        if (!parent.isDirectory())
            throw new RuntimeException("target is not a dir: " + parent);
        try {
            return new FileOutputStream(tmp, true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void delete(File f) {
        f.delete();
    }

    public void move(File f, File to) {
        Log.d(TAG, "migrate: " + f + " --> " + to);
        if (f.isDirectory()) {
            to.mkdirs();
            if (!to.exists()) {
                throw new RuntimeException("No permission " + to);
            }
            String[] files = f.list();
            if (files != null) {
                for (String n : files) {
                    File ff = new File(f, n);
                    move(ff, new File(to, n));
                }
            }
            FileUtils.deleteQuietly(f);
            return;
        }
        try {
            InputStream in = new FileInputStream(f);
            OutputStream out = new FileOutputStream(to);

            byte[] buf = new byte[1024 * 1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            in.close();
            out.close();
            FileUtils.deleteQuietly(f);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void pause() {
        for (Torrent t : torrents) {
            if (Libtorrent.TorrentActive(t.t))
                pause.add(t);
            t.stop();
        }
    }

    public void resume() {
        for (Torrent t : pause) {
            t.start();
        }
        pause.clear();
    }

    public boolean isPause() {
        return !pause.isEmpty();
    }

    public String formatHeader() {
        File f = getStoragePath();
        long free = getFree(f);
        return getApp().formatFree(free, downloaded.getCurrentSpeed(), uploaded.getCurrentSpeed());
    }

    public void addTorrentFromFile(String p) {
        String s = getStoragePath().getPath();
        long t = Libtorrent.AddTorrentFromFile(s, p);
        if (t == -1) {
            throw new RuntimeException(Libtorrent.Error());
        }
        add(new Storage.Torrent(t, s));
    }

    public void addMagnet(String ff) {
        ff = ff.trim();
        if (ff.length() == 40) {
            final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
            String[] ss = shared.getString(MainApplication.PREFERENCE_ANNOUNCE, "").split("\n");
            ff = "magnet:?xt=urn:btih:" + ff;
            for (String s : ss) {
                try {
                    ff += "&tr=" + URLEncoder.encode(s, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                }
            }
        }

        String p = getStoragePath().getPath();
        long t = Libtorrent.AddMagnet(p, ff);
        if (t == -1) {
            throw new RuntimeException(Libtorrent.Error());
        }
        add(new Storage.Torrent(t, p));
    }

    public void addTorrent(byte[] buf) {
        String s = getStoragePath().getPath();
        long t = Libtorrent.AddTorrentFromBytes(s, buf);
        if (t == -1) {
            throw new RuntimeException(Libtorrent.Error());
        }
        add(new Storage.Torrent(t, s));
    }

    public void addTorrentFromURL(String p) {
        String s = getStoragePath().getPath();
        long t = Libtorrent.AddTorrentFromURL(s, p);
        if (t == -1) {
            throw new RuntimeException(Libtorrent.Error());
        }
        add(new Storage.Torrent(t, s));
    }

    boolean isConnectedWifi() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        if (activeNetwork != null) { // connected to the internet
            if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
                return true;
            }
        }
        return false;
    }

    public int status(Torrent t) {
        if (pause.contains(t))
            return Libtorrent.StatusQueued;

        return Libtorrent.TorrentStatus(t.t);
    }

    public void start(Torrent t) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        boolean wifi = shared.getBoolean(MainApplication.PREFERENCE_WIFI, true);

        if (!wifi || isConnectedWifi())
            t.start();
        else
            pause.add(t);
    }

    public void stop(Torrent t) {
        pause.remove(t);
        t.stop();
    }
}
