package com.github.axet.torrentclient.fragments;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.github.axet.torrentclient.R;
import com.github.axet.torrentclient.activities.MainActivity;

public class PeersFragment extends Fragment implements MainActivity.TorrentFragmentInterface {
    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.torrent_peers, container, false);
        return rootView;
    }

    @Override
    public void update() {

    }
}
