package com.github.axet.torrentclient.fragments;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.github.axet.torrentclient.R;
import com.github.axet.torrentclient.activities.MainActivity;

import go.libtorrent.Libtorrent;

public class TrackersFragment extends Fragment implements MainActivity.TorrentFragmentInterface {
    View v;

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        v = inflater.inflate(R.layout.torrent_trackers, container, false);

        update();

        return v;
    }

    @Override
    public void update() {
        long t = getArguments().getLong("torrent");

        TextView dht = (TextView) v.findViewById(R.id.torrent_trackers_dht);

        TextView pex = (TextView) v.findViewById(R.id.torrent_trackers_pex);

        LinearLayout list = (LinearLayout) v.findViewById(R.id.torrent_trackers_list);
        list.removeAllViews();

        long l = Libtorrent.TorrentTrackersCount(t);

        if (l == 0) {
            TextView tracker = new TextView(getContext());
            tracker.setText("No Trackers");
            list.addView(tracker);
        }

        for (long i = 0; i < l; i++) {
            Libtorrent.Tracker tt = Libtorrent.TorrentTrackers(t, i);
            String url = tt.getAddr();
            if (url.equals("PEX")) {
                pex.setText("Peers " + tt.getPeers());
                continue;
            }
            if (url.equals("DHT")) {
                dht.setText("Peers " + tt.getPeers());
                continue;
            }
            TextView tracker = new TextView(getContext());
            tracker.setText(url);
            list.addView(tracker, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        list.requestLayout();
    }
}
