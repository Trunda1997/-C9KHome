package de.tcl.c9khome;

import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** The app catalogue is intentionally separate from the media/person search. */
public final class AppsScreen {
    private static final Map<android.app.Activity, State> STATES = new WeakHashMap<>();
    private static final class State {
        String query = "";
        AppStoreRepository.Category category = AppStoreRepository.Category.ALL;
    }
    private AppsScreen() {}

    public static ScreenView build(ScreenHost host) {
        ScreenView page = new ScreenView(host.activity(), R.id.page_apps);
        TvUi ui = host.ui();
        ui.heading(page.root, "Apps");
        ui.description(page.root, "Deine Apps und neue Entdeckungen für deinen Fernseher");
        AppStoreRepository store = new AppStoreRepository(host.activity());
        List<LaunchableApps.Entry> installed = new ArrayList<>(host.installedApps());
        State state = STATES.get(host.activity());
        if (state == null) {
            state = new State();
            STATES.put(host.activity(), state);
        }
        final State filters = state;
        GridLayout grid = new GridLayout(host.activity());
        grid.setId(R.id.all_apps_grid);
        grid.setFocusable(false);
        grid.setFocusableInTouchMode(false);
        grid.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        int availableDp = (int) (host.activity().getResources().getDisplayMetrics().widthPixels
                / host.activity().getResources().getDisplayMetrics().density) - 96;
        int columns = Math.max(1, Math.min(6, availableDp / 180));
        grid.setColumnCount(columns);
        android.widget.TextView results = new android.widget.TextView(host.activity());
        results.setTextColor(android.graphics.Color.LTGRAY);
        results.setTextSize(15);
        results.setPadding(0, dp(host, 8), 0, dp(host, 12));
        Runnable update = () -> {
            grid.removeAllViews();
            int index = 0;
            for (LaunchableApps.Entry entry : installed) {
                if (!store.matches(entry, filters.category, filters.query)) continue;
                View tile = host.appTile(entry, false);
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                        GridLayout.spec(index / columns), GridLayout.spec(index % columns, 1f));
                lp.width = 0;
                lp.height = dp(host, 156);
                lp.setMargins(dp(host, 6), dp(host, 8), dp(host, 6), dp(host, 8));
                grid.addView(tile, lp);
                index++;
            }
            results.setText(index == 0 ? "Keine passende installierte App gefunden. Suche im Google Play Store weiter."
                    : index + " installierte Apps" + (filters.category == AppStoreRepository.Category.ALL ? "" : " · " + filters.category.label));
            host.rebindFocus();
        };

        EditText input = ui.addSearch(page, R.id.apps_search_input, R.id.apps_search_submit,
                "Installierte Apps finden oder im Play Store suchen", text -> {
                    filters.query = text == null ? "" : text.trim();
                    update.run();
                });
        input.setText(filters.query);

        LinearLayout categories = page.row(ui, "App-Kategorien");
        categories.setId(R.id.apps_categories);
        for (AppStoreRepository.Category candidate : AppStoreRepository.Category.values()) {
            View chip = ui.button(candidate.label, () -> {
                filters.category = candidate;
                filters.query = input.getText().toString().trim();
                update.run();
            });
            categories.addView(chip);
        }
        // Search feedback stays next to the search controls instead of being
        // hidden below the discovery rows.
        page.root.addView(results);

        LinearLayout searchActions = page.row(ui, "Apps entdecken");
        searchActions.addView(ui.button("Installierte Apps anzeigen", () -> {
            if (grid.getChildCount() > 0) host.reveal(grid.getChildAt(0), View.FOCUS_DOWN);
        }));
        View playSearch = ui.button("Im Google Play Store suchen", () -> {
            String text = input.getText().toString().trim();
            if (text.isEmpty() && filters.category != AppStoreRepository.Category.ALL) text = filters.category.label;
            AppStoreRepository.openSearch(host.activity(), text);
        });
        playSearch.setId(R.id.store_search);
        searchActions.addView(playSearch);
        View reset = ui.button("Alle Apps anzeigen", () -> {
            input.setText("");
            filters.query = "";
            filters.category = AppStoreRepository.Category.ALL;
            update.run();
        });
        reset.setId(R.id.apps_clear_search);
        searchActions.addView(reset);

        ui.emptyRow(page, "Empfehlungen für dich", "Personalisierte Store-Empfehlungen sind noch nicht verbunden.");
        addDirectory(page, host, store, installed, "Filme & Serien", AppStoreRepository.Category.MOVIES);
        addDirectory(page, host, store, installed, "Unterhaltungs-Apps", AppStoreRepository.Category.ENTERTAINMENT);
        addDirectory(page, host, store, installed, "Musik", AppStoreRepository.Category.MUSIC);

        ui.heading(page.root, "Alle installierten Apps");
        page.root.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        page.registerGrid(grid);
        update.run();
        store.loadCategories(installed, () -> {
            if (page.root.isAttachedToWindow() && filters.category != AppStoreRepository.Category.ALL) update.run();
        });
        return page;
    }

    private static void addDirectory(ScreenView page, ScreenHost host, AppStoreRepository store,
            List<LaunchableApps.Entry> installed, String title, AppStoreRepository.Category category) {
        LinearLayout row = page.row(host.ui(), title);
        for (AppStoreRepository.StoreApp app : store.directory(category)) {
            LaunchableApps.Entry existing = app.installedEntry(installed);
            if (existing != null) {
                // Reuse the real app tile so long OK retains the V0.4 favorites menu.
                row.addView(host.appTile(existing, false));
            } else {
                row.addView(host.ui().button(app.label + "\nIm Play Store öffnen", () ->
                        AppStoreRepository.openDetails(host.activity(), app.packageName)));
            }
        }
    }

    private static int dp(ScreenHost host, int value) {
        return Math.round(value * host.activity().getResources().getDisplayMetrics().density);
    }
}
