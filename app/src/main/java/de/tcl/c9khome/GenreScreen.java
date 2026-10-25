package de.tcl.c9khome;

import java.util.List;

/** A genre has its own internal view, backed only by actual media metadata. */
public final class GenreScreen {
    private GenreScreen() {}

    public static ScreenView build(ScreenHost host, String genre) {
        ScreenView screen = new ScreenView(host.activity(), R.id.page_genre);
        host.ui().hero(screen, genre, "Entdecke echte Inhalte deiner installierten Anbieter nach Genre");
        MoviesScreen.providerFilters(host, screen, false);
        addGenreRow(host, screen, "Filme", MediaRepository.Section.POPULAR_MOVIES, MediaItem.Kind.MOVIE, genre);
        addGenreRow(host, screen, "Serien", MediaRepository.Section.POPULAR_SERIES, MediaItem.Kind.SERIES, genre);
        addGenreRow(host, screen, "Weiter anschauen", MediaRepository.Section.CONTINUE, null, genre);
        return screen;
    }

    private static void addGenreRow(ScreenHost host, ScreenView screen, String title,
                                    MediaRepository.Section section, MediaItem.Kind kind, String genre) {
        List<MediaItem> items = host.media().get(section, kind, host.providerFilter(), genre);
        if (items.isEmpty()) host.ui().emptyRow(screen, title,
                "Noch keine verfügbaren Inhalte für „" + genre + "“ aus einer verbundenen Quelle");
        else host.ui().mediaRow(screen, title, items, host);
    }
}
