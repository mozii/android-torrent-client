package com.github.axet.torrentclient.fragments;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.github.axet.torrentclient.R;
import com.github.axet.torrentclient.activities.MainActivity;
import com.github.axet.torrentclient.app.MainApplication;
import com.github.axet.torrentclient.widgets.Pieces;

import java.text.SimpleDateFormat;
import java.util.Date;

import go.libtorrent.Libtorrent;

public class DetailsFragment extends Fragment implements MainActivity.TorrentFragmentInterface {
    View v;

    final static String NA = "N/A";

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        v = inflater.inflate(R.layout.torrent_status, container, false);

        update();

        return v;
    }

    String formatDate(long d) {
        if (d == 0)
            return NA;

        SimpleDateFormat s = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        return s.format(new Date(d * 1000));
    }

    public void update() {
        long t = getArguments().getLong("torrent");

        Pieces pview = (Pieces)v.findViewById(R.id.torrent_status_pieces);
        pview.setTorrent(t);

        TextView size = (TextView) v.findViewById(R.id.torrent_size);
        size.setText(Libtorrent.TorrentBytesCompleted(t) == 0 ? NA : MainApplication.formatSize(Libtorrent.TorrentBytesLength(t)));

        TextView pieces = (TextView) v.findViewById(R.id.torrent_pieces);
        pieces.setText(Libtorrent.TorrentBytesCompleted(t) == 0 ? NA : Libtorrent.TorrentPiecesCount(t) + ", Length: " + MainApplication.formatSize(Libtorrent.TorrentPieceLength(t)));

        final String h = Libtorrent.TorrentHash(t);
        final TextView hash = (TextView) v.findViewById(R.id.torrent_hash);
        hash.setText(h);

        View hashCopy = v.findViewById(R.id.torrent_hash_copy);
        hashCopy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("hash", h);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(getContext(), "Hash copied!", Toast.LENGTH_SHORT).show();
            }
        });

        TextView creator = (TextView) v.findViewById(R.id.torrent_creator);
        creator.setText(Libtorrent.TorrentCreator(t));

        TextView createdon = (TextView) v.findViewById(R.id.torrent_created_on);
        createdon.setText(formatDate(Libtorrent.TorrentCreateOn(t)));

        TextView comment = (TextView) v.findViewById(R.id.torrent_comment);
        comment.setText(Libtorrent.TorrentComment(t));

        TextView status = (TextView) v.findViewById(R.id.torrent_status);
        switch (Libtorrent.TorrentStatus(t)) {
            case Libtorrent.StatusDownloading:
                status.setText("Downloading");
                break;
            case Libtorrent.StatusPaused:
                status.setText("Paused");
                break;
            case Libtorrent.StatusSeeding:
                status.setText("Seeding");
                break;
        }

        TextView progress = (TextView) v.findViewById(R.id.torrent_progress);
        long p = Libtorrent.TorrentBytesCompleted(t) == 0 ? 0 : Libtorrent.TorrentBytesCompleted(t) * 100 / Libtorrent.TorrentBytesLength(t);
        progress.setText(String.format("%d%%", p));

        TextView downloaded = (TextView) v.findViewById(R.id.torrent_downloaded);
        Libtorrent.BytesInfo b = Libtorrent.TorrentStats(t);
        downloaded.setText(MainApplication.formatSize(b.getDownloaded()));

        TextView uploaded = (TextView) v.findViewById(R.id.torrent_uploaded);
        uploaded.setText(MainApplication.formatSize(b.getUploaded()));

        TextView ratio = (TextView) v.findViewById(R.id.torrent_ratio);
        float r = b.getDownloaded() > 0 ? b.getUploaded() / (float) b.getDownloaded() : 0;
        ratio.setText(String.format("%.2f", r));

        TextView added = (TextView) v.findViewById(R.id.torrent_added);
        added.setText(formatDate(Libtorrent.TorrentDateAdded(t)));

        TextView completed = (TextView) v.findViewById(R.id.torrent_completed);
        completed.setText(formatDate(Libtorrent.TorrentDateCompleted(t)));
    }
}