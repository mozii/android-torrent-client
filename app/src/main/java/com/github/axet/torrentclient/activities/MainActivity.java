package com.github.axet.torrentclient.activities;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.design.widget.TabLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.getbase.floatingactionbutton.FloatingActionButton;
import com.getbase.floatingactionbutton.FloatingActionsMenu;
import com.github.axet.androidlibrary.animations.RemoveItemAnimation;
import com.github.axet.androidlibrary.widgets.OpenFileDialog;
import com.github.axet.androidlibrary.widgets.PopupShareActionProvider;
import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.torrentclient.R;
import com.github.axet.torrentclient.app.SpeedInfo;
import com.github.axet.torrentclient.fragments.DetailsFragment;
import com.github.axet.torrentclient.fragments.FilesFragment;
import com.github.axet.torrentclient.fragments.PeersFragment;
import com.github.axet.torrentclient.fragments.TrackersFragment;
import com.github.axet.torrentclient.animations.RecordingAnimation;
import com.github.axet.torrentclient.app.MainApplication;
import com.github.axet.torrentclient.app.Storage;
import com.github.axet.torrentclient.services.TorrentService;
import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.api.GoogleApiClient;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import go.libtorrent.Libtorrent;

public class MainActivity extends AppCompatActivity implements AbsListView.OnScrollListener, DialogInterface.OnDismissListener {
    public final static String TAG = MainActivity.class.getSimpleName();

    static final int TYPE_COLLAPSED = 0;
    static final int TYPE_EXPANDED = 1;
    static final int TYPE_DELETED = 2;

    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    private GoogleApiClient client;

    int scrollState;

    Runnable refresh;
    TorrentDialogFragment dialog;

    Storage storage;

    Torrents torrents;
    ListView list;
    Handler handler;
    PopupShareActionProvider shareProvider;

    int themeId;

    // not delared locally - used from two places
    FloatingActionButton create;
    FloatingActionButton add;

    public static class Tag {
        public int tag;
        public int position;

        public Tag(int t, int p) {
            this.tag = t;
            this.position = p;
        }

        public static boolean animate(View v, int s, int p) {
            if (v.getTag() == null)
                return true;
            if (animate(v, s))
                return true;
            return ((Tag) v.getTag()).position != p;
        }

        public static boolean animate(View v, int s) {
            if (v.getTag() == null)
                return false;
            return ((Tag) v.getTag()).tag == s;
        }

        public static void setTag(View v, int t, int p) {
            v.setTag(new Tag(t, p));
        }
    }

    public static void startActivity(Context context) {
        Intent i = new Intent(context, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(i);
    }

    public class Torrents extends BaseAdapter {
        int selected = -1;
        Context context;

        public Torrents(Context context) {
            super();

            this.context = context;
        }

        public Context getContext() {
            return context;
        }

        public void update() {
            for (int i = 0; i < getCount(); i++) {
                Storage.Torrent t = getItem(i);
                if (Libtorrent.TorrentActive(t.t)) {
                    t.update();
                }
            }
        }

        public void close() {
        }

        @Override
        public int getCount() {
            return getStorage().count();
        }

        @Override
        public Storage.Torrent getItem(int i) {
            return getStorage().torrent(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(final int position, View convertView, final ViewGroup parent) {
            LayoutInflater inflater = LayoutInflater.from(getContext());

            if (convertView == null) {
                convertView = inflater.inflate(R.layout.torrent, parent, false);
                convertView.setTag(null);
            }

            if (Tag.animate(convertView, TYPE_DELETED)) {
                RemoveItemAnimation.restore(convertView);
                convertView.setTag(null);
            }

            final View view = convertView;
            final View base = convertView.findViewById(R.id.recording_base);

            final Storage.Torrent t = (Storage.Torrent) getItem(position);

            TextView title = (TextView) convertView.findViewById(R.id.torrent_title);
            title.setText(t.name());

            TextView time = (TextView) convertView.findViewById(R.id.torrent_status);
            time.setText(t.status(getContext()));

            final View playerBase = convertView.findViewById(R.id.recording_player);
            // cover area, prevent click over to convertView
            playerBase.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.d(TAG, "ignored");
                }
            });

            // we need runnable because we have View references
            final Runnable delete = new Runnable() {
                @Override
                public void run() {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                    builder.setTitle("Delete Torrent");
                    builder.setMessage(".../" + Libtorrent.TorrentName(t.t) + "\n\n" + "Are you sure ? ");
                    builder.setNeutralButton("Delete With Data", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                            RemoveItemAnimation.apply(list, base, new Runnable() {
                                @Override
                                public void run() {
                                    t.stop();
                                    File f = new File(getStorage().getStoragePath(), Libtorrent.TorrentName(t.t));
                                    try {
                                        FileUtils.deleteDirectory(f);
                                    } catch (IOException e) {
                                    }
                                    getStorage().remove(t);
                                    Libtorrent.RemoveTorrent(t.t);
                                    Tag.setTag(view, TYPE_DELETED, -1);
                                    select(-1);
                                }
                            });
                        }
                    });
                    builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                            RemoveItemAnimation.apply(list, base, new Runnable() {
                                @Override
                                public void run() {
                                    t.stop();
                                    getStorage().remove(t);
                                    Libtorrent.RemoveTorrent(t.t);
                                    Tag.setTag(view, TYPE_DELETED, -1);
                                    select(-1);
                                }
                            });
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
            };

            View play = convertView.findViewById(R.id.torrent_play);
            play.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Log.d(TAG, "Click Play");
                    if (Libtorrent.TorrentStatus(t.t) == Libtorrent.StatusPaused)
                        t.start();
                    else
                        t.stop();
                    torrents.notifyDataSetChanged();
                }
            });

            {
                // should be done using states, so animation will apply
                ProgressBar bar = (ProgressBar) convertView.findViewById(R.id.torrent_process);
                ImageView stateImage = (ImageView) convertView.findViewById(R.id.torrent_state_image);

                TextView tt = (TextView) convertView.findViewById(R.id.torrent_process_text);

                long p = Libtorrent.TorrentBytesCompleted(t.t) == 0 ? 0 : Libtorrent.TorrentBytesCompleted(t.t) * 100 / Libtorrent.TorrentBytesLength(t.t);

                Drawable d = null;
                switch (Libtorrent.TorrentStatus(t.t)) {
                    case Libtorrent.StatusPaused:
                        d = ContextCompat.getDrawable(getContext(), R.drawable.ic_pause_24dp);
                        stateImage.setColorFilter(ThemeUtils.getThemeColor(getContext(), R.attr.secondBackground));
                        stateImage.setAlpha(1f);
                        bar.getProgressDrawable().setColorFilter(ThemeUtils.getThemeColor(getContext(), R.attr.secondBackground), PorterDuff.Mode.SRC_IN);
                        tt.setText(p + "%");
                        break;
                    case Libtorrent.StatusDownloading:
                        d = ContextCompat.getDrawable(getContext(), R.drawable.play);
                        stateImage.setColorFilter(ThemeUtils.getThemeColor(getContext(), R.attr.colorAccent));
                        stateImage.setAlpha(0.3f);
                        bar.getProgressDrawable().setColorFilter(ThemeUtils.getThemeColor(getContext(), R.attr.colorAccent), PorterDuff.Mode.SRC_IN);
                        tt.setText(p + "%");
                        break;
                    case Libtorrent.StatusSeeding:
                        d = ContextCompat.getDrawable(getContext(), R.drawable.play);
                        stateImage.setColorFilter(ThemeUtils.getThemeColor(getContext(), R.attr.colorAccent));
                        stateImage.setAlpha(0.3f);
                        bar.getProgressDrawable().setColorFilter(ThemeUtils.getThemeColor(getContext(), R.attr.colorAccent), PorterDuff.Mode.SRC_IN);
                        tt.setText("Seed");
                        break;
                }
                stateImage.setImageDrawable(d);

                bar.setProgress((int) p);
            }

            ImageView expand = (ImageView) convertView.findViewById(R.id.torrent_expand);

            if (selected == position) {
                if (Tag.animate(convertView, TYPE_COLLAPSED, position))
                    RecordingAnimation.apply(list, convertView, true, scrollState == SCROLL_STATE_IDLE && Tag.animate(convertView, TYPE_COLLAPSED));
                Tag.setTag(convertView, TYPE_EXPANDED, position);

                final View rename = convertView.findViewById(R.id.recording_player_rename);
                rename.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Log.d(TAG, "Click Rename");
                        //renameDialog(t);
                    }
                });

                final View check = convertView.findViewById(R.id.recording_player_check);
                check.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Log.d(TAG, "Click Check");
                        //renameDialog(t);
                    }
                });

                final View share = convertView.findViewById(R.id.recording_player_share);
                share.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Log.d(TAG, "Click Share");
                        shareProvider = new PopupShareActionProvider(getContext(), share);

                        Intent emailIntent = new Intent(Intent.ACTION_SEND);
                        emailIntent.setType("text/plain");
                        emailIntent.putExtra(Intent.EXTRA_EMAIL, "");
                        emailIntent.putExtra(Intent.EXTRA_STREAM, Libtorrent.TorrentMagnet(t.t));
                        emailIntent.putExtra(Intent.EXTRA_SUBJECT, Libtorrent.TorrentName(t.t));
                        emailIntent.putExtra(Intent.EXTRA_TEXT, "Shared via " + getString(R.string.app_name));

                        shareProvider.setShareIntent(emailIntent);

                        shareProvider.show();
                    }
                });

                View trash = convertView.findViewById(R.id.recording_player_trash);
                trash.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Log.d(TAG, "Click Trash");
                        delete.run();
                    }
                });

                expand.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.ic_expand_less_black_24dp));
                expand.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Log.d(TAG, "Click Collapse");
                        select(-1);
                    }
                });
            } else {
                if (Tag.animate(convertView, TYPE_EXPANDED, position))
                    RecordingAnimation.apply(list, convertView, false, scrollState == SCROLL_STATE_IDLE && Tag.animate(convertView, TYPE_EXPANDED));
                Tag.setTag(convertView, TYPE_COLLAPSED, position);

                expand.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.ic_expand_more_black_24dp));
                expand.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Log.d(TAG, "Click Expand");
                        select(position);
                    }
                });
            }

            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Log.d(TAG, "Click Details");
                    showDetails(t.t);
                }
            });

            convertView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    PopupMenu popup = new PopupMenu(getContext(), v);
                    MenuInflater inflater = popup.getMenuInflater();
                    inflater.inflate(R.menu.menu_context, popup.getMenu());
                    popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            if (item.getItemId() == R.id.action_delete) {
                                delete.run();
                                return true;
                            }
                            if (item.getItemId() == R.id.action_rename) {
                                //renameDialog(t);
                                return true;
                            }
                            return false;
                        }
                    });
                    popup.show();
                    return true;
                }
            });

            return convertView;
        }

        public void select(int pos) {
            selected = pos;
            notifyDataSetChanged();
        }
    }

    void showDetails(Long f) {
        dialog = TorrentDialogFragment.create(f);
        dialog.show(getSupportFragmentManager(), "");
    }

    void renameDialog(final Long f) {
        final OpenFileDialog.EditTextDialog e = new OpenFileDialog.EditTextDialog(this);
        e.setTitle("Rename Torrent");
        e.setText(Libtorrent.TorrentName(f));
        e.setPositiveButton(new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Libtorrent.TorrentFileRename(f, 0, e.getText());
                load();
            }
        });
        e.show();
    }

    public MainApplication getApp() {
        return (MainApplication) getApplication();
    }

    public void setAppTheme(int id) {
        super.setTheme(id);

        themeId = id;
    }

    int getAppTheme() {
        return MainApplication.getTheme(this, R.style.AppThemeLight_NoActionBar, R.style.AppThemeDark_NoActionBar);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setAppTheme(getAppTheme());

        setContentView(R.layout.activity_main);

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setBackground(new ColorDrawable(MainApplication.getActionbarColor(this)));
        setSupportActionBar(toolbar);

        handler = new Handler();

        final FloatingActionsMenu fab = (FloatingActionsMenu) findViewById(R.id.fab);

        create = (FloatingActionButton) findViewById(R.id.torrent_create_button);
        create.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final OpenFileDialog f = new OpenFileDialog(MainActivity.this);

                String path = "";

                final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                shared.getString(MainApplication.PREFERENCE_STORAGE, "");

                if (path == null || path.isEmpty()) {
                    path = "/sdcard";
                }

                f.setCurrentPath(new File(path));
                f.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        File ff = f.getCurrentPath();
                        String fileName = ff.getPath();
                        //byte[] buf = Libtorrent.CreateTorrent(fileName);
                    }
                });
                f.show();
            }
        });
        add = (FloatingActionButton) findViewById(R.id.torrent_add_button);
        FloatingActionButton magnet = (FloatingActionButton) findViewById(R.id.torrent_magnet_button);
        magnet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final OpenFileDialog.EditTextDialog f = new OpenFileDialog.EditTextDialog(MainActivity.this);
                f.setTitle("Add Magnet");
                f.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String ff = f.getText();
                        String p = getStorage().getStoragePath().getPath();
                        long t = Libtorrent.AddMagnet(p, ff);
                        if (t == -1) {
                            Error(Libtorrent.Error());
                        }
                        getStorage().add(new Storage.Torrent(t, p));
                        torrents.notifyDataSetChanged();
                    }
                });
                f.show();

                fab.collapse();
            }
        });

        storage = new Storage(this);
        torrents = new Torrents(this);

        list = (ListView) findViewById(R.id.list);
        list.setOnScrollListener(this);
        list.setAdapter(torrents);
        list.setEmptyView(findViewById(R.id.empty_list));

        if (permitted()) {
            getStorage().migrateLocalStorage();
        } else {
            // with no permission we can't choise files to 'torrent', or select downloaded torrent
            // file, since we have no persmission to user files.
            create.setVisibility(View.GONE);
            add.setVisibility(View.GONE);
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        TorrentService.startService(this, storage.formatHeader());
    }

    // load torrents
    void load() {
//        if (torrents != null)
//            torrents.scan();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        getMenuInflater().inflate(R.menu.menu_main, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar base clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }

        if (id == R.id.action_shutdown) {
            finish();
            return true;
        }

        if (id == R.id.action_show_folder) {
            Uri selectedUri = Uri.fromFile(getStorage().getStoragePath());
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(selectedUri, "resource/folder");
            if (intent.resolveActivityInfo(getPackageManager(), 0) != null) {
                startActivity(intent);
            } else {
                Toast.makeText(this, "No folder view application installed", Toast.LENGTH_SHORT).show();
            }
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");

        if (themeId != getAppTheme()) {
            finish();
            MainActivity.startActivity(this);
            return;
        }

        refresh = new Runnable() {
            @Override
            public void run() {
                torrents.notifyDataSetChanged();

                getStorage().update();

                updateHeader();

                TorrentService.updateNotify(MainActivity.this, storage.formatHeader());

                torrents.update();

                if (dialog != null)
                    dialog.update();

                handler.removeCallbacks(refresh);
                handler.postDelayed(refresh, 1000);
            }
        };
        refresh.run();

        if (permitted(PERMISSIONS))
            load();
        else
            load();

        updateHeader();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (refresh != null) {
            handler.removeCallbacks(refresh);
            refresh = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 1:
                if (permitted(permissions)) {
                    getStorage().migrateLocalStorage();
                    load();
                    create.setVisibility(View.VISIBLE);
                    add.setVisibility(View.VISIBLE);
                } else {
                    Toast.makeText(this, "Not permitted", Toast.LENGTH_SHORT).show();
                }
        }
    }

    public static final String[] PERMISSIONS = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};

    boolean permitted(String[] ss) {
        for (String s : ss) {
            if (ContextCompat.checkSelfPermission(this, s) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    boolean permitted() {
        for (String s : PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, s) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, PERMISSIONS, 1);
                return false;
            }
        }
        return true;
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        this.scrollState = scrollState;
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        handler.post(new Runnable() {
            @Override
            public void run() {
                list.smoothScrollToPosition(torrents.selected);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (torrents != null)
            torrents.close();

        storage.close();

        TorrentService.stopService(this);
    }

    @Override
    public void onStart() {
        super.onStart();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client.connect();
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Main Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app deep link URI is correct.
                Uri.parse("android-app://com.github.axet.torrentclient/http/host/path")
        );
        AppIndex.AppIndexApi.start(client, viewAction);
    }


    @Override
    public void onStop() {
        super.onStop();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Main Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app deep link URI is correct.
                Uri.parse("android-app://com.github.axet.torrentclient/http/host/path")
        );
        AppIndex.AppIndexApi.end(client, viewAction);
        client.disconnect();
    }

    void Error(String err) {
        Log.e(TAG, Libtorrent.Error());

        new AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage(err)
                .setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // continue with delete
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    void Fatal(String err) {
        Log.e(TAG, Libtorrent.Error());

        new AlertDialog.Builder(this)
                .setTitle("Fatal")
                .setMessage(err)
                .setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        finish();
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    @Override
    public void onDismiss(DialogInterface dialogInterface) {
        dialog = null;
    }

    void updateHeader() {
        TextView text = (TextView) findViewById(R.id.space_left);
        text.setText(storage.formatHeader());
    }

    public Storage getStorage() {
        return storage;
    }

    public interface TorrentFragmentInterface {
        void update();
    }

    public static class TorrentPagerAdapter extends FragmentPagerAdapter {
        long t;

        Map<Integer, Fragment> map = new HashMap<>();

        public TorrentPagerAdapter(FragmentManager fm, long t) {
            super(fm);

            this.t = t;
        }

        @Override
        public Fragment getItem(int i) {
            Fragment f;

            switch (i) {
                case 0:
                    f = new DetailsFragment();
                    break;
                case 1:
                    f = new FilesFragment();
                    break;
                case 2:
                    f = new PeersFragment();
                    break;
                case 3:
                    f = new TrackersFragment();
                    break;
                default:
                    return null;
            }

            map.put(i, f);

            Bundle args = new Bundle();
            args.putLong("torrent", t);
            f.setArguments(args);

            return f;
        }

        public TorrentFragmentInterface getFragment(int i) {
            return (TorrentFragmentInterface) map.get(i);
        }

        @Override
        public int getCount() {
            return 4;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return "DETAILS";
                case 1:
                    return "FILES";
                case 2:
                    return "PEERS";
                case 3:
                    return "TRACKERS";
                default:
                    return "EMPTY";
            }
        }
    }

    public static class TorrentDialogFragment extends DialogFragment {
        ViewPager pager;

        public static TorrentDialogFragment create(Long t) {
            TorrentDialogFragment f = new TorrentDialogFragment();
            Bundle args = new Bundle();
            args.putLong("torrent", t);
            f.setArguments(args);
            return f;
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            super.onDismiss(dialog);
            final Activity activity = getActivity();
            if (activity instanceof DialogInterface.OnDismissListener) {
                ((DialogInterface.OnDismissListener) activity).onDismiss(dialog);
            }
        }

        public void update() {
            // dialog maybe created but onCreateView not yet called
            if (pager == null)
                return;

            int i = pager.getCurrentItem();
            TorrentPagerAdapter a = (TorrentPagerAdapter) pager.getAdapter();
            TorrentFragmentInterface f = a.getFragment(i);
            if (f == null)
                return;
            f.update();
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.torrent_details, container);

            View v = view.findViewById(R.id.torrent_close);
            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    getDialog().dismiss();
                }
            });

            long t = getArguments().getLong("torrent");

            pager = (ViewPager) view.findViewById(R.id.pager);
            TorrentPagerAdapter adapter = new TorrentPagerAdapter(getChildFragmentManager(), t);
            pager.setAdapter(adapter);

            TabLayout tabLayout = (TabLayout) view.findViewById(R.id.tab_layout);
            tabLayout.setupWithViewPager(pager);

            pager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabLayout));

            getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
            return view;
        }
    }
}
