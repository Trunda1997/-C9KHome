package de.tcl.c9khome;

import android.app.AlertDialog;
import android.graphics.Color;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Collections;
import java.util.List;

/** Keeps the V0.4 favorite format and remote-control editing independent of screens. */
public final class FavoritesController {
    private final ScreenHost host;
    private LinearLayout homeRow;
    private TextView hint;
    private List<String> moveOrder;
    private String movingPackage;
    private View movingTile;

    public FavoritesController(ScreenHost host) { this.host = host; }

    public void addTo(ScreenView screen) {
        // A screen rebuild must never commit an unfinished drag operation.
        clearMoveState();
        homeRow = screen.row(host.ui(), "Meine Apps");
        homeRow.setId(R.id.apps_row);
        hint = host.ui().text("", 13, false);
        hint.setId(R.id.favorites_sort_hint);
        hint.setTextColor(Color.rgb(168, 181, 201));
        hint.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        hint.setPadding(host.ui().dp(4), host.ui().dp(4), 0, host.ui().dp(12));
        screen.root.addView(hint);
        populateHomeRow();
    }

    public View appTile(LaunchableApps.Entry entry, boolean homeFavorite) {
        TvUi ui = host.ui();
        LinearLayout tile = ui.card();
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(12));
        tile.setFocusable(true);
        tile.setFocusableInTouchMode(true);
        tile.setClickable(true);

        ImageView icon = new ImageView(host.activity());
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        tile.addView(icon, new LinearLayout.LayoutParams(ui.dp(68), ui.dp(68)));
        ui.loadAppIcon(entry, icon);

        TextView name = ui.text(entry.label, 15, true);
        name.setGravity(Gravity.CENTER);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tile.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(38)));
        if (!homeFavorite) {
            TextView open = ui.text("Öffnen", 12, false);
            open.setTextColor(TvUi.MUTED);
            open.setGravity(Gravity.CENTER);
            tile.addView(open, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(18)));
        }
        tile.setContentDescription(entry.label);
        tile.setTag(entry.packageName);
        tile.setOnClickListener(v -> entry.launch(host.activity()));
        tile.setOnLongClickListener(v -> {
            showMenu(entry, tile, homeFavorite);
            return true;
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ui.dp(182), ui.dp(152));
        lp.setMargins(ui.dp(6), ui.dp(9), ui.dp(14), ui.dp(9));
        tile.setLayoutParams(lp);
        ui.styleFocus(tile);
        return tile;
    }

    private void populateHomeRow() {
        if (homeRow == null) return;
        homeRow.removeAllViews();
        List<String> packages = moveOrder == null ? host.favorites().getPackages() : moveOrder;
        for (String packageName : packages) {
            for (LaunchableApps.Entry entry : host.installedApps()) {
                if (packageName.equals(entry.packageName)) {
                    homeRow.addView(appTile(entry, true));
                    break;
                }
            }
        }
        View allApps = host.ui().button("Alle Apps", () -> host.navigate(ScreenId.APPS));
        allApps.setId(R.id.home_all_apps);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(host.ui().dp(182), host.ui().dp(152));
        lp.setMargins(host.ui().dp(6), host.ui().dp(9), host.ui().dp(14), host.ui().dp(9));
        homeRow.addView(allApps, lp);
        updateHint();
    }

    private void updateHint() {
        if (hint == null || homeRow == null) return;
        hint.setText(isMoving()
                ? "Verschieben: ◀ / ▶ · OK speichern · Zurück abbrechen"
                : homeRow.getChildCount() == 1
                        ? "Füge über Apps mit langem OK deine Favoriten hinzu."
                        : "Langes OK: Favoriten verschieben oder entfernen");
    }

    private void showMenu(LaunchableApps.Entry entry, View tile, boolean homeFavorite) {
        if (isMoving()) return;
        if (homeFavorite) {
            new AlertDialog.Builder(host.activity()).setTitle(entry.label)
                    .setItems(new String[]{"Verschieben", "Von Startseite entfernen"}, (dialog, which) -> {
                        if (which == 0) beginMove(entry.packageName, tile);
                        else {
                            int index = homeRow == null ? 0 : homeRow.indexOfChild(tile);
                            host.favorites().remove(entry.packageName);
                            refreshHomeRow(null, index);
                        }
                    }).show();
        } else {
            boolean exists = host.favorites().contains(entry.packageName);
            new AlertDialog.Builder(host.activity()).setTitle(entry.label)
                    .setItems(new String[]{exists ? "Von Startseite entfernen" : "Zur Startseite hinzufügen"},
                            (dialog, which) -> {
                                if (exists) host.favorites().remove(entry.packageName);
                                else host.favorites().add(entry.packageName);
                            }).show();
        }
    }

    private void refreshHomeRow(String preferredPackage, int fallbackIndex) {
        if (homeRow == null) return;
        populateHomeRow();
        host.rebindFocus();
        View target = null;
        if (preferredPackage != null) {
            for (int i = 0; i < homeRow.getChildCount(); i++) {
                View item = homeRow.getChildAt(i);
                if (preferredPackage.equals(item.getTag())) target = item;
            }
        }
        if (target == null && homeRow.getChildCount() > 0) {
            target = homeRow.getChildAt(Math.max(0, Math.min(fallbackIndex, homeRow.getChildCount() - 1)));
        }
        if (target != null && homeRow.isShown()) host.reveal(target, View.FOCUS_DOWN);
    }

    private void beginMove(String packageName, View tile) {
        if (homeRow == null || homeRow.indexOfChild(tile) < 0) return;
        moveOrder = host.favorites().getPackages();
        if (!moveOrder.contains(packageName)) {
            clearMoveState();
            return;
        }
        movingPackage = packageName;
        movingTile = tile;
        movingTile.setActivated(true);
        host.ui().styleFocus(movingTile);
        updateHint();
        host.reveal(movingTile, View.FOCUS_DOWN);
    }

    /** Called before the shared focus navigator consumes directional keys. */
    public boolean handleKey(View view, int keyCode, KeyEvent event) {
        if (!isMoving() || view != movingTile) return false;
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                int from = homeRow.indexOfChild(movingTile);
                int to = from + (keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
                if (from >= 0 && to >= 0 && to < homeRow.getChildCount() - 1) {
                    Object neighbor = homeRow.getChildAt(to).getTag();
                    int movingIndex = moveOrder.indexOf(movingPackage);
                    int neighborIndex = moveOrder.indexOf(neighbor);
                    if (movingIndex >= 0 && neighborIndex >= 0) {
                        Collections.swap(moveOrder, movingIndex, neighborIndex);
                        homeRow.removeView(movingTile);
                        homeRow.addView(movingTile, to);
                        host.rebindFocus();
                        host.reveal(movingTile, to < from ? View.FOCUS_LEFT : View.FOCUS_RIGHT);
                    }
                }
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (event.getAction() == KeyEvent.ACTION_UP) finishMove(true);
            return true;
        }
        return keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN;
    }

    public boolean isMoving() { return moveOrder != null && movingTile != null; }

    public boolean handleBack() {
        if (!isMoving()) return false;
        finishMove(false);
        return true;
    }

    public void stop() {
        if (isMoving()) finishMove(false);
        // Navigation rebuilds the Home screen. Do not retain an arbitrarily
        // long favorite row while browsing other areas of the application.
        homeRow = null;
        hint = null;
    }

    private void finishMove(boolean save) {
        String packageName = movingPackage;
        if (save && moveOrder != null) host.favorites().setPackages(moveOrder);
        clearMoveState();
        refreshHomeRow(packageName, 0);
    }

    private void clearMoveState() {
        if (movingTile != null) {
            movingTile.setActivated(false);
            host.ui().styleFocus(movingTile);
        }
        moveOrder = null;
        movingPackage = null;
        movingTile = null;
    }
}
