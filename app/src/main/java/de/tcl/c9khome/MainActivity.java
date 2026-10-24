package de.tcl.c9khome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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
    private TextView clock;
    private HdmiRepository hdmiRepository;
    private final Handler handler = new Handler();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        hdmiRepository = new HdmiRepository(this);
        buildUi();
    }

    @Override protected void onStart() {
        super.onStart();
        hdmiRepository.start(this::refreshHdmi);
        refreshHdmi();
        refreshApps();
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

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(makeGradient());

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(48), dp(28), dp(48), dp(40));
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        buildTopBar();
        addSpacer(26);
        addSection("Meine Apps");
        appsRow = addHorizontalRow();
        addSpacer(26);
        addSection("HDMI & Eingänge");
        hdmiRow = addHorizontalRow();
        addSpacer(26);
        addSection("System");
        LinearLayout systemRow = addHorizontalRow();
        systemRow.addView(createActionTile("Google Play Store", R.drawable.logo_playstore, v -> LauncherUtils.openPlayStore(this)));
        systemRow.addView(createActionTile("Einstellungen", R.drawable.logo_settings, v -> LauncherUtils.openSettings(this)));
        systemRow.addView(createActionTile("TCL Eingänge", R.drawable.logo_hdmi, v -> {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            catch (Exception ignored) {}
        }));

        setContentView(scroll);
    }

    private void buildTopBar() {
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

        TextView logo = text("TCL", 30, true);
        logo.setTextColor(Color.rgb(255, 54, 54));
        top.addView(logo, new LinearLayout.LayoutParams(dp(120), ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams navLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        top.addView(nav, navLp);
        for (String n : new String[]{"Startseite", "Apps", "Filme", "Serien", "Live TV"}) {
            TextView t = text(n, 17, n.equals("Startseite"));
            t.setPadding(dp(16), 0, dp(16), 0);
            nav.addView(t);
        }

        clock = text("", 16, false);
        clock.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        top.addView(clock, new LinearLayout.LayoutParams(dp(260), ViewGroup.LayoutParams.MATCH_PARENT));

        TextView gear = text("⚙", 30, false);
        gear.setGravity(Gravity.CENTER);
        gear.setFocusable(true);
        gear.setOnClickListener(v -> LauncherUtils.openSettings(this));
        applyFocus(gear);
        top.addView(gear, new LinearLayout.LayoutParams(dp(70), ViewGroup.LayoutParams.MATCH_PARENT));
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

    private void refreshApps() {
        if (appsRow == null) return;
        appsRow.removeAllViews();
        PackageManager pm = getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN);
        q.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> infos = pm.queryIntentActivities(q, 0);
        int added = 0;
        for (ResolveInfo ri : infos) {
            if (ri.activityInfo == null || getPackageName().equals(ri.activityInfo.packageName)) continue;
            LinearLayout tile = card();
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER);
            tile.setPadding(dp(12), dp(12), dp(12), dp(10));

            ImageView icon = new ImageView(this);
            try { icon.setImageDrawable(ri.loadIcon(pm)); } catch (Exception ignored) {}
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            tile.addView(icon, new LinearLayout.LayoutParams(dp(72), dp(72)));
            TextView name = text(ri.loadLabel(pm).toString(), 14, true);
            name.setGravity(Gravity.CENTER);
            name.setSingleLine(true);
            tile.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(35)));

            String pkg = ri.activityInfo.packageName;
            tile.setOnClickListener(v -> {
                Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
                if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
                if (launch != null) startActivity(launch);
            });
            applyFocus(tile);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(180), dp(135));
            lp.setMargins(0, 0, dp(14), 0);
            tile.setLayoutParams(lp);
            appsRow.addView(tile);
            added++;
            if (added >= 12) break;
        }
        appsRow.addView(createActionTile("Alle Apps / Store", R.drawable.logo_playstore, v -> LauncherUtils.openPlayStore(this)));
    }

    private LinearLayout addHorizontalRow() {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setClipToPadding(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(2), dp(8), dp(2), dp(8));
        hsv.addView(row, new HorizontalScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(hsv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
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
        v.setOnFocusChangeListener((view, hasFocus) -> {
            view.animate().scaleX(hasFocus ? 1.07f : 1f).scaleY(hasFocus ? 1.07f : 1f).setDuration(120).start();
            if (view instanceof LinearLayout) view.setBackground(roundRect(hasFocus ? TILE_FOCUS : TILE, 18));
            view.setElevation(hasFocus ? dp(10) : 0);
        });
    }

    private void addSection(String s) {
        TextView t = text(s, 22, true);
        t.setPadding(0, 0, 0, dp(8));
        root.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
    }

    private void addSpacer(int dp) {
        View s = new View(this);
        root.addView(s, new LinearLayout.LayoutParams(1, dp(dp)));
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
