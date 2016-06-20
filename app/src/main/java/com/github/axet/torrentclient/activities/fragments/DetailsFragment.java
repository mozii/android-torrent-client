package com.github.axet.torrentclient.activities.fragments;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.github.axet.torrentclient.R;
import com.github.axet.torrentclient.app.MainApplication;

import org.w3c.dom.Text;

import go.libtorrent.Libtorrent;

public class DetailsFragment extends Fragment {
    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.torrent_status, container, false);

        update(getArguments().getLong("torrent"), rootView);

        return rootView;
    }

    void update(long t, View v) {
        TextView s = (TextView) v.findViewById(R.id.torrent_status);
        switch (Libtorrent.TorrentStatus(t)) {
            case Libtorrent.StatusDownloading:
                s.setText("Downloading");
                break;
            case Libtorrent.StatusPaused:
                s.setText("Paused");
                break;
            case Libtorrent.StatusSeeding:
                s.setText("Seeding");
                break;
        }

        s = (TextView) v.findViewById(R.id.torrent_progress);
        long p = Libtorrent.TorrentBytesCompleted(t) > 0 ? Libtorrent.TorrentBytesLength(t) * 100 / Libtorrent.TorrentBytesCompleted(t) : 0;
        s.setText(String.format("%d%%", p));

        s = (TextView) v.findViewById(R.id.torrent_downloaded);
        Libtorrent.BytesInfo b = Libtorrent.TorrentStats(t);
        s.setText(MainApplication.formatSize(b.getDownloaded()));

        s = (TextView) v.findViewById(R.id.torrent_uploaded);
        s.setText(MainApplication.formatSize(b.getUploaded()));

        s = (TextView) v.findViewById(R.id.torrent_ratio);
        float r = b.getDownloaded() > 0 ? b.getUploaded() / (float) b.getDownloaded() : 0;
        s.setText(String.format("%.2f", r));
    }
}