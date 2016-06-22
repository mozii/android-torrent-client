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

public class PeersFragment extends Fragment implements MainActivity.TorrentFragmentInterface {
    View v;
    ListView list;

    ArrayList<Libtorrent.Peer> ff = new ArrayList<>();
    Files files;

    class Files extends BaseAdapter {

        @Override
        public int getCount() {
            return ff.size();
        }

        @Override
        public Libtorrent.Peer getItem(int i) {
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

            TextView addr = (TextView) view.findViewById(R.id.torrent_peer_addr);
            TextView name = (TextView) view.findViewById(R.id.torrent_peer_name);
            TextView d = (TextView) view.findViewById(R.id.torrent_peer_downloaded);
            TextView u = (TextView) view.findViewById(R.id.torrent_peer_uploaded);

            Libtorrent.Peer f = getItem(i);

            addr.setText(f.getAddr());
            name.setText(f.getName());
            d.setText(MainApplication.formatSize(f.getDownloaded()));
            u.setText(MainApplication.formatSize(f.getUploaded()));


            return view;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        v = inflater.inflate(R.layout.torrent_peers, container, false);

        list = (ListView) v.findViewById(R.id.list);

        files = new Files();

        list.setAdapter(files);

        list.setEmptyView(v.findViewById(R.id.empty_list));

        update();

        return v;
    }

    @Override
    public void update() {
        long t = getArguments().getLong("torrent");

        long l = Libtorrent.TorrentPeersCount(t);

        ff.clear();
        for (long i = 0; i < l; i++) {
            ff.add(Libtorrent.TorrentPeers(t, i));
        }

        files.notifyDataSetChanged();
    }
}
