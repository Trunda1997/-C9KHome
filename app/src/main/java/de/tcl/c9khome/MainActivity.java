package de.tcl.c9khome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvInputManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private final int BG = Color.rgb(7, 11, 18);
    private final int TILE = Color.argb(210, 28, 33, 43);
    private final int TILE_FOCUS = Color.argb(245, 52, 61, 77);
    private LinearLayout root;
    private LinearLayout hdmiRow;
    private LinearLayout appsRow;
    private LinearLayout pageHost;
    private ScrollView pageScroll;
    private GridLayout allAppsGrid;
    private final List<ViewGroup> horizontalRows = new ArrayList<>();
    private List<LaunchableApps.Entry> installedApps = new ArrayList<>();
    private enum Page { HOME, APPS, MOVIES, SERIES, LIVE_TV }
    private Page currentPage = Page.HOME;
    private final List<View> topBarItems = new ArrayList<>();
    private boolean initialFocusAssigned;
    private TextView clock;
    private HdmiRepository hdmiRepository;
    private final Handler handler = new Handler();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        hdmiRepository = new HdmiRepository(this);
        if (savedInstanceState != null) {
            try { currentPage = Page.valueOf(savedInstanceState.getString("page", "HOME")); }
            catch (IllegalArgumentException ignored) {}
        }
        buildUi();
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::returnHome);
        }
    }

    @Override protected void onStart() {
        super.onStart();
        hdmiRepository.start(() -> {
            refreshHdmi();
            connectPageFocus();
        });
        installedApps = LaunchableApps.load(this);
        renderPage();
        if (!initialFocusAssigned) {
            focusAndReveal(firstContent(), View.FOCUS_DOWN);
            initialFocusAssigned = true;
        }
        handler.post(clockTick);
    }

    @Override protected void onStop() {
        hdmiRepository.stop();
        handler.removeCallbacks(clockTick);
        super.onStop();
    }

    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            if (clock != null) clock.setText(new SimpleDateFormat("HH:mm   EEE, d. MMM", Locale.GERMANY).format(new Date()));
            handler.postDelayed(this, 30000);
        }
    };

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putString("page", currentPage.name());
        super.onSaveInstanceState(outState);
    }

    @Override public void onBackPressed() { returnHome(); }

    private void returnHome() {
        showPage(Page.HOME);
        focusAndReveal(topBarItems.get(0), View.FOCUS_UP);
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        configureFocusContainer(root);
        root.setPadding(dp(48), dp(28), dp(48), 0);
        root.setBackground(makeGradient());
        buildTopBar();

        // Only the page contents scroll. The navigation remains reachable and
        // visible while switching pages or browsing a long app list.
        pageScroll = new ScrollView(this);
        pageScroll.setId(R.id.page_content);
        pageScroll.setFillViewport(true);
        configureFocusContainer(pageScroll);
        root.addView(pageScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        pageHost = new LinearLayout(this);
        pageHost.setOrientation(LinearLayout.VERTICAL);
        pageHost.setPadding(0, dp(26), 0, dp(40));
        configureFocusContainer(pageHost);
        pageScroll.addView(pageHost, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);
    }

    private void showPage(Page page) {
        // Also catch installs/removals that happened while the launcher stayed
        // in the foreground (for example through ADB).
        if (page == Page.APPS) installedApps = LaunchableApps.load(this);
        if (currentPage != page || page == Page.APPS || pageHost.getChildCount() == 0) {
            currentPage = page;
            renderPage();
        }
        pageScroll.scrollTo(0, 0);
    }

    private void renderPage() {
        View previousFocus = getCurrentFocus();
        boolean keepTopFocus = topBarItems.contains(previousFocus);
        Object previousApp = previousFocus == null ? null : previousFocus.getTag();
        hdmiRow = null;
        appsRow = null;
        allAppsGrid = null;
        horizontalRows.clear();
        pageHost.removeAllViews();
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        configureFocusContainer(page);
        int[] pageIds = {R.id.page_home, R.id.page_apps, R.id.page_movies,
                R.id.page_series, R.id.page_live_tv};
        page.setId(pageIds[currentPage.ordinal()]);
        pageHost.addView(page, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        switch (currentPage) {
            case HOME: buildHomePage(page); break;
            case APPS: buildAppsPage(page); break;
            case MOVIES: buildMediaPage(page, "Filme", "Hier entsteht deine Filmübersicht."); break;
            case SERIES: buildMediaPage(page, "Serien", "Hier entsteht deine Serienübersicht."); break;
            case LIVE_TV: buildLiveTvPage(page); break;
        }
        updateActiveMenu();
        connectPageFocus();
        if (keepTopFocus) {
            focusAndReveal(previousFocus, View.FOCUS_DOWN);
        } else {
            View restore = null;
            if (previousApp != null) {
                for (List<View> row : contentRows()) {
                    for (View item : row) if (previousApp.equals(item.getTag())) restore = item;
                }
            }
            focusAndReveal(restore != null ? restore : firstContent(), View.FOCUS_DOWN);
        }
    }

    private void buildHomePage(LinearLayout page) {
        addSection(page, "Meine Apps");
        appsRow = addHorizontalRow(page);
        appsRow.setId(R.id.apps_row);
        refreshApps();
        addSpacer(page, 26);
        addSection(page, "HDMI & Eingänge");
        hdmiRow = addHorizontalRow(page);
        refreshHdmi();
        addSpacer(page, 26);
        addSection(page, "System");
        LinearLayout systemRow = addHorizontalRow(page);
        systemRow.addView(createActionTile("Google Play Store", R.drawable.logo_playstore, v -> LauncherUtils.openPlayStore(this)));
        systemRow.addView(createActionTile("Einstellungen", R.drawable.logo_settings, v -> LauncherUtils.openSettings(this)));
        systemRow.addView(createActionTile("TCL Eingänge", R.drawable.logo_hdmi, v -> {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            catch (Exception ignored) {}
        }));
    }

    private void buildAppsPage(LinearLayout page) {
        addSection(page, "Alle Apps");
        addDescription(page, installedApps.size() + " startbare Apps – TV-Apps und weitere installierte Apps");
        allAppsGrid = new GridLayout(this);
        allAppsGrid.setId(R.id.all_apps_grid);
        configureFocusContainer(allAppsGrid);
        int availableDp = (int) (getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density) - 96;
        int columns = Math.max(1, Math.min(6, availableDp / 180));
        allAppsGrid.setColumnCount(columns);
        page.addView(allAppsGrid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        for (int i = 0; i < installedApps.size(); i++) {
            View tile = createAppTile(installedApps.get(i));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(i / columns), GridLayout.spec(i % columns, 1f));
            lp.width = 0;
            lp.height = dp(150);
            lp.setMargins(dp(6), dp(8), dp(6), dp(8));
            allAppsGrid.addView(tile, lp);
        }
        if (installedApps.isEmpty()) {
            addDescription(page, "Es wurden keine startbaren Apps gefunden.");
            addBackHome(page);
        }
    }

    private void buildMediaPage(LinearLayout page, String title, String description) {
        addSection(page, title);
        addDescription(page, description);
        addBackHome(page);
    }

    private void buildLiveTvPage(LinearLayout page) {
        addSection(page, "Live TV");
        addDescription(page, "Öffne die TV-/Tuner-Funktion deines Fernsehers, um Live-Sender anzusehen.");
        LinearLayout actions = addHorizontalRow(page);
        View open = createActionTile("TV / Tuner öffnen", R.drawable.logo_live_tv,
                v -> LiveTvLauncher.open(this));
        open.setId(R.id.live_tv_open);
        actions.addView(open);
        View home = createActionTile("Zur Startseite", R.drawable.app_icon, v -> returnHome());
        home.setId(R.id.page_back_home);
        actions.addView(home);
    }

    private void addBackHome(LinearLayout page) {
        View home = createActionTile("Zur Startseite", R.drawable.app_icon, v -> returnHome());
        home.setId(R.id.page_back_home);
        addHorizontalRow(page).addView(home);
    }

    private void addDescription(LinearLayout page, String description) {
        TextView message = text(description, 16, false);
        message.setTextColor(Color.LTGRAY);
        message.setPadding(0, dp(12), 0, dp(24));
        page.addView(message);
    }

    private void buildTopBar() {
        LinearLayout top = new LinearLayout(this);
        configureFocusContainer(top);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

        TextView logo = text("TCL", 30, true);
        logo.setTextColor(Color.rgb(255, 54, 54));
        top.addView(logo, new LinearLayout.LayoutParams(dp(120), ViewGroup.LayoutParams.MATCH_PARENT));

        // Keep all six controls in one scrollable strip, even on narrower TVs.
        HorizontalScrollView navScroll = new HorizontalScrollView(this);
        configureFocusContainer(navScroll);
        navScroll.setHorizontalScrollBarEnabled(false);
        navScroll.setPadding(dp(4), dp(4), dp(4), dp(4));
        navScroll.setClipToPadding(false);
        LinearLayout nav = new LinearLayout(this);
        configureFocusContainer(nav);
        nav.setClipChildren(false);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams navLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        top.addView(navScroll, navLp);
        navScroll.addView(nav, new HorizontalScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        String[] labels = {"Startseite", "Apps", "Filme", "Serien", "Live TV"};
        int[] ids = {R.id.top_bar_home, R.id.top_bar_apps, R.id.top_bar_movies, R.id.top_bar_series, R.id.top_bar_live_tv};
        for (int i = 0; i < labels.length; i++) {
            String n = labels[i];
            TextView t = text(n, 17, false);
            t.setPadding(dp(16), 0, dp(16), 0);
            prepareTopBarItem(t, ids[i]);
            final Page targetPage = Page.values()[i];
            t.setOnClickListener(v -> showPage(targetPage));
            nav.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        TextView gear = text("⚙", 30, false);
        gear.setGravity(Gravity.CENTER);
        gear.setContentDescription("Einstellungen");
        prepareTopBarItem(gear, R.id.top_bar_settings);
        gear.setOnClickListener(v -> LauncherUtils.openSettings(this));
        nav.addView(gear, new LinearLayout.LayoutParams(dp(70), ViewGroup.LayoutParams.MATCH_PARENT));

        clock = text("", 16, false);
        clock.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        top.addView(clock, new LinearLayout.LayoutParams(dp(260), ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void prepareTopBarItem(View item, int id) {
        item.setId(id);
        item.setFocusable(true);
        item.setFocusableInTouchMode(true);
        applyFocus(item);
        topBarItems.add(item);
    }

    private void configureFocusContainer(ViewGroup container) {
        container.setFocusable(false);
        container.setFocusableInTouchMode(false);
        container.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
    }

    private void focusAndReveal(View target, int direction) {
        if (target == null || !target.requestFocus(direction)) return;
        // Explicitly reveal offscreen targets; ScrollView's arrow search can
        // otherwise scroll without giving the target focus.
        target.post(() -> {
            if (target.hasFocus()) target.requestRectangleOnScreen(
                    new Rect(0, 0, target.getWidth(), target.getHeight()), false);
        });
    }

    private View activeMenu() { return topBarItems.get(currentPage.ordinal()); }

    private void updateActiveMenu() {
        for (View item : topBarItems) {
            item.setSelected(item == activeMenu());
            updateFocusAppearance(item);
        }
    }

    private List<List<View>> contentRows() {
        List<List<View>> rows = new ArrayList<>();
        if (allAppsGrid != null && allAppsGrid.getChildCount() > 0) {
            for (int i = 0; i < allAppsGrid.getChildCount(); i += allAppsGrid.getColumnCount()) {
                List<View> row = new ArrayList<>();
                for (int j = i; j < Math.min(i + allAppsGrid.getColumnCount(), allAppsGrid.getChildCount()); j++) {
                    row.add(allAppsGrid.getChildAt(j));
                }
                rows.add(row);
            }
        }
        for (ViewGroup group : horizontalRows) {
            List<View> row = new ArrayList<>();
            for (int i = 0; i < group.getChildCount(); i++) {
                if (group.getChildAt(i).isFocusable()) row.add(group.getChildAt(i));
            }
            if (!row.isEmpty()) rows.add(row);
        }
        return rows;
    }

    private View firstContent() {
        List<List<View>> rows = contentRows();
        return rows.isEmpty() ? activeMenu() : rows.get(0).get(0);
    }

    private void connectPageFocus() {
        List<List<View>> rows = contentRows();
        for (List<View> row : rows) {
            for (View item : row) {
                if (item.getId() == View.NO_ID) item.setId(View.generateViewId());
                item.setFocusableInTouchMode(true);
            }
        }
        for (int i = 0; i < topBarItems.size(); i++) {
            View item = topBarItems.get(i);
            connectDirections(item, topBarItems.get(Math.max(0, i - 1)),
                    topBarItems.get(Math.min(topBarItems.size() - 1, i + 1)), item, firstContent());
        }
        for (int r = 0; r < rows.size(); r++) {
            List<View> row = rows.get(r);
            for (int c = 0; c < row.size(); c++) {
                View item = row.get(c);
                View up = r == 0 ? activeMenu() : rows.get(r - 1).get(Math.min(c, rows.get(r - 1).size() - 1));
                View down = r == rows.size() - 1 ? item : rows.get(r + 1).get(Math.min(c, rows.get(r + 1).size() - 1));
                connectDirections(item, row.get(Math.max(0, c - 1)),
                        row.get(Math.min(row.size() - 1, c + 1)), up, down);
            }
        }
    }

    private void connectDirections(View item, View left, View right, View up, View down) {
        item.setNextFocusLeftId(left.getId());
        item.setNextFocusRightId(right.getId());
        item.setNextFocusUpId(up.getId());
        item.setNextFocusDownId(down.getId());
        item.setOnKeyListener((v, keyCode, event) -> {
            View target;
            int direction;
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_LEFT: target = left; direction = View.FOCUS_LEFT; break;
                case KeyEvent.KEYCODE_DPAD_RIGHT: target = right; direction = View.FOCUS_RIGHT; break;
                case KeyEvent.KEYCODE_DPAD_UP: target = up; direction = View.FOCUS_UP; break;
                case KeyEvent.KEYCODE_DPAD_DOWN: target = down; direction = View.FOCUS_DOWN; break;
                default: return false;
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN) focusAndReveal(target, direction);
            return true;
        });
    }

    private void refreshHdmi() {
        if (hdmiRow == null) return;
        hdmiRow.removeAllViews();
        List<HdmiInput> inputs = hdmiRepository.list();
        if (inputs.isEmpty()) {
            TextView empty = text("Keine HDMI-Eingänge wurden über Android TV gemeldet.", 16, false);
            empty.setPadding(dp(22), dp(30), dp(22), dp(30));
            hdmiRow.addView(empty);
            return;
        }
        for (HdmiInput input : inputs) hdmiRow.addView(createHdmiTile(input));
    }

    private View createHdmiTile(HdmiInput input) {
        LinearLayout tile = card();
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(18), dp(18), dp(18), dp(14));

        TextView logo = text(input.brand.logoText, input.brand == DeviceBrand.HDMI ? 18 : 22, true);
        logo.setGravity(Gravity.CENTER);
        logo.setLetterSpacing(input.brand == DeviceBrand.HDMI ? 0.08f : 0.03f);
        logo.setAlpha(input.state == TvInputManager.INPUT_STATE_DISCONNECTED ? 0.35f : 1f);
        tile.addView(logo, new LinearLayout.LayoutParams(dp(170), dp(72)));

        TextView label = text(input.brand == DeviceBrand.HDMI ? input.label : input.brand.displayName, 15, true);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        tile.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));

        String stateText = input.state == TvInputManager.INPUT_STATE_CONNECTED ? "Verbunden"
                : input.state == TvInputManager.INPUT_STATE_CONNECTED_STANDBY ? "Standby" : "Nicht verbunden";
        TextView state = text(stateText, 12, false);
        state.setTextColor(Color.LTGRAY);
        state.setGravity(Gravity.CENTER);
        tile.addView(state, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(24)));

        tile.setOnClickListener(v -> LauncherUtils.openHdmi(this, input.id));
        tile.setOnLongClickListener(v -> { showBrandPicker(input); return true; });
        tile.setContentDescription(input.label + ", " + stateText + ". Lang drücken zum Logo ändern.");
        applyFocus(tile);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(230), dp(165));
        lp.setMargins(0, 0, dp(16), 0);
        tile.setLayoutParams(lp);
        return tile;
    }

    private void showBrandPicker(HdmiInput input) {
        List<DeviceBrand> brands = new ArrayList<>();
        for (DeviceBrand b : DeviceBrand.values()) brands.add(b);
        String[] names = new String[brands.size() + 1];
        names[0] = "Automatisch erkennen";
        for (int i = 0; i < brands.size(); i++) names[i + 1] = brands.get(i).displayName;
        new AlertDialog.Builder(this)
                .setTitle(input.label + " – Logo")
                .setItems(names, (d, which) -> {
                    if (which == 0) hdmiRepository.clearOverride(input.id);
                    else hdmiRepository.setOverride(input.id, brands.get(which - 1));
                })
                .show();
    }

    private View createAppTile(LaunchableApps.Entry entry) {
        LinearLayout tile = card();
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(12), dp(12), dp(12), dp(10));
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(entry.loadIcon(getPackageManager()));
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        tile.addView(icon, new LinearLayout.LayoutParams(dp(72), dp(72)));
        TextView name = text(entry.label, 14, true);
        name.setGravity(Gravity.CENTER);
        name.setSingleLine(true);
        tile.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(35)));
        tile.setContentDescription(entry.label);
        tile.setTag(entry.component.flattenToString());
        tile.setOnClickListener(v -> entry.launch(this));
        applyFocus(tile);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(180), dp(135));
        lp.setMargins(0, 0, dp(14), 0);
        tile.setLayoutParams(lp);
        return tile;
    }

    private void refreshApps() {
        if (appsRow == null) return;
        appsRow.removeAllViews();
        for (int i = 0; i < Math.min(12, installedApps.size()); i++) {
            appsRow.addView(createAppTile(installedApps.get(i)));
        }
        View allApps = createActionTile("Alle Apps", R.drawable.logo_playstore, v -> showPage(Page.APPS));
        allApps.setId(R.id.home_all_apps);
        appsRow.addView(allApps);
    }

    private LinearLayout addHorizontalRow(LinearLayout parent) {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        configureFocusContainer(hsv);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setClipToPadding(false);
        LinearLayout row = new LinearLayout(this);
        configureFocusContainer(row);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(2), dp(8), dp(2), dp(8));
        hsv.addView(row, new HorizontalScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        parent.addView(hsv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        horizontalRows.add(row);
        return row;
    }

    private View createActionTile(String title, int drawable, View.OnClickListener click) {
        LinearLayout tile = card();
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(18), dp(18), dp(18), dp(14));
        ImageView icon = new ImageView(this);
        icon.setImageResource(drawable);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        tile.addView(icon, new LinearLayout.LayoutParams(dp(95), dp(70)));
        TextView label = text(title, 14, true);
        label.setGravity(Gravity.CENTER);
        tile.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));
        tile.setOnClickListener(click);
        applyFocus(tile);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(205), dp(135));
        lp.setMargins(0, 0, dp(16), 0);
        tile.setLayoutParams(lp);
        return tile;
    }

    private LinearLayout card() {
        LinearLayout v = new LinearLayout(this);
        v.setFocusable(true);
        v.setClickable(true);
        v.setBackground(roundRect(TILE, 18));
        return v;
    }

    private void applyFocus(View v) {
        v.setOnFocusChangeListener((view, hasFocus) -> updateFocusAppearance(view));
        updateFocusAppearance(v);
    }

    private void updateFocusAppearance(View view) {
        boolean focused = view.hasFocus();
        boolean selected = view.isSelected();
        boolean menu = view instanceof TextView;
        float scale = !menu && focused ? 1.07f : 1f;
        view.animate().scaleX(scale).scaleY(scale).setDuration(120).start();
        int active = Color.rgb(29, 64, 87);
        GradientDrawable background = roundRect(focused ? TILE_FOCUS :
                (selected ? active : (menu ? Color.TRANSPARENT : TILE)), menu ? 10 : 18);
        if (focused || selected) background.setStroke(dp(2), focused ? Color.WHITE : Color.rgb(102, 209, 255));
        view.setBackground(background);
        view.setElevation(focused ? dp(10) : 0);
        if (menu) {
            TextView label = (TextView) view;
            label.setTextColor(selected && !focused ? Color.rgb(144, 220, 255) : Color.WHITE);
            label.setTypeface(android.graphics.Typeface.DEFAULT,
                    selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
    }

    private void addSection(LinearLayout parent, String s) {
        TextView t = text(s, 22, true);
        t.setPadding(0, 0, 0, dp(8));
        parent.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
    }

    private void addSpacer(LinearLayout parent, int sizeDp) {
        parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(sizeDp)));
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(sp);
        t.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return t;
    }

    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private GradientDrawable makeGradient() {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(5, 20, 34), Color.rgb(9, 14, 24), Color.rgb(36, 16, 18)});
        return d;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
