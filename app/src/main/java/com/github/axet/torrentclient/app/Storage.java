package com.github.axet.torrentclient.app;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.util.Base64;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import go.libtorrent.Libtorrent;

public class Storage {
    public static final String TORRENTS = "torrents";
    public static final String[] PERMISSIONS = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};

    Context context;

    ArrayList<Torrent> torrents = new ArrayList<>();

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
                    long c = Libtorrent.TorrentBytesCompleted(t);
                    int a = downloaded.getAverageSpeed();
                    if (c > 0 && a > 0) {
                        int diff = (int) (c * 1000 / a);
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

    public Storage(Context context) {
        this.context = context;

        create();

        load();
    }

    public void load() {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        int count = shared.getInt("TORRENT_COUNT", 0);
        for (int i = 0; i < count; i++) {
            String path = shared.getString("TORRENT_" + i + "_PATH", "");

            if (path.isEmpty())
                path = getStoragePath().getPath();

            String state = shared.getString("TORRENT_" + i + "_STATE", "");

            byte[] b = Base64.decode(state, Base64.DEFAULT);
            long t = Libtorrent.LoadTorrent(path, b);
            add(new Torrent(t, path));
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
            edit.putString("TORRENT_" + i + "_STATE", state);
            edit.putString("TORRENT_" + i + "_PATH", t.path);
        }
        edit.commit();
    }

    void create() {
        if (!Libtorrent.Create()) {
            throw new RuntimeException(Libtorrent.Error());
        }
    }

    public void close() {
        save();
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

        if (l.listFiles() != null) {
            migrateTorrents();
            migrateFiles();
        }
    }

    void migrateTorrents() {
        File l = getLocalStorage();
        File t = getStoragePath();

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

            // if file does not exist, maybe it already been migrated.
            // skip it.
            if (f.exists()) {
                move(f, tt);

                // target name changed update torrent meta or pause it
                if (tt.getName().equals(name)) {
                    // TODO replace with rename when it will be impelemented
                    //Libtorrent.TorrentFileRename(torrent.t, 0, tt.getName());

                    // rename not implement so, just pause it
                    active.remove(torrent);
                }
            }
        }

        save();

        if (!l.equals(t)) {
            // restart libtorrent with new storage path
            Libtorrent.Close();
            create();
        }

        for (int i = 0; i < active.size(); i++) {
            Libtorrent.StartTorrent(active.get(i).t);
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
