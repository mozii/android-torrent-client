package com.github.axet.torrentclient.fragments;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.github.axet.androidlibrary.animations.RemoveItemAnimation;
import com.github.axet.androidlibrary.widgets.OpenFileDialog;
import com.github.axet.torrentclient.R;
import com.github.axet.torrentclient.activities.MainActivity;
import com.github.axet.torrentclient.app.MainApplication;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import go.libtorrent.Libtorrent;

public class TrackersFragment extends Fragment implements MainActivity.TorrentFragmentInterface {
    View v;

    ArrayList<Libtorrent.Tracker> ff = new ArrayList<>();
    Files files;
    ListView list;

    class Files extends BaseAdapter {

        @Override
        public int getCount() {
            return ff.size();
        }

        @Override
        public Libtorrent.Tracker getItem(int i) {
            return ff.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(final int i, View view, ViewGroup viewGroup) {
            LayoutInflater inflater = LayoutInflater.from(getContext());

            if (view == null) {
                view = inflater.inflate(R.layout.torrent_trackers_item, viewGroup, false);
            }

            final long t = getArguments().getLong("torrent");

            View trash = view.findViewById(R.id.torrent_trackers_trash);
            trash.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                    builder.setTitle("Delete Tracker");
                    builder.setMessage(ff.get(i).getAddr() + "\n\n" + "Are you sure ? ");
                    builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                            Libtorrent.TorrentTrackerRemove(t, ff.get(i).getAddr());
                            update();
                        }
                    });
                    builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    });
                    builder.show();
                }
            });

            TextView url = (TextView) view.findViewById(R.id.torrent_trackers_url);
            TextView lastAnnounce = (TextView) view.findViewById(R.id.torrent_trackers_lastannounce);
            TextView nextAnnounce = (TextView) view.findViewById(R.id.torrent_trackers_nextannounce);
            TextView lastScrape = (TextView) view.findViewById(R.id.torrent_trackers_lastscrape);

            Libtorrent.Tracker f = getItem(i);

            url.setText(f.getAddr());

            String scrape = "Last Scrape: " + formatDate(f.getLastScrape());

            if (f.getLastScrape() != 0)
                scrape += " (S:" + f.getSeeders() + " L:" + f.getLeechers() + " D:" + f.getDownloaded() + ")";

            String ann = "Last Announce: " + formatDate(f.getLastAnnounce());

            if (f.getError() != null && !f.getError().isEmpty()) {
                ann += " (" + f.getError() + ")";
            } else {
                if (f.getLastAnnounce() != 0)
                    ann += " (P:" + f.getPeers() + ")";
            }
            lastAnnounce.setText(ann);
            nextAnnounce.setText("Next Announce: " + formatDate(f.getNextAnnounce()));
            lastScrape.setText(scrape);

            return view;
        }
    }

    String formatDate(long d) {
        if (d == 0)
            return "N/A";

        SimpleDateFormat s = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        return s.format(new Date(d * 1000));
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        v = inflater.inflate(R.layout.torrent_trackers, container, false);

        list = (ListView) v.findViewById(R.id.torrent_trackers_list);

        files = new Files();

        list.setAdapter(files);

        list.setEmptyView(v.findViewById(R.id.empty_list));

        update();

        return v;
    }

    @Override
    public void update() {
        final long t = getArguments().getLong("torrent");

        View add = v.findViewById(R.id.torrent_trackers_add);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final OpenFileDialog.EditTextDialog e = new OpenFileDialog.EditTextDialog(getContext());
                e.setTitle("Add Tracker");
                e.setText("");
                e.setPositiveButton(new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Libtorrent.TorrentTrackerAdd(t, e.getText());
                        update();
                    }
                });
                e.show();
            }
        });

        TextView dhtLast = (TextView) v.findViewById(R.id.torrent_trackers_dht_last);

        TextView pex = (TextView) v.findViewById(R.id.torrent_trackers_pex);

        ff.clear();
        long l = Libtorrent.TorrentTrackersCount(t);
        for (long i = 0; i < l; i++) {
            Libtorrent.Tracker tt = Libtorrent.TorrentTrackers(t, i);
            String url = tt.getAddr();
            if (url.equals("PEX")) {
                pex.setText("Peers: " + tt.getPeers());
                continue;
            }
            if (url.equals("DHT")) {
                String str = "Last Announce: " + formatDate(tt.getLastAnnounce());
                if (tt.getError() != null && !tt.getError().isEmpty())
                    str += " (" + tt.getError() + ")";
                else {
                    if (tt.getLastAnnounce() != 0)
                        str += " (P: " + tt.getPeers() + ")";
                }
                dhtLast.setText(str);
                continue;
            }
            ff.add(tt);
        }
        files.notifyDataSetChanged();
    }
}
