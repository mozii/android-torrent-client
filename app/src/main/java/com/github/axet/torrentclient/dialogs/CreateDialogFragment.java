package com.github.axet.torrentclient.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.github.axet.androidlibrary.widgets.OpenFileDialog;
import com.github.axet.torrentclient.R;
import com.github.axet.torrentclient.activities.MainActivity;
import com.github.axet.torrentclient.app.MainApplication;
import com.github.axet.torrentclient.app.Storage;
import com.github.axet.torrentclient.widgets.Pieces;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import go.libtorrent.Libtorrent;

public class CreateDialogFragment extends AddDialogFragment {

    @Override
    public View createView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = super.createView(inflater, container, savedInstanceState);

        View browse = v.findViewById(R.id.torrent_add_browse);
        browse.setVisibility(View.GONE);

//        ImageButton check = (ImageButton) v.findViewById(R.id.torrent_add_check);
//        check.setVisibility(View.GONE);

        return v;
    }

    void builder(AlertDialog.Builder b) {
        b.setTitle("Create Torrent");
    }

    @Override
    public void updateView(View view) {
        final CheckBox check = (CheckBox) view.findViewById(R.id.torrent_files_check);
        check.setEnabled(false);
    }

    @Override
    public void update() {
        super.update();

        if (v == null)
            return;

        toolbar.setVisibility(View.GONE);
    }
}
