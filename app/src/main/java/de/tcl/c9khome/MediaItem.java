package de.tcl.c9khome;

import android.net.Uri;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A sourced media/person record. Empty fields mean unavailable, never invented data. */
public final class MediaItem {
    public enum Kind { MOVIE, SERIES, SHOW, EPISODE, PERSON }
    public final String id, title, description, providerPackage, rating, personRole;
    public final Kind kind;
    public final Uri posterUri, backgroundUri, deepLinkUri, trailerUri;
    public final int year, durationMinutes, seasonNumber, episodeNumber;
    public final float progress;
    public final List<String> genres;
    public final List<MediaItem> episodes;
    public final MediaItem nextEpisode;

    private MediaItem(Builder b) {
        id = clean(b.id); title = clean(b.title); kind = b.kind == null ? Kind.SHOW : b.kind;
        providerPackage = clean(b.providerPackage); description = clean(b.description);
        rating = clean(b.rating); personRole = clean(b.personRole);
        posterUri = b.posterUri; backgroundUri = b.backgroundUri;
        deepLinkUri = b.deepLinkUri; trailerUri = b.trailerUri;
        year = Math.max(0, b.year); durationMinutes = Math.max(0, b.durationMinutes);
        seasonNumber = Math.max(0, b.seasonNumber); episodeNumber = Math.max(0, b.episodeNumber);
        progress = Float.isNaN(b.progress) ? 0f : Math.max(0f, Math.min(1f, b.progress));
        genres = Collections.unmodifiableList(new ArrayList<>(b.genres.subList(0, Math.min(24, b.genres.size()))));
        episodes = Collections.unmodifiableList(new ArrayList<>(b.episodes.subList(0, Math.min(100, b.episodes.size()))));
        nextEpisode = b.nextEpisode;
    }

    public String stableKey() { return providerPackage + "\u001f" + id; }
    private static String clean(String value) { return value == null ? "" : value; }

    public static final class Builder {
        private final String id, title, providerPackage;
        private final Kind kind;
        private String description = "", rating = "", personRole = "";
        private Uri posterUri, backgroundUri, deepLinkUri, trailerUri;
        private int year, durationMinutes, seasonNumber, episodeNumber;
        private float progress;
        private List<String> genres = Collections.emptyList();
        private List<MediaItem> episodes = Collections.emptyList();
        private MediaItem nextEpisode;
        public Builder(String id, String title, Kind kind, String providerPackage) {
            this.id = id; this.title = title; this.kind = kind; this.providerPackage = providerPackage;
        }
        public Builder description(String value) { description = value; return this; }
        public Builder rating(String value) { rating = value; return this; }
        public Builder personRole(String value) { personRole = value; return this; }
        public Builder posterUri(Uri value) { posterUri = value; return this; }
        public Builder backgroundUri(Uri value) { backgroundUri = value; return this; }
        public Builder deepLinkUri(Uri value) { deepLinkUri = value; return this; }
        public Builder trailerUri(Uri value) { trailerUri = value; return this; }
        public Builder year(int value) { year = value; return this; }
        public Builder durationMinutes(int value) { durationMinutes = value; return this; }
        public Builder seasonNumber(int value) { seasonNumber = value; return this; }
        public Builder episodeNumber(int value) { episodeNumber = value; return this; }
        public Builder progress(float value) { progress = value; return this; }
        public Builder genres(List<String> value) { genres = value == null ? Collections.emptyList() : value; return this; }
        public Builder episodes(List<MediaItem> value) { episodes = value == null ? Collections.emptyList() : value; return this; }
        public Builder nextEpisode(MediaItem value) { nextEpisode = value; return this; }
        public MediaItem build() { return new MediaItem(this); }
    }
}
