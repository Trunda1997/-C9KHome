package de.tcl.c9khome;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Activity coordinates lifecycle and navigation; screens and data live separately. */
public class MainActivity extends Activity implements ScreenHost {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService dataWorker = Executors.newSingleThreadExecutor();
    private final List<View> topItems = new ArrayList<>();
    private final EnumMap<ScreenId, View> menu = new EnumMap<>(ScreenId.class);
    private final Deque<Route> history = new ArrayDeque<>();
    private List<LaunchableApps.Entry> apps = new ArrayList<>();
    private List<EntertainmentAppRepository.Provider> installedProviders = new ArrayList<>();
    private TvUi tvUi;
    private FavoriteApps favoriteStore;
    private InstalledAppsRepository appsRepository;
    private EntertainmentAppRepository entertainmentRepository;
    private MediaRepository mediaRepository;
    private LiveTvRepository liveRepository;
    private HdmiRepository hdmiRepository;
    private FavoritesController favoriteController;
    private HdmiSection hdmiSection;
    private FocusNavigator navigator;
    private LinearLayout pageHost;
    private ScrollView scroll;
    private ScreenView page;
    private Route route = new Route(ScreenId.HOME, ScreenId.HOME);
    private boolean started, receiverRegistered, firstData = true;
    private int dataGeneration;
    private TextView clock;

    private static final class Route {
        final ScreenId screen, area;
        String genre, provider;
        MediaItem item;
        int scrollY;
        Object focusTag;
        int focusId = View.NO_ID;
        Bundle inputs = new Bundle();
        Route(ScreenId screen, ScreenId area) { this.screen = screen; this.area = area; }
    }

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        tvUi = new TvUi(this);
        favoriteStore = new FavoriteApps(this);
        appsRepository = new InstalledAppsRepository(this);
        entertainmentRepository = new EntertainmentAppRepository(this);
        mediaRepository = new MediaRepository(this);
        liveRepository = new LiveTvRepository(this);
        hdmiRepository = new HdmiRepository(this);
        navigator = new FocusNavigator(this);
        favoriteController = new FavoritesController(this);
        hdmiSection = new HdmiSection(this, hdmiRepository);
        if (saved != null) {
            try {
                ScreenId restored = ScreenId.valueOf(saved.getString("screen", "HOME"));
                if (restored != ScreenId.DETAIL) {
                    ScreenId area = ScreenId.valueOf(saved.getString("area", "HOME"));
                    route = new Route(restored, area);
                    route.genre = saved.getString("genre");
                    route.provider = saved.getString("provider");
                    route.inputs = saved.getBundle("inputs") == null ? new Bundle() : saved.getBundle("inputs");
                    if (restored != ScreenId.HOME) history.push(new Route(ScreenId.HOME, ScreenId.HOME));
                }
            } catch (IllegalArgumentException ignored) {}
        }
        buildShell();
        render(false);
        if (Build.VERSION.SDK_INT >= 33) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::back);
    }

    @Override protected void onStart() {
        super.onStart();
        started = true;
        hdmiRepository.start(() -> { hdmiSection.refresh(); rebindFocus(); });
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(packagesChanged, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(packagesChanged, filter);
        receiverRegistered = true;
        reloadData();
        main.post(clockTick);
    }

    @Override protected void onStop() {
        started = false;
        dataGeneration++;
        favoriteController.handleBack();
        hdmiRepository.stop();
        if (receiverRegistered) { unregisterReceiver(packagesChanged); receiverRegistered = false; }
        main.removeCallbacks(clockTick);
        super.onStop();
    }

    @Override protected void onDestroy() {
        dataWorker.shutdownNow();
        tvUi.close();
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override protected void onSaveInstanceState(Bundle saved) {
        captureRoute();
        saved.putString("screen", route.screen.name()); saved.putString("area", route.area.name());
        saved.putString("genre", route.genre); saved.putString("provider", route.provider);
        saved.putBundle("inputs", route.inputs);
        super.onSaveInstanceState(saved);
    }

    private final BroadcastReceiver packagesChanged = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())
                    && !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false) && intent.getData() != null) {
                String removed = intent.getData().getSchemeSpecificPart();
                mediaRepository.purgePackage(removed);
                liveRepository.purgePackage(removed);
                tvUi.clearPackage(removed);
            }
            if (!intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                    || !Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())) reloadData();
        }
    };

    private void reloadData() {
        final int generation = ++dataGeneration;
        dataWorker.execute(() -> {
            List<LaunchableApps.Entry> nextApps = appsRepository.load();
            List<EntertainmentAppRepository.Provider> nextProviders = entertainmentRepository.detect(nextApps);
            favoriteStore.initializeIfMissing(nextApps);
            mediaRepository.refresh(nextProviders);
            liveRepository.refresh(nextProviders);
            main.post(() -> {
                if (!started || isFinishing() || generation != dataGeneration) return;
                apps = nextApps;
                installedProviders = nextProviders;
                if (route.provider != null) {
                    boolean present = false;
                    for (EntertainmentAppRepository.Provider p : nextProviders) if (p.packageName.equals(route.provider)) present = true;
                    if (!present) route.provider = null;
                }
                if (favoriteController.isMoving()) return; // Never interrupt an in-progress reorder.
                captureRoute();
                render(true);
                if (firstData) { reveal(firstContent(), View.FOCUS_DOWN); firstData = false; }
            });
        });
    }

    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            clock.setText(new SimpleDateFormat("HH:mm", Locale.GERMANY).format(new java.util.Date()));
            main.postDelayed(this, 30000);
        }
    };

    private void buildShell() {
        getWindow().setStatusBarColor(Color.rgb(6, 11, 20));
        getWindow().setNavigationBarColor(Color.rgb(6, 11, 20));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(tvUi.dp(36), tvUi.dp(22), tvUi.dp(36), 0);
        ScreenView.passive(root);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(9, 22, 36), Color.rgb(8, 11, 20), Color.rgb(25, 15, 30)}));
        buildTop(root);
        scroll = new ScrollView(this);
        scroll.setId(R.id.page_content);
        scroll.setFillViewport(true); scroll.setClipToPadding(false);
        ScreenView.passive(scroll);
        pageHost = new LinearLayout(this);
        pageHost.setOrientation(LinearLayout.VERTICAL);
        pageHost.setPadding(tvUi.dp(4), tvUi.dp(22), tvUi.dp(4), tvUi.dp(40));
        ScreenView.passive(pageHost);
        scroll.addView(pageHost, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void buildTop(LinearLayout root) {
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL); top.setGravity(Gravity.CENTER_VERTICAL);
        ScreenView.passive(top);
        root.addView(top, new LinearLayout.LayoutParams(-1, tvUi.dp(68)));
        TextView logo = tvUi.text("TCL", 30, true); logo.setTextColor(Color.rgb(255, 60, 65));
        logo.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(logo, new LinearLayout.LayoutParams(tvUi.dp(96), -1));
        HorizontalScrollView viewport = new HorizontalScrollView(this);
        ScreenView.passive(viewport); viewport.setHorizontalScrollBarEnabled(false);
        viewport.setPadding(tvUi.dp(5), tvUi.dp(5), tvUi.dp(5), tvUi.dp(5)); viewport.setClipToPadding(false);
        LinearLayout nav = new LinearLayout(this); nav.setOrientation(LinearLayout.HORIZONTAL);
        ScreenView.passive(nav);
        viewport.addView(nav, new HorizontalScrollView.LayoutParams(-2, -1));
        top.addView(viewport, new LinearLayout.LayoutParams(0, -1, 1));
        ScreenId[] areas = {ScreenId.SEARCH, ScreenId.HOME, ScreenId.LIVE_TV, ScreenId.MOVIES, ScreenId.SERIES, ScreenId.APPS};
        String[] labels = {"⌕", "Startseite", "Live TV", "Filme", "Serien", "Apps"};
        int[] ids = {R.id.top_bar_search, R.id.top_bar_home, R.id.top_bar_live_tv,
                R.id.top_bar_movies, R.id.top_bar_series, R.id.top_bar_apps};
        for (int i = 0; i < areas.length; i++) {
            ScreenId area = areas[i];
            TextView item = tvUi.text(labels[i], i == 0 ? 29 : 16, false);
            item.setId(ids[i]); item.setContentDescription(i == 0 ? "Suche" : labels[i]);
            item.setGravity(Gravity.CENTER); item.setPadding(tvUi.dp(14), 0, tvUi.dp(14), 0);
            item.setFocusable(true); item.setFocusableInTouchMode(true);
            item.setOnClickListener(v -> navigate(area));
            tvUi.styleFocus(item); menu.put(area, item); topItems.add(item);
            nav.addView(item, new LinearLayout.LayoutParams(-2, -1));
        }
        clock = tvUi.text("", 16, false); clock.setGravity(Gravity.CENTER);
        top.addView(clock, new LinearLayout.LayoutParams(tvUi.dp(74), -1));
        TextView gear = tvUi.text("⚙", 29, false); gear.setId(R.id.top_bar_settings);
        gear.setGravity(Gravity.CENTER); gear.setContentDescription("Einstellungen");
        gear.setFocusable(true); gear.setFocusableInTouchMode(true);
        gear.setOnClickListener(v -> LauncherUtils.openSettings(this));
        tvUi.styleFocus(gear); topItems.add(gear);
        top.addView(gear, new LinearLayout.LayoutParams(tvUi.dp(54), -1));
    }

    private void captureRoute() {
        if (scroll != null) route.scrollY = scroll.getScrollY();
        View focused = getCurrentFocus();
        if (focused != null) { route.focusTag = focused.getTag(); route.focusId = focused.getId(); }
        int[] inputs = {R.id.media_search_input, R.id.apps_search_input};
        for (int id : inputs) {
            EditText field = findViewById(id);
            if (field != null) route.inputs.putString(String.valueOf(id), field.getText().toString());
        }
    }

    private void render(boolean restore) {
        View old = getCurrentFocus();
        boolean topFocus = topItems.contains(old);
        pageHost.removeAllViews();
        switch (route.screen) {
            case SEARCH: page = SearchScreen.build(this); break;
            case HOME: page = HomeScreen.build(this); break;
            case LIVE_TV: page = LiveTvScreen.build(this); break;
            case MOVIES: page = MoviesScreen.build(this); break;
            case SERIES: page = SeriesScreen.build(this); break;
            case APPS: page = AppsScreen.build(this); break;
            case GENRE: page = GenreScreen.build(this, route.genre); break;
            case DETAIL: page = MediaDetailScreen.build(this, route.item); break;
        }
        pageHost.addView(page.root, new LinearLayout.LayoutParams(-1, -2));
        for (View item : topItems) { item.setSelected(item == activeMenu()); tvUi.styleFocus(item); }
        for (String key : route.inputs.keySet()) {
            EditText field = findViewById(Integer.parseInt(key));
            if (field != null) {
                field.setText(route.inputs.getString(key, ""));
                if (route.screen == ScreenId.SEARCH && field.length() > 0) {
                    field.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
                }
            }
        }
        rebindFocus();
        View target = topFocus ? old : null;
        if (restore && !topFocus) {
            for (List<View> row : page.focusRows()) for (View item : row) {
                if (route.focusTag != null ? route.focusTag.equals(item.getTag())
                        : route.focusId != View.NO_ID && route.focusId == item.getId()) target = item;
            }
        }
        reveal(target != null ? target : activeMenu(), View.FOCUS_DOWN);
        scroll.post(() -> scroll.scrollTo(0, restore ? route.scrollY : 0));
    }

    private View activeMenu() { return menu.get(route.area); }
    private View firstContent() {
        List<List<View>> rows = page.focusRows();
        return rows.isEmpty() ? activeMenu() : rows.get(0).get(0);
    }

    @Override public void onBackPressed() { back(); }
    private void back() {
        if (favoriteController.handleBack()) return;
        if (!history.isEmpty()) { captureRoute(); route = history.pop(); render(true); }
        else if (route.screen != ScreenId.HOME) navigate(ScreenId.HOME);
        else reveal(menu.get(ScreenId.HOME), View.FOCUS_UP);
    }

    @Override public void navigate(ScreenId screen) {
        if (screen == route.screen) { scroll.scrollTo(0, 0); return; }
        favoriteController.stop(); captureRoute();
        history.clear();
        if (screen != ScreenId.HOME) history.push(new Route(ScreenId.HOME, ScreenId.HOME));
        route = new Route(screen, screen); render(false);
    }
    @Override public void openGenre(String genre) {
        favoriteController.stop(); captureRoute(); history.push(route);
        Route next = new Route(ScreenId.GENRE, route.area); next.genre = genre; next.provider = route.provider;
        route = next; render(false); reveal(firstContent(), View.FOCUS_DOWN);
    }
    @Override public void openMedia(MediaItem item) {
        if (item == null) return;
        captureRoute(); history.push(route);
        Route next = new Route(ScreenId.DETAIL, route.area); next.item = item;
        route = next; render(false); reveal(firstContent(), View.FOCUS_DOWN);
    }
    @Override public Activity activity() { return this; }
    @Override public TvUi ui() { return tvUi; }
    @Override public List<LaunchableApps.Entry> installedApps() { return apps; }
    @Override public List<EntertainmentAppRepository.Provider> providers() { return installedProviders; }
    @Override public FavoriteApps favorites() { return favoriteStore; }
    @Override public MediaRepository media() { return mediaRepository; }
    @Override public LiveTvRepository liveTv() { return liveRepository; }
    @Override public View appTile(LaunchableApps.Entry entry, boolean home) { return favoriteController.appTile(entry, home); }
    @Override public void addFavorites(ScreenView view) { favoriteController.addTo(view); }
    @Override public void addHdmi(ScreenView view) { hdmiSection.addTo(view); }
    @Override public void rebindFocus() { if (page != null) navigator.bind(topItems, activeMenu(), page); }
    @Override public void reveal(View view, int direction) { FocusNavigator.reveal(view, direction); }
    @Override public boolean handleSpecialKey(View view, int code, KeyEvent event) { return favoriteController.handleKey(view, code, event); }
    @Override public void refreshScreen() { captureRoute(); render(true); }
    @Override public String providerFilter() { return route.provider; }
    @Override public void setProviderFilter(String packageName) { route.provider = packageName; }
}
