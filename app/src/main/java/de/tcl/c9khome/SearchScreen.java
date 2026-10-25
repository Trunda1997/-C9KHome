package de.tcl.c9khome;

import android.widget.LinearLayout;
import java.util.List;

/** Media/person search is intentionally independent of the installed-app catalog. */
public final class SearchScreen {
    private SearchScreen() {}

    public static ScreenView build(ScreenHost host) {
        ScreenView screen = new ScreenView(host.activity(), R.id.page_search);
        host.ui().heading(screen.root, "Was möchtest du entdecken?");
        final LinearLayout[] resultRow = new LinearLayout[1];
        host.ui().addSearch(screen, R.id.media_search_input, R.id.media_search_submit,
                "Nach Filmen, Serien, Sendungen, Schauspielern und mehr suchen", query -> {
                    LinearLayout results = resultRow[0];
                    if (results == null) return;
                    results.removeAllViews();
                    String value = query == null ? "" : query.trim();
                    if (value.isEmpty()) {
                        host.ui().description(results, "Gib einen Film, eine Serie, eine Sendung oder eine Person ein.");
                    } else {
                        List<MediaItem> matches = host.media().search(value);
                        if (matches.isEmpty()) host.ui().description(results,
                                "Keine Ergebnisse in den verfügbaren Mediendaten. Weitere Datenquellen können später verbunden werden.");
                        else for (MediaItem item : matches) {
                            results.addView(host.ui().poster(item, () -> host.openMedia(item)));
                        }
                    }
                    host.rebindFocus();
                });
        host.ui().genreRows(screen, host);
        resultRow[0] = screen.row(host.ui(), "Suchergebnisse");
        resultRow[0].setId(R.id.media_search_results);
        host.ui().description(resultRow[0], "Gib einen Film, eine Serie, eine Sendung oder eine Person ein.");
        return screen;
    }
}
