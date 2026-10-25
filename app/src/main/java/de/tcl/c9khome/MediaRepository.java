package de.tcl.c9khome;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Looper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.json.JSONArray;
import org.json.JSONException;

/** Small, source-backed cache. Reading cards/search never performs provider or network I/O. */
public final class MediaRepository {
    public enum Section { CONTINUE, POPULAR_MOVIES, POPULAR_SERIES, NEW_RELEASES, YOUTUBE, PERSONAL, UPCOMING, NEW_EPISODES, WATCHLIST }
    /** Future account/API adapters can provide genuine section-specific records on the worker. */
    public interface MediaSource {
        Map<Section, List<MediaItem>> load(Context context,
                List<EntertainmentAppRepository.Provider> installedProviders) throws Exception;
    }
    private static final int MAX_ITEMS = 120;
    private final Context context;
    private final SharedPreferences preferences;
    private final List<MediaSource> sources = new CopyOnWriteArrayList<>();
    private volatile Map<Section, List<MediaItem>> cache = emptySections();
    private volatile Set<String> installedPackages = Collections.emptySet();
    private final Object cacheLock = new Object();
    private long invalidation;

    public MediaRepository(Context context) {
        this.context = context.getApplicationContext();
        preferences = this.context.getSharedPreferences("media_saved_items", Context.MODE_PRIVATE);
        sources.add(new WatchNextSource());
    }
    public void addSource(MediaSource source) { if (source != null) sources.add(source); }
    public void removeSource(MediaSource source) { sources.remove(source); }

    /** Called by the coordinator's worker after provider discovery; no UI-thread query is allowed. */
    public void refresh(List<EntertainmentAppRepository.Provider> providers) {
        requireWorker();
        final long generation;
        synchronized (cacheLock) { generation = invalidation; }
        List<EntertainmentAppRepository.Provider> providerSnapshot = new ArrayList<>(providers);
        Set<String> packages = new HashSet<>();
        for (EntertainmentAppRepository.Provider provider : providerSnapshot) packages.add(provider.packageName);
        Map<Section, List<MediaItem>> next = emptySections();
        Set<String> admitted = new LinkedHashSet<>();
        for (MediaSource source : sources) {
            try {
                Map<Section, List<MediaItem>> loaded = source.load(context,
                        Collections.unmodifiableList(providerSnapshot));
                if (loaded == null) continue;
                for (Section section : Section.values()) {
                    List<MediaItem> values = loaded.get(section);
                    if (values == null) continue;
                    Set<String> sectionKeys = new HashSet<>();
                    for (MediaItem existing : next.get(section)) sectionKeys.add(existing.stableKey());
                    for (MediaItem item : values) {
                        if (!valid(item, packages) || sectionKeys.contains(item.stableKey())) continue;
                        if (!admitted.contains(item.stableKey()) && admitted.size() >= MAX_ITEMS) continue;
                        admitted.add(item.stableKey()); sectionKeys.add(item.stableKey());
                        next.get(section).add(item);
                    }
                }
            } catch (Exception ignored) {
                // An unavailable permission, account or provider leaves an honest empty section.
            }
        }
        Map<Section, List<MediaItem>> immutable = new EnumMap<>(Section.class);
        for (Section section : Section.values()) immutable.put(section,
                Collections.unmodifiableList(new ArrayList<>(next.get(section))));
        synchronized (cacheLock) {
            if (generation != invalidation) return;
            installedPackages = Collections.unmodifiableSet(packages);
            cache = Collections.unmodifiableMap(immutable);
        }
        pruneSavedPackages(packages);
    }

    public List<MediaItem> get(Section section, MediaItem.Kind kind, String providerPackage, String genre) {
        List<MediaItem> result = new ArrayList<>();
        List<MediaItem> values = cache.get(section);
        if (values == null) return result;
        for (MediaItem item : values) {
            if (!installedPackages.contains(item.providerPackage) || !matchesKind(item, kind)) continue;
            if (providerPackage != null && !providerPackage.isEmpty()
                    && !providerPackage.equals(item.providerPackage)) continue;
            if (genre != null && !genre.isEmpty() && !matchesGenre(item, genre)) continue;
            result.add(item);
        }
        return result;
    }

    /** Media/person search only. App labels and installed-package names are never search records. */
    public List<MediaItem> search(String query) {
        String term = normalize(query).trim();
        if (term.isEmpty()) return Collections.emptyList();
        Map<String, MediaItem> found = new LinkedHashMap<>();
        for (List<MediaItem> values : cache.values()) {
            for (MediaItem item : values) {
                if (!installedPackages.contains(item.providerPackage)) continue;
                String searchable = item.title + " " + item.description + " " + item.personRole;
                if (normalize(searchable).contains(term)) found.put(item.stableKey(), item);
            }
        }
        return new ArrayList<>(found.values());
    }

    public List<MediaItem> savedItems(MediaItem.Kind kind) {
        Set<String> saved = savedKeys();
        Map<String, MediaItem> result = new LinkedHashMap<>();
        for (List<MediaItem> values : cache.values()) for (MediaItem item : values) {
            if (installedPackages.contains(item.providerPackage) && matchesKind(item, kind)
                    && saved.contains(item.stableKey())) result.put(item.stableKey(), item);
        }
        return new ArrayList<>(result.values());
    }
    public boolean isSaved(MediaItem item) { return item != null && savedKeys().contains(item.stableKey()); }
    public synchronized void toggleSaved(MediaItem item) {
        if (item == null || item.id.isEmpty() || !installedPackages.contains(item.providerPackage)) return;
        Set<String> saved = savedKeys();
        if (!saved.remove(item.stableKey())) saved.add(item.stableKey());
        preferences.edit().putString("ids", new JSONArray(saved).toString()).apply();
    }

    /** Can be called immediately on PACKAGE_REMOVED, before the next background refresh. */
    public synchronized void purgePackage(String packageName) {
        Set<String> packages;
        synchronized (cacheLock) {
            invalidation++;
            packages = new HashSet<>(installedPackages); packages.remove(packageName);
            Map<Section, List<MediaItem>> next = emptySections();
            for (Section section : Section.values()) for (MediaItem item : cache.get(section)) {
                if (!packageName.equals(item.providerPackage)) next.get(section).add(item);
            }
            installedPackages = Collections.unmodifiableSet(packages);
            cache = next;
        }
        pruneSavedPackages(packages);
    }

    private synchronized void pruneSavedPackages(Set<String> packages) {
        Set<String> saved = savedKeys();
        boolean changed = saved.removeIf(key -> {
            int split = key.indexOf('\u001f');
            return split <= 0 || !packages.contains(key.substring(0, split));
        });
        if (changed) preferences.edit().putString("ids", new JSONArray(saved).toString()).apply();
    }
    private Set<String> savedKeys() {
        Set<String> saved = new LinkedHashSet<>();
        try {
            JSONArray array = new JSONArray(preferences.getString("ids", "[]"));
            for (int i = 0; i < array.length(); i++) {
                String key = array.optString(i, ""); if (!key.isEmpty()) saved.add(key);
            }
        } catch (JSONException ignored) {}
        return saved;
    }
    private static boolean valid(MediaItem item, Set<String> packages) {
        return item != null && !item.id.isEmpty() && !item.title.isEmpty()
                && packages.contains(item.providerPackage);
    }
    private static boolean matchesKind(MediaItem item, MediaItem.Kind kind) {
        return kind == null || item.kind == kind
                || (kind == MediaItem.Kind.SERIES && item.kind == MediaItem.Kind.EPISODE);
    }
    private static boolean matchesGenre(MediaItem item, String genre) {
        String target = normalize(genre).replace("science-fiction", "sci-fi");
        for (String value : item.genres) {
            String actual = normalize(value).replace('_', ' ').replace("science fiction", "sci-fi");
            if (actual.equals(target) || actual.contains(target)) return true;
            if (target.equals("dokumentation") && actual.contains("documentary")) return true;
            if (target.equals("komödie") || target.equals("comedy")) if (actual.contains("comedy")) return true;
            if (target.equals("familie") && (actual.contains("family") || actual.contains("kids"))) return true;
            if (target.equals("abenteuer") && actual.contains("adventure")) return true;
            if (target.equals("romantik") && actual.contains("romance")) return true;
        }
        return false;
    }
    private static String normalize(String value) { return value == null ? "" : value.toLowerCase(Locale.GERMANY); }
    private static Map<Section, List<MediaItem>> emptySections() {
        Map<Section, List<MediaItem>> values = new EnumMap<>(Section.class);
        for (Section section : Section.values()) values.put(section, new ArrayList<>());
        return values;
    }
    private static void requireWorker() {
        if (Looper.myLooper() == Looper.getMainLooper()) throw new IllegalStateException("Media discovery requires a worker thread");
    }

    /** Watch Next contributes progress/new-episode data only, never invented popularity or releases. */
    private static final class WatchNextSource implements MediaSource {
        @Override public Map<Section, List<MediaItem>> load(Context context,
                List<EntertainmentAppRepository.Provider> providers) {
            Map<Section, List<MediaItem>> result = emptySections();
            try (Cursor cursor = context.getContentResolver().query(
                    TvContract.WatchNextPrograms.CONTENT_URI, null, null, null, null)) {
                if (cursor == null) return result;
                int count = 0;
                while (count++ < MAX_ITEMS && cursor.moveToNext()) {
                    String title = text(cursor, "title");
                    String pkg = text(cursor, "package_name");
                    if (title.isEmpty() || pkg.isEmpty()) continue;
                    String id = text(cursor, "internal_provider_id");
                    if (id.isEmpty()) id = "watch-next:" + text(cursor, "_id");
                    long type = number(cursor, "type", -1);
                    MediaItem.Kind kind = type == TvContract.WatchNextPrograms.TYPE_MOVIE ? MediaItem.Kind.MOVIE
                            : type == TvContract.WatchNextPrograms.TYPE_TV_SERIES
                            || type == TvContract.WatchNextPrograms.TYPE_TV_SEASON ? MediaItem.Kind.SERIES
                            : type == TvContract.WatchNextPrograms.TYPE_TV_EPISODE ? MediaItem.Kind.EPISODE : MediaItem.Kind.SHOW;
                    long duration = number(cursor, "duration_millis", 0);
                    long position = number(cursor, "last_playback_position_millis", 0);
                    String release = text(cursor, "release_date");
                    int year = 0;
                    if (release.matches("^[0-9]{4}.*")) {
                        try { year = Integer.parseInt(release.substring(0, 4)); } catch (NumberFormatException ignored) {}
                    }
                    String genre = text(cursor, "genre");
                    if (genre.isEmpty()) genre = text(cursor, "canonical_genre");
                    List<String> genres = new ArrayList<>();
                    if (!genre.isEmpty()) genres.addAll(Arrays.asList(TvContract.Programs.Genres.decode(genre)));
                    MediaItem item = new MediaItem.Builder(id, title, kind, pkg)
                            .description(text(cursor, "long_description").isEmpty()
                                    ? text(cursor, "short_description") : text(cursor, "long_description"))
                            .posterUri(uri(text(cursor, "poster_art_uri")))
                            .backgroundUri(uri(text(cursor, "thumbnail_uri")))
                            .deepLinkUri(uri(text(cursor, "intent_uri")))
                            .rating(text(cursor, "content_rating")).genres(genres).year(year)
                            .durationMinutes((int) Math.min(Integer.MAX_VALUE, duration / 60000))
                            .progress(duration > 0 ? (float) position / duration : 0f)
                            .seasonNumber((int) number(cursor, "season_display_number", 0))
                            .episodeNumber((int) number(cursor, "episode_display_number", 0)).build();
                    long watchType = number(cursor, "watch_next_type", -1);
                    if (watchType == TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE
                            || watchType == TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_NEXT) result.get(Section.CONTINUE).add(item);
                    else if (watchType == TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_NEW
                            && kind == MediaItem.Kind.EPISODE) result.get(Section.NEW_EPISODES).add(item);
                    else if (watchType == TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_WATCHLIST) {
                        // A watchlist is genuine search data, not a popularity
                        // or personalized recommendation signal.
                        result.get(Section.WATCHLIST).add(item);
                    }
                }
            } catch (RuntimeException ignored) {
                // Firmware/account policy may intentionally restrict these rows to the system launcher.
            }
            return result;
        }
    }
    private static String text(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? "" : cursor.getString(index);
    }
    private static long number(Cursor cursor, String column, long fallback) {
        try { String value = text(cursor, column); return value.isEmpty() ? fallback : Long.parseLong(value); }
        catch (RuntimeException ignored) { return fallback; }
    }
    private static Uri uri(String value) { return value.isEmpty() ? null : Uri.parse(value); }
}
