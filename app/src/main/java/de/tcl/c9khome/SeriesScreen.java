package de.tcl.c9khome;

import java.util.List;

/** Series hub with real episode-aware sections and an explicit empty state. */
public final class SeriesScreen {
    private SeriesScreen() {}

    public static ScreenView build(ScreenHost host) {
        ScreenView screen = new ScreenView(host.activity(), R.id.page_series);
        MoviesScreen.hero(host, screen, MediaItem.Kind.SERIES, "Deine Serienwelt",
                "Serien und neue Folgen deiner installierten Anbieter");
        MoviesScreen.providerFilters(host, screen, false);
        MoviesScreen.mediaRow(host, screen, "Weiter anschauen", MediaRepository.Section.CONTINUE,
                MediaItem.Kind.SERIES, "Noch keine Serien oder Folgen zum Weiterschauen verfügbar");
        MoviesScreen.mediaRow(host, screen, "Neue Folgen für dich", MediaRepository.Section.NEW_EPISODES,
                MediaItem.Kind.SERIES, "Noch keine bestätigten neuen Folgen verfügbar");
        MoviesScreen.mediaRow(host, screen, "Beliebte Serien", MediaRepository.Section.POPULAR_SERIES,
                MediaItem.Kind.SERIES, "Noch keine Serienempfehlungen aus einer echten Quelle verfügbar");
        MoviesScreen.mediaRow(host, screen, "Neue Serien / neue Staffeln", MediaRepository.Section.NEW_RELEASES,
                MediaItem.Kind.SERIES, "Noch keine Daten zu neuen Serien oder Staffeln verfügbar");
        MoviesScreen.mediaRow(host, screen, "Für dich empfohlen", MediaRepository.Section.PERSONAL,
                MediaItem.Kind.SERIES, "Für persönliche Serientipps ist noch keine Datenquelle verbunden");
        host.ui().genreRows(screen, host);
        List<MediaItem> saved = host.media().savedItems(MediaItem.Kind.SERIES);
        if (saved.isEmpty()) host.ui().emptyRow(screen, "Meine Serien / Meine Liste", "Noch keine Serien gespeichert");
        else host.ui().mediaRow(screen, "Meine Serien / Meine Liste", saved, host);
        return screen;
    }
}
