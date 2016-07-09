package com.github.axet.torrentclient.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.github.axet.androidlibrary.widgets.OpenFileDialog;
import com.github.axet.torrentclient.R;
import com.github.axet.torrentclient.activities.MainActivity;
import com.github.axet.torrentclient.app.MainApplication;
import com.github.axet.torrentclient.app.Storage;
import com.github.axet.torrentclient.widgets.Pieces;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import go.libtorrent.Libtorrent;

public class AddDialogFragment extends DialogFragment implements MainActivity.TorrentFragmentInterface {
    View v;
    View header;
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
            MainApplication.setText(percent, (f.file.getBytesCompleted() * 100 / f.file.getLength()) + "%");

            TextView size = (TextView) view.findViewById(R.id.torrent_files_size);
            size.setText("Size: " + MainApplication.formatSize(f.file.getLength()));

            TextView folder = (TextView) view.findViewById(R.id.torrent_files_folder);
            TextView file = (TextView) view.findViewById(R.id.torrent_files_name);

            View fc = view.findViewById(R.id.torrent_files_file);
            fc.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Libtorrent.TorrentFilesCheck(t, f.index, check.isChecked());
                }
            });

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

            updateView(view);

            return view;
        }
    }

    public void updateView(View view) {
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

    MainApplication getApp() {
        return (MainApplication) getActivity().getApplicationContext();
    }

    MainActivity getMainActivity() {
        return (MainActivity) getActivity();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        final Activity activity = getActivity();
        if (activity instanceof DialogInterface.OnDismissListener) {
            ((DialogInterface.OnDismissListener) activity).onDismiss(dialog);
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder b = new AlertDialog.Builder(getActivity(), getTheme())
                .setPositiveButton("OK",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                long t = getArguments().getLong("torrent");
                                String path = getArguments().getString("path");
                                getApp().getStorage().add(new Storage.Torrent(t, path));
                                onDismiss(dialog);
                            }
                        }
                )
                .setNegativeButton("Cancel",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                long t = getArguments().getLong("torrent");
                                Libtorrent.RemoveTorrent(t);
                                dialog.dismiss();
                                onDismiss(dialog);
                            }
                        }
                )
                .setTitle("Add Torrent")
                .setView(createView(LayoutInflater.from(getContext()), null, savedInstanceState));

        builder(b);

        return b.create();
    }

    void builder(AlertDialog.Builder b) {
    }

    @Nullable
    @Override
    public View getView() {
        return null;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return v;
    }

    public View createView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        header = inflater.inflate(R.layout.torrent_add, container, false);

        long t = getArguments().getLong("torrent");

        download = header.findViewById(R.id.torrent_files_metadata);
        download.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                long t = getArguments().getLong("torrent");
                if (!Libtorrent.DownloadMetadata(t)) {
                    getMainActivity().Error(Libtorrent.Error());
                    return;
                }
            }
        });

        list = new ListView(getContext());

        list.addHeaderView(header);

        v = list;

        toolbar = header.findViewById(R.id.torrent_files_toolbar);

        files = new Files();

        list.setAdapter(files);

        View none = header.findViewById(R.id.torrent_files_none);
        none.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                long t = getArguments().getLong("torrent");
                for (TorFile f : ff) {
                    Libtorrent.TorrentFilesCheck(t, f.index, false);
                }
                files.notifyDataSetChanged();
            }
        });

        View all = header.findViewById(R.id.torrent_files_all);
        all.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                long t = getArguments().getLong("torrent");
                for (TorFile f : ff) {
                    Libtorrent.TorrentFilesCheck(t, f.index, true);
                }
                files.notifyDataSetChanged();
            }
        });

        View browse = header.findViewById(R.id.torrent_add_browse);
        browse.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final OpenFileDialog f = new OpenFileDialog(getContext());

                f.setCurrentPath(new File(getArguments().getString("path")));
                f.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        File p = f.getCurrentPath();
                        getArguments().putString("path", p.getPath());

                        long t = getArguments().getLong("torrent");

                        byte[] buf = Libtorrent.GetTorrent(t);
                        Libtorrent.RemoveTorrent(t);

                        t = Libtorrent.AddTorrentFromBytes(p.getPath(), buf);
                        getArguments().putLong("torrent", t);

                        update();
                    }
                });
                f.show();
            }
        });

        final String h = Libtorrent.TorrentHash(t);
        final TextView hash = (TextView) header.findViewById(R.id.torrent_hash);
        hash.setText(h);

        update();

        return v;
    }

    @Override
    public void update() {
        // dialog maybe created but onCreateView not yet called
        if (v == null)
            return;

        long t = getArguments().getLong("torrent");

        View info = header.findViewById(R.id.torrent_add_info_section);
        info.setVisibility(Libtorrent.MetaTorrent(t) ? View.VISIBLE : View.GONE);

        TextView size = (TextView) header.findViewById(R.id.torrent_size);
        MainApplication.setText(size, !Libtorrent.MetaTorrent(t) ? "" : MainApplication.formatSize(Libtorrent.TorrentBytesLength(t)));

        TextView pieces = (TextView) header.findViewById(R.id.torrent_pieces);
        MainApplication.setText(pieces, !Libtorrent.MetaTorrent(t) ? "" : Libtorrent.TorrentPiecesCount(t) + " / " + MainApplication.formatSize(Libtorrent.TorrentPieceLength(t)));

        TextView path = (TextView) header.findViewById(R.id.torrent_add_path);
        path.setText(getArguments().getString("path"));

        ImageButton check = (ImageButton) header.findViewById(R.id.torrent_add_check);
        check.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                long t = getArguments().getLong("torrent");

                if (Libtorrent.TorrentStatus(t) == Libtorrent.StatusChecking) {
                    Libtorrent.StopTorrent(t);
                    Toast.makeText(getContext(), "Stop Checking", Toast.LENGTH_SHORT).show();
                    update();
                    return;
                }

                Libtorrent.CheckTorrent(t);
                Toast.makeText(getContext(), "Start Checking", Toast.LENGTH_SHORT).show();
                update();
            }
        });

        if (Libtorrent.TorrentStatus(t) == Libtorrent.StatusChecking) {
            check.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.ic_stop_black_24dp));
        } else {
            check.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.ic_done_all_black_24dp));
        }

        Pieces pview = (Pieces) header.findViewById(R.id.torrent_status_pieces);
        pview.setTorrent(t);

        download.setVisibility(Libtorrent.MetaTorrent(t) ? View.GONE : View.VISIBLE);
        toolbar.setVisibility(Libtorrent.MetaTorrent(t) ? View.VISIBLE : View.GONE);

        torrentName = Libtorrent.TorrentName(t);

        long l = Libtorrent.TorrentFilesCount(t);

        ff.clear();
        for (long i = 0; i < l; i++) {
            ff.add(new TorFile(i, Libtorrent.TorrentFiles(t, i)));
        }

        Collections.sort(ff, new SortFiles());

        TextView name = (TextView) header.findViewById(R.id.torrent_name);
        if (Libtorrent.MetaTorrent(t)) {
            String n = "./" + Libtorrent.TorrentName(t);
            if (l > 1)
                n += "/";
            name.setText(n);
        } else {
            String n = Libtorrent.TorrentName(t);
            if (n.isEmpty())
                n = MainApplication.NA;
            name.setText(n);
        }

        files.notifyDataSetChanged();
    }
}
