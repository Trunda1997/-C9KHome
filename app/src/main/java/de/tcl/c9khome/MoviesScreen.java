package de.tcl.c9khome;

import android.view.View;
import android.widget.LinearLayout;
import java.util.List;

/** Film hub. A row only contains data supplied for that actual section. */
public final class MoviesScreen {
    private MoviesScreen() {}

    public static ScreenView build(ScreenHost host) {
        ScreenView screen = new ScreenView(host.activity(), R.id.page_movies);
        hero(host, screen, MediaItem.Kind.MOVIE, "Deine Filmzentrale",
                "Filme deiner installierten Anbieter – sobald eine echte Inhaltsquelle verbunden ist");
        providerFilters(host, screen, false);
        mediaRow(host, screen, "Weiter anschauen", MediaRepository.Section.CONTINUE, MediaItem.Kind.MOVIE,
                "Noch keine Filme zum Weiterschauen verfügbar");
        mediaRow(host, screen, "Beliebt bei dir", MediaRepository.Section.PERSONAL, MediaItem.Kind.MOVIE,
                "Für persönliche Filmtipps ist noch keine Datenquelle verbunden");
        mediaRow(host, screen, "Beliebte Filme", MediaRepository.Section.POPULAR_MOVIES, MediaItem.Kind.MOVIE,
                "Noch keine aktuellen Filmempfehlungen verfügbar");
        mediaRow(host, screen, "Neu erschienen", MediaRepository.Section.NEW_RELEASES, MediaItem.Kind.MOVIE,
                "Noch keine Daten zu neuen Filmen verfügbar");
        mediaRow(host, screen, "Demnächst verfügbar", MediaRepository.Section.UPCOMING, MediaItem.Kind.MOVIE,
                "Noch keine bestätigten kommenden Filme verfügbar");
        host.ui().genreRows(screen, host);
        mediaRow(host, screen, "Für dich empfohlen", MediaRepository.Section.PERSONAL, MediaItem.Kind.MOVIE,
                "Persönliche Empfehlungen erscheinen erst mit einer verbundenen Quelle");
        List<MediaItem> saved = host.media().savedItems(MediaItem.Kind.MOVIE);
        if (saved.isEmpty()) host.ui().emptyRow(screen, "Meine Liste", "Noch keine Filme gespeichert");
        else host.ui().mediaRow(screen, "Meine Liste", saved, host);
        return screen;
    }

    static void mediaRow(ScreenHost host, ScreenView screen, String title, MediaRepository.Section section,
                         MediaItem.Kind kind, String emptyMessage) {
        List<MediaItem> items = host.media().get(section, kind, host.providerFilter(), null);
        if (items.isEmpty()) host.ui().emptyRow(screen, title, emptyMessage);
        else host.ui().mediaRow(screen, title, items, host);
    }

    static void hero(ScreenHost host, ScreenView screen, MediaItem.Kind kind, String title, String emptyMessage) {
        MediaRepository.Section section = kind == MediaItem.Kind.MOVIE
                ? MediaRepository.Section.POPULAR_MOVIES : MediaRepository.Section.POPULAR_SERIES;
        List<MediaItem> highlights = host.media().get(section, kind, host.providerFilter(), null);
        if (highlights.isEmpty()) {
            host.ui().hero(screen, title, emptyMessage);
        } else {
            MediaItem item = highlights.get(0);
            host.ui().hero(screen, item.title, item.description == null ? "" : item.description);
            LinearLayout action = screen.row(host.ui(), "Highlight");
            action.addView(host.ui().button("Details ansehen", () -> host.openMedia(item)));
        }
    }

    static void providerFilters(ScreenHost host, ScreenView screen, boolean liveOnly) {
        boolean filterAvailable = host.providerFilter() == null;
        for (EntertainmentAppRepository.Provider provider : host.providers()) {
            if ((!liveOnly || provider.liveTv) && provider.packageName.equals(host.providerFilter())) {
                filterAvailable = true;
            }
        }
        if (!filterAvailable) host.setProviderFilter(null);
        LinearLayout filters = screen.row(host.ui(), "Anbieter");
        View all = host.ui().button(host.providerFilter() == null ? "✓ Alle" : "Alle", () -> {
            host.setProviderFilter(null);
            host.refreshScreen();
        });
        all.setSelected(host.providerFilter() == null);
        filters.addView(all);
        for (EntertainmentAppRepository.Provider provider : host.providers()) {
            if (liveOnly && !provider.liveTv) continue;
            boolean selected = provider.packageName.equals(host.providerFilter());
            View button = host.ui().button((selected ? "✓ " : "") + provider.name, () -> {
                host.setProviderFilter(provider.packageName);
                host.refreshScreen();
            });
            button.setSelected(selected);
            button.setTag("provider:" + provider.packageName);
            filters.addView(button);
        }
    }
}
