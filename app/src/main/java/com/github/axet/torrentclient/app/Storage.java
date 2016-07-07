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
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.github.axet.torrentclient.services.TorrentService;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.HexDump;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import go.libtorrent.Libtorrent;

public class Storage {
    public static final String TAG = Storage.class.getSimpleName();

    public static final String TORRENTS = "torrents";
    public static final String[] PERMISSIONS = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};

    Context context;

    SpeedInfo downloaded = new SpeedInfo();
    SpeedInfo uploaded = new SpeedInfo();

    ArrayList<Torrent> torrents = new ArrayList<>();

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
            Libtorrent.StatsTorrent b = Libtorrent.TorrentStats(t);
            downloaded.start(b.getDownloaded());
            uploaded.start(b.getUploaded());
        }

        public void update() {
            Libtorrent.StatsTorrent b = Libtorrent.TorrentStats(t);
            downloaded.step(b.getDownloaded());
            uploaded.step(b.getUploaded());
        }

        public void stop() {
            Libtorrent.StopTorrent(t);
            Libtorrent.StatsTorrent b = Libtorrent.TorrentStats(t);
            downloaded.end(b.getDownloaded());
            uploaded.end(b.getUploaded());
        }

        // "Left: 5m 30s · ↓ 1.5Mb/s · ↑ 0.6Mb/s"
        public String status(Context context) {
            String str = "";

            switch (Libtorrent.TorrentStatus(t)) {
                case Libtorrent.StatusQueued:
                case Libtorrent.StatusChecking:
                case Libtorrent.StatusPaused:
                case Libtorrent.StatusSeeding:
                    if (Libtorrent.MetaTorrent(t))
                        str += MainApplication.formatSize(Libtorrent.TorrentBytesLength(t)) + " · ";

                    str += "↓ " + MainApplication.formatSize(downloaded.getCurrentSpeed()) + "/s";
                    str += " · ↑ " + MainApplication.formatSize(uploaded.getCurrentSpeed()) + "/s";
                    break;
                case Libtorrent.StatusDownloading:
                    long c = 0;
                    if (Libtorrent.MetaTorrent(t))
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

        public String toString() {
            String str = name();

            if (Libtorrent.MetaTorrent(t))
                str += " · " + MainApplication.formatSize(Libtorrent.TorrentBytesLength(t));

            str += " · (" + getProgress() + "%)";

            return str;
        }

        public static int getProgress(long t) {
            if (Libtorrent.MetaTorrent(t)) {
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
    }

    // seeds should go to start. !seeds to the end (so start download it).
    // seed ordered by seed time desc. !seed ordered by percent
    public static class LoadTorrents implements Comparator<Torrent> {

        @Override
        public int compare(Torrent lhs, Torrent rhs) {
            Boolean lseed = Libtorrent.PendingCompleted(lhs.t);
            Boolean rseed = Libtorrent.PendingCompleted(rhs.t);

            // booth done
            if (lseed && rseed) {
                Long ltime = Libtorrent.TorrentStats(lhs.t).getSeeding();
                Long rtime = Libtorrent.TorrentStats(rhs.t).getSeeding();

                // seed time desc
                return rtime.compareTo(ltime);
            }

            // seed to start, download to the end
            if (lseed || rseed) {
                return rseed.compareTo(lseed);
            }

            if (!lseed && !rseed) {
                Integer lp = lhs.getProgress();
                Integer rp = rhs.getProgress();

                // seed time desc
                return lp.compareTo(rp);
            }

            return 0;
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
        Log.d(TAG, "load()");
        ArrayList<Torrent> resume = new ArrayList<>();

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
                resume.add(tt);
            }
        }

        Collections.sort(resume, new LoadTorrents());

        for (Torrent t : resume) {
            start(t);
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
            edit.putInt("TORRENT_" + i + "_STATUS", Libtorrent.TorrentStatus(t.t));
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

        boolean wifi = shared.getBoolean(MainApplication.PREFERENCE_WIFI, true);

        if (wifi && !isConnectedWifi()) {
            pause();
        }

        IntentFilter wifiFilter = new IntentFilter();
        wifiFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        wifiFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        wifiReciver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                boolean wifi = shared.getBoolean(MainApplication.PREFERENCE_WIFI, true);
                final String action = intent.getAction();
                if (action.equals(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)) {
                    SupplicantState state = intent.getParcelableExtra(WifiManager.EXTRA_NEW_STATE);
                    Log.d(TAG, state.toString());
                    if (wifi) { // suplicant only related to 'wifi only'
                        if (intent.getBooleanExtra(WifiManager.EXTRA_SUPPLICANT_CONNECTED, false)) {
                            resume();
                            return;
                        }
                        if (isConnectedWifi()) { // maybe 'state' have incorrect state. check system service additionaly.
                            resume();
                            return;
                        }
                        pause();
                        return;
                    }
                }
                if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                    NetworkInfo state = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                    Log.d(TAG, state.toString());
                    if (state.isConnected()) {
                        if (wifi) { // wifi only?
                            switch (state.getType()) {
                                case ConnectivityManager.TYPE_WIFI:
                                case ConnectivityManager.TYPE_ETHERNET:
                                    resume();
                                    return;
                            }
                        } else { // resume for any connection type
                            resume();
                            return;
                        }
                    }
                    // if not state.isConnected() maybe it is not correct, check service information
                    if (wifi) {
                        if (isConnectedWifi()) {
                            resume();
                            return;
                        }
                    } else {
                        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                        if (activeNetwork != null) { // connected to the internet
                            resume();
                            return;
                        }
                    }
                    pause();
                    return;
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

        Libtorrent.RemoveTorrent(t.t);
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

        migrateTorrents();
        migrateFiles();
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
                touch = true;
                if (f.exists()) {
                    move(f, tt);
                    // target name changed update torrent meta or pause it
                    if (!tt.getName().equals(name)) {
                        // TODO replace with rename when it will be impelemented
                        //Libtorrent.TorrentFileRename(torrent.t, 0, tt.getName());
                    }
                }
                torrent.path = t.getPath();
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

        File parent = to.getParentFile();
        parent.mkdirs();
        if (!parent.exists()) {
            throw new RuntimeException("No permission: " + parent);
        }

        try {
            InputStream in = new BufferedInputStream(new FileInputStream(f));
            OutputStream out = new BufferedOutputStream(new FileOutputStream(to));

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
        Log.d(TAG, "pause()");
        Libtorrent.Pause();
    }

    public void resume() {
        Log.d(TAG, "resume()");
        Libtorrent.Resume();
    }

    public String formatHeader() {
        File f = getStoragePath();
        long free = getFree(f);
        return getApp().formatFree(free, downloaded.getCurrentSpeed(), uploaded.getCurrentSpeed());
    }

    public void addMagnetSplit(String ff) {
        ff = ff.trim();

        String scheme = "magnet:";
        String[] ss = ff.split(scheme);
        if (ss.length > 1) {
            for (String s : ss) {
                s = s.trim();
                if (s.isEmpty())
                    continue;
                addMagnet(scheme + s);
            }
            return;
        }

        ss = ff.split("\\W+");

        for (String s : ss) {
            s = s.trim();
            if (s.isEmpty())
                continue;
            int len = 40;
            if (s.length() % len == 0) {
                int index = 0;
                // check all are 40 bytes hex strings
                while (index < s.length()) {
                    String mag = s.substring(index, index + len);
                    index += mag.length();
                    try {
                        new BigInteger(mag, 16);

                        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
                        String[] tt = shared.getString(MainApplication.PREFERENCE_ANNOUNCE, "").split("\n");
                        ff = "magnet:?xt=urn:btih:" + mag;
                        for (String t : tt) {
                            try {
                                ff += "&tr=" + URLEncoder.encode(t, "UTF-8");
                            } catch (UnsupportedEncodingException e) {
                            }
                        }
                        addMagnet(ff);
                    } catch (NumberFormatException e) {
                    }
                }
            }
        }
    }

    public void addMagnet(String s) {
        String p = getStoragePath().getPath();
        long t = Libtorrent.AddMagnet(p, s);
        if (t == -1) {
            throw new RuntimeException(Libtorrent.Error());
        }
        add(new Storage.Torrent(t, p));
    }

    public void addTorrentFromBytes(byte[] buf) {
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

    public boolean isConnectedWifi() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        if (activeNetwork != null) { // connected to the internet
            if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
                return true;
            }
        }
        return false;
    }

    public void start(Torrent t) {
        t.start();
    }

    public void stop(Torrent t) {
        t.stop();
    }
}
