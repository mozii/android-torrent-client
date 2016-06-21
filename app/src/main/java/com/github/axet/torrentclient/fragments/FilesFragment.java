package com.github.axet.torrentclient.fragments;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.github.axet.torrentclient.R;
import com.github.axet.torrentclient.activities.MainActivity;

public class FilesFragment extends Fragment implements MainActivity.TorrentFragmentInterface {
    View v;

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        v = inflater.inflate(R.layout.torrent_files, container, false);

        update();

        return v;
    }

    @Override
    public void update() {
        long t = getArguments().getLong("torrent");

        ;
    }
}
