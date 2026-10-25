package de.tcl.c9khome;

/** Home composition. Favorites and HDMI keep their separate, proven controllers. */
public final class HomeScreen {
    private HomeScreen() {}

    public static ScreenView build(ScreenHost host) {
        ScreenView screen = new ScreenView(host.activity(), R.id.page_home);
        host.addFavorites(screen);
        MoviesScreen.mediaRow(host, screen, "Weiter anschauen", MediaRepository.Section.CONTINUE, null,
                "Noch keine Inhalte zum Weiterschauen verfügbar");
        MoviesScreen.mediaRow(host, screen, "Beliebte Filme", MediaRepository.Section.POPULAR_MOVIES,
                MediaItem.Kind.MOVIE, "Noch keine Filmquelle für deine installierten Anbieter verbunden");
        MoviesScreen.mediaRow(host, screen, "Beliebte Serien", MediaRepository.Section.POPULAR_SERIES,
                MediaItem.Kind.SERIES, "Noch keine Serienquelle für deine installierten Anbieter verbunden");
        MoviesScreen.mediaRow(host, screen, "Neuerscheinungen", MediaRepository.Section.NEW_RELEASES, null,
                "Neuerscheinungen erscheinen hier, sobald eine echte Inhaltsquelle verfügbar ist");
        MoviesScreen.mediaRow(host, screen, "Empfehlungen aus YouTube", MediaRepository.Section.YOUTUBE, null,
                "Persönliche YouTube-Empfehlungen sind hier noch nicht verfügbar");
        host.ui().genreRows(screen, host);
        host.addHdmi(screen);
        return screen;
    }
}
