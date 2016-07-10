package com.github.axet.torrentclient.fragments;

import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.github.axet.androidlibrary.animations.RemoveItemAnimation;
import com.github.axet.androidlibrary.widgets.ThemeUtils;
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

    Pieces pview;
    TextView size;
    TextView hash;
    TextView pieces;
    TextView creator;
    TextView createdon;
    TextView comment;
    TextView status;
    TextView progress;
    TextView added;
    TextView completed;
    TextView downloading;
    TextView seeding;
    View pathButton;
    ImageButton pathImage;
    ImageView check;

    KeyguardManager myKM;

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        v = inflater.inflate(R.layout.torrent_status, container, false);

        pview = (Pieces) v.findViewById(R.id.torrent_status_pieces);
        size = (TextView) v.findViewById(R.id.torrent_size);
        hash = (TextView) v.findViewById(R.id.torrent_hash);
        pieces = (TextView) v.findViewById(R.id.torrent_pieces);
        creator = (TextView) v.findViewById(R.id.torrent_creator);
        createdon = (TextView) v.findViewById(R.id.torrent_created_on);
        comment = (TextView) v.findViewById(R.id.torrent_comment);
        status = (TextView) v.findViewById(R.id.torrent_status);
        progress = (TextView) v.findViewById(R.id.torrent_progress);
        added = (TextView) v.findViewById(R.id.torrent_added);
        completed = (TextView) v.findViewById(R.id.torrent_completed);
        downloading = (TextView) v.findViewById(R.id.torrent_downloading);
        seeding = (TextView) v.findViewById(R.id.torrent_seeding);
        check = (ImageView) v.findViewById(R.id.torrent_status_check);

        final long t = getArguments().getLong("torrent");

        final String h = Libtorrent.TorrentHash(t);
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

        final String p = ((MainApplication) getContext().getApplicationContext()).getStorage().path(t);

        TextView path = (TextView) v.findViewById(R.id.torrent_path);
        path.setText(p);

        pathButton = v.findViewById(R.id.torrent_path_open);
        pathButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((MainActivity) getActivity()).openFolder(new File(p));
            }
        });

        pathImage = (ImageButton) v.findViewById(R.id.torrent_path_image);

        myKM = (KeyguardManager) getContext().getSystemService(Context.KEYGUARD_SERVICE);

        update();

        return v;
    }

    public void update() {
        final long t = getArguments().getLong("torrent");

        if (myKM.inKeyguardRestrictedInputMode()) {
            pathButton.setEnabled(false);
            pathImage.setColorFilter(Color.GRAY);
        } else {
            pathButton.setEnabled(true);
            pathImage.setColorFilter(ThemeUtils.getThemeColor(getContext(), R.attr.colorAccent));
        }

        final Runnable checkUpdate = new Runnable() {
            @Override
            public void run() {
                if (Libtorrent.TorrentStatus(t) == Libtorrent.StatusChecking) {
                    check.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.ic_stop_black_24dp));
                } else {
                    check.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.ic_done_all_black_24dp));
                }
            }
        };
        checkUpdate.run();
        check.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Libtorrent.TorrentStatus(t) == Libtorrent.StatusChecking) {
                    Libtorrent.StopTorrent(t);
                    Toast.makeText(getContext(), "Stop Checking", Toast.LENGTH_SHORT).show();
                    checkUpdate.run();
                    return;
                }

                Libtorrent.CheckTorrent(t);
                Toast.makeText(getContext(), "Start Checking", Toast.LENGTH_SHORT).show();
                checkUpdate.run();
            }
        });

        pview.setTorrent(t);

        MainApplication.setText(size, !Libtorrent.MetaTorrent(t) ? "" : MainApplication.formatSize(Libtorrent.TorrentBytesLength(t)));

        MainApplication.setText(pieces, !Libtorrent.MetaTorrent(t) ? "" : Libtorrent.TorrentPiecesCount(t) + " / " + MainApplication.formatSize(Libtorrent.TorrentPieceLength(t)));

        Libtorrent.InfoTorrent i = Libtorrent.TorrentInfo(t);

        MainApplication.setText(creator, i.getCreator());

        MainApplication.setDate(createdon, i.getCreateOn());

        final String c = i.getComment().trim();
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

        progress.setText(String.format("%d%%", Storage.Torrent.getProgress(t)));

        TextView downloaded = (TextView) v.findViewById(R.id.torrent_downloaded);
        Libtorrent.StatsTorrent b = Libtorrent.TorrentStats(t);
        downloaded.setText(MainApplication.formatSize(b.getDownloaded()));

        TextView uploaded = (TextView) v.findViewById(R.id.torrent_uploaded);
        uploaded.setText(MainApplication.formatSize(b.getUploaded()));

        TextView ratio = (TextView) v.findViewById(R.id.torrent_ratio);
        float r = 0;
        if (Libtorrent.MetaTorrent(t)) {
            if (b.getDownloaded() >= Libtorrent.TorrentBytesLength(t)) {
                r = b.getUploaded() / (float) b.getDownloaded();
            } else {
                r = b.getUploaded() / (float) Libtorrent.TorrentBytesLength(t);
            }
        }
        ratio.setText(String.format("%.2f", r));

        Libtorrent.InfoTorrent info = Libtorrent.TorrentInfo(t);

        MainApplication.setDate(added, info.getDateAdded());

        MainApplication.setDate(completed, info.getDateCompleted());

        downloading.setText(MainApplication.formatDuration(b.getDownloading() / 1000000));

        seeding.setText(MainApplication.formatDuration(b.getSeeding() / 1000000));
    }
}