package com.github.axet.torrentclient.fragments;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.github.axet.androidlibrary.animations.RemoveItemAnimation;
import com.github.axet.torrentclient.R;
import com.github.axet.torrentclient.activities.MainActivity;
import com.github.axet.torrentclient.app.MainApplication;
import com.github.axet.torrentclient.app.Storage;
import com.github.axet.torrentclient.widgets.Pieces;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import go.libtorrent.Libtorrent;

public class DetailsFragment extends Fragment implements MainActivity.TorrentFragmentInterface {
    View v;

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        v = inflater.inflate(R.layout.torrent_status, container, false);

        update();

        return v;
    }

    public void update() {
        long t = getArguments().getLong("torrent");

        Pieces pview = (Pieces) v.findViewById(R.id.torrent_status_pieces);
        pview.setTorrent(t);

        TextView size = (TextView) v.findViewById(R.id.torrent_size);
        MainApplication.setText(size, !Libtorrent.MetaTorrent(t) ? "" : MainApplication.formatSize(Libtorrent.TorrentBytesLength(t)));

        TextView pieces = (TextView) v.findViewById(R.id.torrent_pieces);
        MainApplication.setText(pieces, !Libtorrent.MetaTorrent(t) ? "" : Libtorrent.TorrentPiecesCount(t) + ", Length: " + MainApplication.formatSize(Libtorrent.TorrentPieceLength(t)));

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

        Libtorrent.InfoTorrent i = Libtorrent.TorrentInfo(t);

        TextView creator = (TextView) v.findViewById(R.id.torrent_creator);
        MainApplication.setText(creator, i.getCreator());

        TextView createdon = (TextView) v.findViewById(R.id.torrent_created_on);
        MainApplication.setDate(createdon, i.getCreateOn());

        final String c = i.getComment().trim();
        final TextView comment = (TextView) v.findViewById(R.id.torrent_comment);
        MainApplication.setText(comment, c);
        comment.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!c.startsWith("http"))
                    return;

                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setTitle("Open URL Browser");

                builder.setMessage(c + "\n\n" + "Are you sure ? ");
                builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(c));
                        startActivity(browserIntent);
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
            case Libtorrent.StatusChecking:
                status.setText("Checking");
                break;
        }

        TextView progress = (TextView) v.findViewById(R.id.torrent_progress);
        progress.setText(String.format("%d%%", Storage.Torrent.getProgress(t)));

        TextView downloaded = (TextView) v.findViewById(R.id.torrent_downloaded);
        Libtorrent.StatsTorrent b = Libtorrent.TorrentStats(t);
        downloaded.setText(MainApplication.formatSize(b.getDownloaded()));

        TextView uploaded = (TextView) v.findViewById(R.id.torrent_uploaded);
        uploaded.setText(MainApplication.formatSize(b.getUploaded()));

        TextView ratio = (TextView) v.findViewById(R.id.torrent_ratio);
        float r;
        if (b.getDownloaded() >= Libtorrent.TorrentBytesLength(t)) {
            r = b.getUploaded() / (float) b.getDownloaded();
        } else {
            r = b.getUploaded() / (float) Libtorrent.TorrentBytesLength(t);
        }
        ratio.setText(String.format("%.2f", r));

        Libtorrent.InfoTorrent info = Libtorrent.TorrentInfo(t);

        TextView added = (TextView) v.findViewById(R.id.torrent_added);
        MainApplication.setDate(added, info.getDateAdded());

        TextView completed = (TextView) v.findViewById(R.id.torrent_completed);
        MainApplication.setDate(completed, info.getDateCompleted());

        TextView downloading = (TextView) v.findViewById(R.id.torrent_downloading);
        downloading.setText(MainApplication.formatDuration(b.getDownloading() / 1000000));

        TextView seeding = (TextView) v.findViewById(R.id.torrent_seeding);
        seeding.setText(MainApplication.formatDuration(b.getSeeding() / 1000000));
    }
}