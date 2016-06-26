package com.github.axet.torrentclient.fragments;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.github.axet.torrentclient.R;
import com.github.axet.torrentclient.activities.MainActivity;
import com.github.axet.torrentclient.app.MainApplication;
import com.github.axet.torrentclient.app.SpeedInfo;

import java.io.File;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;

import go.libtorrent.Libtorrent;

public class PeersFragment extends Fragment implements MainActivity.TorrentFragmentInterface {
    View v;
    ListView list;

    ArrayList<Libtorrent.Peer> ff = new ArrayList<>();
    HashMap<String, SpeedInfo> dinfo = new HashMap<>();
    HashMap<String, SpeedInfo> uinfo = new HashMap<>();

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
                view = inflater.inflate(R.layout.torrent_peers_item, viewGroup, false);
            }

            TextView addr = (TextView) view.findViewById(R.id.torrent_peer_addr);
            TextView stats = (TextView) view.findViewById(R.id.torrent_peer_stats);
            TextView name = (TextView) view.findViewById(R.id.torrent_peer_name);
            TextView d = (TextView) view.findViewById(R.id.torrent_peer_downloaded);
            TextView u = (TextView) view.findViewById(R.id.torrent_peer_uploaded);

            Libtorrent.Peer f = getItem(i);

            String a = f.getAddr();

            SpeedInfo di = dinfo.get(a);
            if (di == null) {
                di = new SpeedInfo();
                di.start(f.getDownloaded());
                dinfo.put(a, di);
            } else {
                di.step(f.getDownloaded());
            }

            SpeedInfo ui = uinfo.get(a);
            if (ui == null) {
                ui = new SpeedInfo();
                ui.start(f.getUploaded());
                uinfo.put(a, ui);
            } else {
                ui.step(f.getUploaded());
            }

            long t = getArguments().getLong("torrent");

            String str = "";

            if (Libtorrent.InfoTorrent(t))
                str += f.getPiecesCompleted() * 100 / Libtorrent.TorrentPiecesCount(t) + "% ";

            if (f.getSupportsEncryption())
                str += "(E)";

            str += "(" + f.getSource().substring(0, 1) + ")";

            addr.setText(f.getAddr());
            stats.setText(str);
            name.setText(f.getName());
            d.setText(MainApplication.formatSize(di.getCurrentSpeed()) + "/s");
            u.setText(MainApplication.formatSize(ui.getCurrentSpeed()) + "/s");

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

        ArrayList<String> addrs = new ArrayList<>();

        ff.clear();
        for (long i = 0; i < l; i++) {
            Libtorrent.Peer p = Libtorrent.TorrentPeers(t, i);
            ff.add(p);
            addrs.add(p.getAddr());
        }

        ArrayList<String> remove = new ArrayList<>();

        for (String k : uinfo.keySet()) {
            if (!addrs.contains(k))
                remove.add(k);
        }

        for (String k : remove) {
            dinfo.remove(k);
            uinfo.remove(k);
        }

        files.notifyDataSetChanged();
    }
}
