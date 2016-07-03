package com.github.axet.torrentclient.activities;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
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
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupWindow;
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
import com.github.axet.torrentclient.fragments.DetailsFragment;
import com.github.axet.torrentclient.fragments.FilesFragment;
import com.github.axet.torrentclient.fragments.PeersFragment;
import com.github.axet.torrentclient.fragments.TrackersFragment;
import com.github.axet.torrentclient.animations.RecordingAnimation;
import com.github.axet.torrentclient.app.MainApplication;
import com.github.axet.torrentclient.app.Storage;
import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.api.GoogleApiClient;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import go.libtorrent.Libtorrent;

public class MainActivity extends AppCompatActivity implements AbsListView.OnScrollListener, DialogInterface.OnDismissListener, SharedPreferences.OnSharedPreferenceChangeListener {
    public final static String TAG = MainActivity.class.getSimpleName();

    static final int TYPE_COLLAPSED = 0;
    static final int TYPE_EXPANDED = 1;
    static final int TYPE_DELETED = 2;

    public final static String HIDE = "hide";

    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    private GoogleApiClient client;

    int scrollState;

    Runnable refresh;
    Runnable refreshUI;
    TorrentDialogFragment dialog;

    Torrents torrents;
    ProgressBar progress;
    ListView list;
    View empty;
    Handler handler;
    PopupShareActionProvider shareProvider;

    int themeId;

    // not delared locally - used from two places
    FloatingActionsMenu fab;
    FloatingActionButton create;
    FloatingActionButton add;

    // delayedIntent delayedIntent
    Intent delayedIntent;
    Thread initThread;
    Runnable delayedInit;

    BroadcastReceiver screenreceiver;

    public static void startActivity(Context context) {
        Intent i = new Intent(context, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(i);
    }

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

            notifyDataSetInvalidated();
        }

        public void close() {
        }

        public void changed() {
            super.notifyDataSetChanged();
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

            final View view = convertView;
            final View base = convertView.findViewById(R.id.recording_base);

            if (Tag.animate(convertView, TYPE_DELETED)) {
                RemoveItemAnimation.restore(base);
                convertView.setTag(null);
            }

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
                }
            });

            // we need runnable because we have View references
            final Runnable delete = new Runnable() {
                @Override
                public void run() {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                    builder.setTitle("Delete Torrent");

                    String name = Libtorrent.InfoTorrent(t.t) ? ".../" + t.name() : t.name();

                    builder.setMessage(name + "\n\n" + "Are you sure ? ");
                    if (Libtorrent.InfoTorrent(t.t)) {
                        builder.setNeutralButton("Delete With Data", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                                RemoveItemAnimation.apply(list, base, new Runnable() {
                                    @Override
                                    public void run() {
                                        t.stop();
                                        File f = new File(getStorage().getStoragePath(), t.name());
                                        FileUtils.deleteQuietly(f);
                                        getStorage().remove(t);
                                        Libtorrent.RemoveTorrent(t.t);
                                        Tag.setTag(view, TYPE_DELETED, -1);
                                        select(-1);
                                    }
                                });
                            }
                        });
                    }
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
                    int s = Libtorrent.TorrentStatus(t.t);
                    if (s == Libtorrent.StatusChecking) {
                        Libtorrent.StopTorrent(t.t);
                        return;
                    }
                    // library report for queue, we shall priority
                    if (s == Libtorrent.StatusQueued) {
                        getStorage().start(t);
                        return;
                    }

                    s = getStorage().status(t);

                    // queued from wifi, stop it
                    if (s == Libtorrent.StatusQueued) {
                        getStorage().stop(t);
                        return;
                    }

                    if (s == Libtorrent.StatusPaused)
                        getStorage().start(t);
                    else
                        getStorage().stop(t);
                    torrents.notifyDataSetChanged();
                }
            });

            {
                // should be done using states, so animation will apply
                ProgressBar bar = (ProgressBar) convertView.findViewById(R.id.torrent_process);
                ImageView stateImage = (ImageView) convertView.findViewById(R.id.torrent_state_image);

                TextView tt = (TextView) convertView.findViewById(R.id.torrent_process_text);

                long p = t.getProgress();

                int color = 0;
                String text = "";

                Drawable d = null;
                switch (getStorage().status(t)) {
                    case Libtorrent.StatusChecking:
                        d = ContextCompat.getDrawable(getContext(), R.drawable.ic_pause_24dp);
                        color = Color.YELLOW;
                        text = p + "%";
                        break;
                    case Libtorrent.StatusPaused:
                        d = ContextCompat.getDrawable(getContext(), R.drawable.ic_pause_24dp);
                        color = ThemeUtils.getThemeColor(getContext(), R.attr.secondBackground);
                        text = p + "%";
                        break;
                    case Libtorrent.StatusQueued:
                        d = ContextCompat.getDrawable(getContext(), R.drawable.ic_pause_24dp);
                        color = Color.GREEN;
                        text = "Qued";
                        break;
                    case Libtorrent.StatusDownloading:
                        d = ContextCompat.getDrawable(getContext(), R.drawable.play);
                        color = ThemeUtils.getThemeColor(getContext(), R.attr.colorAccent);
                        text = p + "%";
                        break;
                    case Libtorrent.StatusSeeding:
                        d = ContextCompat.getDrawable(getContext(), R.drawable.play);
                        color = ThemeUtils.getThemeColor(getContext(), R.attr.colorAccent);
                        text = "Seed";
                        break;
                }
                PorterDuffColorFilter filter = new PorterDuffColorFilter(0x60000000 | (0xFFFFFF & color), PorterDuff.Mode.MULTIPLY);
                stateImage.setColorFilter(filter);
                stateImage.setImageDrawable(d);

                bar.getBackground().setColorFilter(filter);
                bar.getProgressDrawable().setColorFilter(color, PorterDuff.Mode.SRC_IN);
                bar.setProgress((int) p);

                tt.setText(text);
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
                        //renameDialog(t);
                    }
                });

                final ImageView check = (ImageView) convertView.findViewById(R.id.recording_player_check);

                final Runnable checkUpdate = new Runnable() {
                    @Override
                    public void run() {
                        if (Libtorrent.TorrentStatus(t.t) == Libtorrent.StatusChecking) {
                            check.setImageDrawable(ContextCompat.getDrawable(MainActivity.this, R.drawable.ic_stop_black_24dp));
                        } else {
                            check.setImageDrawable(ContextCompat.getDrawable(MainActivity.this, R.drawable.ic_done_all_black_24dp));
                        }
                    }
                };

                checkUpdate.run();

                check.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (Libtorrent.TorrentStatus(t.t) == Libtorrent.StatusChecking) {
                            Libtorrent.StopTorrent(t.t);
                            Toast.makeText(MainActivity.this, "Stop Checking", Toast.LENGTH_SHORT).show();
                            checkUpdate.run();
                            return;
                        }

                        Libtorrent.CheckTorrent(t.t);
                        Toast.makeText(MainActivity.this, "Start Checking", Toast.LENGTH_SHORT).show();
                        checkUpdate.run();
                    }
                });

                switch (Libtorrent.TorrentStatus(t.t)) {
                    case Libtorrent.StatusPaused:
                    case Libtorrent.StatusChecking:
                        check.setColorFilter(ThemeUtils.getThemeColor(getContext(), R.attr.colorAccent));
                        break;
                    default:
                        check.setColorFilter(Color.GRAY);
                        check.setOnClickListener(null);
                }

                final View share = convertView.findViewById(R.id.recording_player_share);
                share.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        shareProvider = new PopupShareActionProvider(getContext(), share);

                        Intent emailIntent = new Intent(Intent.ACTION_SEND);
                        emailIntent.setType("text/plain");
                        emailIntent.putExtra(Intent.EXTRA_EMAIL, "");
                        emailIntent.putExtra(Intent.EXTRA_SUBJECT, Libtorrent.TorrentName(t.t));
                        emailIntent.putExtra(Intent.EXTRA_TEXT, Libtorrent.TorrentMagnet(t.t));

                        shareProvider.setShareIntent(emailIntent);

                        shareProvider.show();
                    }
                });

                View trash = convertView.findViewById(R.id.recording_player_trash);
                trash.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        delete.run();
                    }
                });

                expand.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.ic_expand_less_black_24dp));
                expand.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
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
                        select(position);
                    }
                });
            }

            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
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

    public static class OpenIntentDialogFragment extends DialogFragment {
        Handler handler = new Handler();

        @Override
        public void onDismiss(DialogInterface dialog) {
            super.onDismiss(dialog);
            final Activity activity = getActivity();
        }

        @Override
        public void onStart() {
            super.onStart();

            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    final MainActivity activity = (MainActivity) getActivity();
                    try {
                        openURL(getArguments().getString("url"));
                    } catch (final RuntimeException e) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                activity.Error(e.getMessage());
                            }
                        });
                    }
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            dismiss();
                        }
                    });
                }
            });
            t.start();
        }

        public void openURL(final String str) {
            final MainActivity activity = (MainActivity) getActivity();
            final Storage storage = activity.getStorage();

            if (str.startsWith("magnet")) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        activity.addMagnet(str);
                    }
                });
                return;
            }

            if (str.startsWith("content")) {
                try {
                    Uri uri = Uri.parse(str);
                    final byte[] buf = IOUtils.toByteArray(activity.getContentResolver().openInputStream(uri));
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            activity.addTorrent(buf);
                        }
                    });
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return;
            }

            if (str.startsWith("http")) {
                try {
                    final byte[] buf = IOUtils.toByteArray(new URL(str));
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            activity.addTorrent(buf);
                        }
                    });
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return;
            }

            if (str.startsWith("file")) {
                Uri uri = Uri.parse(str);
                try {
                    String path = uri.getEncodedPath();
                    final String s = URLDecoder.decode(path, "UTF-8");
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            activity.addTorrentFromFile(s);
                        }
                    });
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }
                return;
            }

            // .torrent?
            if (new File(str).exists()) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        activity.addTorrentFromFile(str);
                    }
                });
            }

            return;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            ProgressBar view = new ProgressBar(inflater.getContext());
            view.setIndeterminate(true);

            // wait until torrent loaded
            setCancelable(false);

            //getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
            return view;
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

        fab = (FloatingActionsMenu) findViewById(R.id.fab);

        create = (FloatingActionButton) findViewById(R.id.torrent_create_button);
        create.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final OpenFileDialog f = new OpenFileDialog(MainActivity.this);

                String path = "";

                final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);

                if (path == null || path.isEmpty()) {
                    path = shared.getString(MainApplication.PREFERENCE_LAST_PATH, Environment.getExternalStorageDirectory().getPath());
                }

                f.setCurrentPath(new File(path));
                f.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        File p = f.getCurrentPath();

                        shared.edit().putString(MainApplication.PREFERENCE_LAST_PATH, p.getParent()).commit();

                        File pp = p.getParentFile();
                        long t = Libtorrent.CreateTorrent(p.getPath());
                        if (t == -1) {
                            Error(Libtorrent.Error());
                            return;
                        }
                        getStorage().add(new Storage.Torrent(t, pp.getPath()));
                        torrents.notifyDataSetChanged();
                    }
                });
                f.show();
                fab.collapse();
            }
        });

        add = (FloatingActionButton) findViewById(R.id.torrent_add_button);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final OpenFileDialog f = new OpenFileDialog(MainActivity.this);

                String path = "";

                final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);

                if (path == null || path.isEmpty()) {
                    path = shared.getString(MainApplication.PREFERENCE_LAST_PATH, Environment.getExternalStorageDirectory().getPath());
                }

                f.setCurrentPath(new File(path));
                f.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        File p = f.getCurrentPath();

                        shared.edit().putString(MainApplication.PREFERENCE_LAST_PATH, p.getParent()).commit();

                        addTorrentFromFile(p.getPath());
                    }
                });
                f.show();
                fab.collapse();
            }
        });

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
                        addMagnet(ff);
                    }
                });
                f.show();
                fab.collapse();
            }
        });

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        progress = (ProgressBar) findViewById(R.id.progress);

        list = (ListView) findViewById(R.id.list);
        empty = findViewById(R.id.empty_list);

        fab.setVisibility(View.GONE);
        empty.setVisibility(View.GONE);
        list.setVisibility(View.GONE);
        fab.setVisibility(View.GONE);

        screenreceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                    Log.d(TAG, "Screen OFF");
                    onBackPressed();
                }
            }
        };
        IntentFilter screenfilter = new IntentFilter();
        screenfilter.addAction(Intent.ACTION_SCREEN_ON);
        screenfilter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenreceiver, screenfilter);

        delayedIntent = getIntent();

        delayedInit = new Runnable() {
            @Override
            public void run() {
                progress.setVisibility(View.GONE);
                list.setVisibility(View.VISIBLE);
                fab.setVisibility(View.VISIBLE);

                invalidateOptionsMenu();

                list.setOnScrollListener(MainActivity.this);
                list.setEmptyView(findViewById(R.id.empty_list));

                final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                shared.registerOnSharedPreferenceChangeListener(MainActivity.this);

                torrents = new Torrents(MainActivity.this);

                list.setAdapter(torrents);

                if (permitted()) {
                    try {
                        getStorage().migrateLocalStorage();
                    } catch (RuntimeException e) {
                        Error(e.getMessage());
                    }
                } else {
                    // with no permission we can't choise files to 'torrent', or select downloaded torrent
                    // file, since we have no persmission to user files.
                    create.setVisibility(View.GONE);
                    add.setVisibility(View.GONE);
                }

                if (delayedIntent != null) {
                    openIntent(delayedIntent);
                    delayedIntent = null;
                }
            }
        };

        updateHeader(new Storage(this));

        initThread = new Thread(new Runnable() {
            @Override
            public void run() {
                getApp().create();

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        // acctivity can be destoryed already do not init
                        if (delayedInit != null) {
                            delayedInit.run();
                            delayedInit = null;
                        }
                    }
                });
            }
        });
        initThread.start();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(MainApplication.PREFERENCE_ANNOUNCE)) {
            Libtorrent.SetDefaultAnnouncesList(sharedPreferences.getString(MainApplication.PREFERENCE_ANNOUNCE, ""));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        getMenuInflater().inflate(R.menu.menu_main, menu);

        KeyguardManager myKM = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (myKM.inKeyguardRestrictedInputMode() || delayedInit != null) {
            menu.removeItem(R.id.action_settings);
            menu.removeItem(R.id.action_show_folder);
        }

        return true;
    }

    public void close() {
        if (initThread != null) {
            try {
                initThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            // prevent delayed delayedInit
            delayedInit = null;
        }

        refreshUI = null;

        if (refresh != null) {
            handler.removeCallbacks(refresh);
            refresh = null;
        }

        if (torrents != null) {
            torrents.close();
            torrents = null;
        }

        Storage s = getStorage();
        if (s != null)
            s.save();

        if (screenreceiver != null) {
            unregisterReceiver(screenreceiver);
            screenreceiver = null;
        }

        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        shared.unregisterOnSharedPreferenceChangeListener(MainActivity.this);

        // do not close storage when mainactivity closes. it may be restarted due to theme change.
        // only close it on shutdown()
        // getApp().close();
    }

    public void shutdown() {
        close();
        getApp().close();
        finishAffinity();
        ExitActivity.exitApplication(this);
    }

    public void Error(String err) {
        Log.e(TAG, Libtorrent.Error());

        new AlertDialog.Builder(MainActivity.this)
                .setTitle("Error")
                .setMessage(err)
                .setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    public void Fatal(String err) {
        Log.e(TAG, Libtorrent.Error());

        new AlertDialog.Builder(this)
                .setTitle("Fatal")
                .setMessage(err)
                .setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        shutdown();
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        shutdown();
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
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
            shutdown();
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

        invalidateOptionsMenu();

        KeyguardManager myKM = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (myKM.inKeyguardRestrictedInputMode()) {
            add.setVisibility(View.GONE);
            create.setVisibility(View.GONE);
        } else {
            if (permitted(PERMISSIONS)) {
                add.setVisibility(View.VISIBLE);
                create.setVisibility(View.VISIBLE);
            }
        }

        if (themeId != getAppTheme()) {
            finish();
            MainActivity.startActivity(this);
            return;
        }

        refreshUI = new Runnable() {
            @Override
            public void run() {
                torrents.notifyDataSetChanged();

                if (dialog != null)
                    dialog.update();
            }
        };

        refresh = new Runnable() {
            @Override
            public void run() {
                handler.removeCallbacks(refresh);
                handler.postDelayed(refresh, 1000);

                if (delayedInit != null)
                    return;

                getStorage().update();

                updateHeader(getStorage());

                torrents.update();

                if (refreshUI != null)
                    refreshUI.run();
            }
        };
        refresh.run();
    }

    @Override
    protected void onPause() {
        super.onPause();

        refreshUI = null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 1:
                if (permitted(permissions)) {
                    try {
                        getStorage().migrateLocalStorage();
                    } catch (RuntimeException e) {
                        Error(e.getMessage());
                    }
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
                if (delayedInit == null)
                    list.smoothScrollToPosition(torrents.selected);
            }
        });
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();

        close();
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

    @Override
    public void onDismiss(DialogInterface dialogInterface) {
        dialog = null;
    }

    void updateHeader(Storage s) {
        TextView text = (TextView) findViewById(R.id.space_left);
        text.setText(s.formatHeader());
    }

    public Storage getStorage() {
        return getApp().getStorage();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (delayedInit == null)
            openIntent(intent);
        else
            this.delayedIntent = intent;
    }

    void openIntent(Intent intent) {
        if (intent == null)
            return;

        Uri openUri = intent.getData();
        if (openUri == null)
            return;

        OpenIntentDialogFragment dialog = new OpenIntentDialogFragment();

        Bundle args = new Bundle();
        args.putString("url", openUri.toString());

        dialog.setArguments(args);
        dialog.show(getSupportFragmentManager(), "");
    }

    void addMagnet(String ff) {
        try {
            getStorage().addMagnet(ff);
        } catch (RuntimeException e) {
            Error(e.getMessage());
        }
        torrents.notifyDataSetChanged();
    }

    void addTorrentFromFile(String p) {
        try {
            getStorage().addTorrentFromFile(p);
        } catch (RuntimeException e) {
            Error(e.getMessage());
        }
        torrents.notifyDataSetChanged();
    }

    void addTorrentFromURL(String p) {
        try {
            getStorage().addTorrentFromURL(p);
        } catch (RuntimeException e) {
            Error(e.getMessage());
        }
        torrents.notifyDataSetChanged();
    }

    void addTorrent(byte[] buf) {
        try {
            getStorage().addTorrent(buf);
        } catch (RuntimeException e) {
            Error(e.getMessage());
        }
        torrents.notifyDataSetChanged();
    }
}
