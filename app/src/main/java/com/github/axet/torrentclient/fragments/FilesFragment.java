package com.github.axet.torrentclient.fragments;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;

import com.github.axet.torrentclient.R;
import com.github.axet.torrentclient.activities.MainActivity;
import com.github.axet.torrentclient.app.MainApplication;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import go.libtorrent.Libtorrent;

public class FilesFragment extends Fragment implements MainActivity.TorrentFragmentInterface {
    View v;
    ListView list;
    View toolbar;
    View download;

    Files files;

    String torrentName;

    static class TorFile {
        public long index;
        public Libtorrent.File file;

        public TorFile(long i, Libtorrent.File f) {
            this.file = f;
            this.index = i;
        }
    }

    static class SortFiles implements Comparator<TorFile> {
        @Override
        public int compare(TorFile file, TorFile file2) {
            List<String> s1 = splitPath(file.file.getPath());
            List<String> s2 = splitPath(file2.file.getPath());

            int c = new Integer(s1.size()).compareTo(s2.size());
            if (c != 0)
                return c;

            for (int i = 0; i < s1.size(); i++) {
                String p1 = s1.get(i);
                String p2 = s2.get(i);
                c = p1.compareTo(p2);
                if (c != 0)
                    return c;
            }

            return 0;
        }
    }

    ArrayList<TorFile> ff = new ArrayList<>();

    class Files extends BaseAdapter {

        @Override
        public int getCount() {
            return ff.size();
        }

        @Override
        public TorFile getItem(int i) {
            return ff.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        boolean single(File path) {
            return path.getName().equals(path);
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            LayoutInflater inflater = LayoutInflater.from(getContext());

            if (view == null) {
                view = inflater.inflate(R.layout.torrent_files_item, viewGroup, false);
            }

            final long t = getArguments().getLong("torrent");

            final TorFile f = getItem(i);

            final CheckBox check = (CheckBox) view.findViewById(R.id.torrent_files_check);
            check.setChecked(f.file.getCheck());
            check.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Libtorrent.TorrentFilesCheck(t, f.index, check.isChecked());
                }
            });

            TextView percent = (TextView) view.findViewById(R.id.torrent_files_percent);
            percent.setEnabled(false);
            percent.setText("N/A");

            TextView size = (TextView) view.findViewById(R.id.torrent_files_size);
            size.setText("Size: " + MainApplication.formatSize(f.file.getLength()));

            TextView folder = (TextView) view.findViewById(R.id.torrent_files_folder);
            TextView file = (TextView) view.findViewById(R.id.torrent_files_name);

            String s = f.file.getPath();

            List<String> ss = splitPathFilter(s);

            if (ss.size() == 0) {
                folder.setVisibility(View.GONE);
                file.setText("./" + s);
            } else {
                if (i == 0) {
                    folder.setVisibility(View.GONE);
                } else {
                    File p1 = new File(makePath(ss)).getParentFile();
                    File p2 = new File(makePath(splitPathFilter(getItem(i - 1).file.getPath()))).getParentFile();
                    if (p1 == null || p1.equals(p2)) {
                        folder.setVisibility(View.GONE);
                    } else {
                        folder.setText("./" + p1.getPath());
                        folder.setVisibility(View.VISIBLE);
                    }
                }
                file.setText("./" + ss.get(ss.size() - 1));
            }

            return view;
        }
    }

    public static String makePath(List<String> ss) {
        if (ss.size() == 0)
            return "/";
        return TextUtils.join(File.separator, ss);
    }

    public List<String> splitPathFilter(String s) {
        List<String> ss = splitPath(s);
        if (ss.get(0).equals(torrentName))
            ss.remove(0);
        return ss;
    }

    public static List<String> splitPath(String s) {
        return new ArrayList<String>(Arrays.asList(s.split(Pattern.quote(File.separator))));
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        v = inflater.inflate(R.layout.torrent_files, container, false);

        final long t = getArguments().getLong("torrent");

        download = v.findViewById(R.id.torrent_files_metadata);
        download.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!Libtorrent.DownloadMetadata(t)) {
                    ((MainActivity) getActivity().getApplicationContext()).Error(Libtorrent.Error());
                    return;
                }
            }
        });

        list = (ListView) v.findViewById(R.id.list);

        toolbar = v.findViewById(R.id.torrent_files_toolbar);

        files = new Files();

        list.setAdapter(files);

        View none = v.findViewById(R.id.torrent_files_none);
        none.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                for (TorFile f : ff) {
                    Libtorrent.TorrentFilesCheck(t, f.index, false);
                }
                files.notifyDataSetChanged();
            }
        });

        View all = v.findViewById(R.id.torrent_files_all);
        all.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                for (TorFile f : ff) {
                    Libtorrent.TorrentFilesCheck(t, f.index, true);
                }
                files.notifyDataSetChanged();
            }
        });
        update();

        return v;
    }

    @Override
    public void update() {
        long t = getArguments().getLong("torrent");

        download.setVisibility(Libtorrent.InfoTorrent(t) ? View.GONE : View.VISIBLE);
        toolbar.setVisibility(Libtorrent.InfoTorrent(t) ? View.VISIBLE : View.GONE);

        torrentName = Libtorrent.TorrentName(t);

        long l = Libtorrent.TorrentFilesCount(t);

        ff.clear();
        for (long i = 0; i < l; i++) {
            ff.add(new TorFile(i, Libtorrent.TorrentFiles(t, i)));
        }

        Collections.sort(ff, new SortFiles());

        files.notifyDataSetChanged();

    }
}
