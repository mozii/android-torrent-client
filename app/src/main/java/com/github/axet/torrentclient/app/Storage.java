package com.github.axet.torrentclient.app;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import go.libtorrent.Libtorrent;

public class Storage {
    public static final String TORRENTS = "torrents";

    Context context;

    public Storage(Context context) {
        this.context = context;
    }

    public static final String[] PERMISSIONS = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};

    ArrayList<Torrent> torrents = new ArrayList<>();

    public static class Torrent {
        public long t;

        SpeedInfo downloaded = new SpeedInfo();
        SpeedInfo uploaded = new SpeedInfo();

        public Torrent(long t) {
            this.t = t;
        }

        public void start() {
            Libtorrent.StartTorrent(t);
            Libtorrent.BytesInfo b = Libtorrent.TorrentStats(t);
            downloaded.start(b.getDownloaded());
            uploaded.start(b.getUploaded());
        }

        public void update() {
            Libtorrent.BytesInfo b = Libtorrent.TorrentStats(t);
            downloaded.step(b.getDownloaded());
            uploaded.step(b.getUploaded());
        }

        public void stop() {
            Libtorrent.StopTorrent(t);
            Libtorrent.BytesInfo b = Libtorrent.TorrentStats(t);
            downloaded.end(b.getDownloaded());
            uploaded.end(b.getUploaded());
        }

        // "Left: 5m 30s · ↓ 1.5Mb/s · ↑ 0.6Mb/s"
        public String status(Context context) {
            String str = "";

            switch (Libtorrent.TorrentStatus(t)) {
                case Libtorrent.StatusPaused:
                    str += "Paused";
                    break;
                case Libtorrent.StatusSeeding:
                    str += "Seeding ";
                    break;
                case Libtorrent.StatusDownloading:
                    if (Libtorrent.TorrentBytesCompleted(t) > 0) {
                        int diff = (int) (Libtorrent.TorrentBytesLength(t) * 1000 / downloaded.getAverageSpeed());
                        str += "Left: " + ((MainApplication) context.getApplicationContext()).formatDuration(diff) + " ";
                    } else {
                        str += "Left: ∞";
                    }
                    break;
            }

            str += "· ↓ " + MainApplication.formatSize(downloaded.getCurrentSpeed()) + "/s";
            str += "· ↑ " + MainApplication.formatSize(uploaded.getCurrentSpeed()) + "/s";
            return str;
        }
    }

    public void add(long t) {
        torrents.add(new Torrent(t));
    }

    public int count() {
        return torrents.size();
    }

    public Torrent torrent(int i) {
        return torrents.get(i);
    }

    public void remove(Torrent t) {
        torrents.remove(t);
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
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String path = shared.getString(MainApplication.PREFERENCE_STORAGE, "");
        if (permitted(PERMISSIONS)) {
            return new File(path);
        } else {
            return getLocalStorage();
        }
    }

    public void migrateLocalStorage() {
        if (!permitted(PERMISSIONS)) {
            return;
        }

        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String path = shared.getString(MainApplication.PREFERENCE_STORAGE, "");

        File l = getLocalStorage();
        File t = new File(path);

        ArrayList<Torrent> active = new ArrayList<>();
        // migrate torrents, then migrate download data
        for (int i = 0; i < torrents.size(); i++) {
            Torrent torrent = torrents.get(i);

            // add to active list, so we will restart torrent after migrate
            if (Libtorrent.TorrentActive(torrent.t))
                active.add(torrent);
            Libtorrent.StopTorrent(torrent.t);

            String name = Libtorrent.TorrentName(torrent.t);
            File f = new File(l, name);
            File tt = getNextFile(t, f);
            move(f, tt);

            // target name changed update torrent meta or pause it
            if (tt.getName().equals(name)) {
                // TODO replace with rename when it will be impelemented
                //Libtorrent.TorrentFileRename(torrent.t, 0, tt.getName());

                // rename not implement so, just pause it
                active.remove(torrent);
            }
        }

        // restart libtorrent with not storage path
        Libtorrent.Close();
        if (!Libtorrent.Create(getStoragePath().getPath())) {
            throw new RuntimeException(Libtorrent.Error());
        }
        for (int i = 0; i < active.size(); i++) {
            Libtorrent.StartTorrent(active.get(i).t);
        }

        // now migrate rest files in local storage
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
        if (f.isDirectory()) {
            to.mkdirs();
            String[] files = f.list();
            if (files != null) {
                for (String n : files) {
                    File ff = new File(f, n);
                    move(ff, new File(to, n));
                }
            }
            f.delete();
            return;
        }
        try {
            InputStream in = new FileInputStream(f);
            OutputStream out = new FileOutputStream(to);

            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            in.close();
            out.close();
            f.delete();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
