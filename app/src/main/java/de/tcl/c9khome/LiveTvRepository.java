package de.tcl.c9khome;

import android.content.Context;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Looper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/** Permission-aware live data adapter, ready for future EPG sources without synthetic schedules. */
public final class LiveTvRepository {
    public static final class Program {
        public final String id, channelName, title, providerPackage;
        public final Uri artworkUri, deepLinkUri;
        public final long startMillis, endMillis;
        public Program(String id, String channelName, String title, String providerPackage,
                Uri artworkUri, Uri deepLinkUri, long startMillis, long endMillis) {
            this.id = id; this.channelName = channelName; this.title = title;
            this.providerPackage = providerPackage; this.artworkUri = artworkUri;
            this.deepLinkUri = deepLinkUri; this.startMillis = startMillis; this.endMillis = endMillis;
        }
        public float progress() {
            if (endMillis <= startMillis) return 0f;
            return Math.max(0f, Math.min(1f, (float) (System.currentTimeMillis() - startMillis) / (endMillis - startMillis)));
        }
    }
    public interface LiveSource {
        List<Program> load(Context context, List<EntertainmentAppRepository.Provider> installedProviders) throws Exception;
    }
    private static final int MAX_PROGRAMS = 100;
    private final Context context;
    private final List<LiveSource> sources = new CopyOnWriteArrayList<>();
    private volatile List<Program> cache = Collections.emptyList();
    private final Object cacheLock = new Object();
    private long invalidation;

    public LiveTvRepository(Context context) {
        this.context = context.getApplicationContext();
        sources.add(new PlatformSource());
    }
    public void addSource(LiveSource source) { if (source != null) sources.add(source); }
    public void removeSource(LiveSource source) { sources.remove(source); }

    public void refresh(List<EntertainmentAppRepository.Provider> providers) {
        if (Looper.myLooper() == Looper.getMainLooper()) throw new IllegalStateException("Live TV discovery requires a worker thread");
        final long generation;
        synchronized (cacheLock) { generation = invalidation; }
        List<EntertainmentAppRepository.Provider> providerSnapshot = new ArrayList<>(providers);
        Set<String> packages = new HashSet<>();
        for (EntertainmentAppRepository.Provider provider : providerSnapshot) packages.add(provider.packageName);
        Map<String, Program> result = new LinkedHashMap<>();
        long now = System.currentTimeMillis();
        for (LiveSource source : sources) {
            try {
                List<Program> loaded = source.load(context, Collections.unmodifiableList(providerSnapshot));
                if (loaded == null) continue;
                for (Program program : loaded) {
                    if (program == null || program.title == null || program.title.isEmpty()
                            || program.channelName == null || program.channelName.isEmpty()
                            || !packages.contains(program.providerPackage)
                            || program.endMillis <= program.startMillis
                            || program.endMillis <= now - 24L * 60 * 60 * 1000) continue;
                    if (result.size() >= MAX_PROGRAMS) break;
                    result.put(program.providerPackage + ":" + program.id, program);
                }
            } catch (Exception ignored) {
                // Missing EPG permissions/accounts leave an empty state rather than a made-up guide.
            }
        }
        synchronized (cacheLock) {
            if (generation == invalidation) cache = Collections.unmodifiableList(new ArrayList<>(result.values()));
        }
    }

    public List<Program> getNow(String providerPackage) {
        List<Program> result = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Program program : cache) {
            if ((providerPackage == null || providerPackage.isEmpty() || providerPackage.equals(program.providerPackage))
                    && program.startMillis <= now && program.endMillis > now) result.add(program);
        }
        return result;
    }
    /** Future guide adapters can cache real upcoming entries with the same program model. */
    public List<Program> getGuide(String providerPackage, long fromMillis, long toMillis) {
        List<Program> result = new ArrayList<>();
        if (toMillis <= fromMillis) return result;
        for (Program program : cache) {
            if ((providerPackage == null || providerPackage.isEmpty() || providerPackage.equals(program.providerPackage))
                    && program.endMillis > fromMillis && program.startMillis < toMillis) result.add(program);
        }
        result.sort((left, right) -> Long.compare(left.startMillis, right.startMillis));
        return result;
    }
    public void purgePackage(String packageName) {
        synchronized (cacheLock) {
            invalidation++;
            List<Program> next = new ArrayList<>();
            for (Program program : cache) if (!packageName.equals(program.providerPackage)) next.add(program);
            cache = Collections.unmodifiableList(next);
        }
    }

    private static final class Channel {
        final String name, packageName;
        Channel(String name, String packageName) { this.name = name; this.packageName = packageName; }
    }
    private static final class PlatformSource implements LiveSource {
        @Override public List<Program> load(Context context,
                List<EntertainmentAppRepository.Provider> providers) {
            Set<String> packages = new HashSet<>();
            for (EntertainmentAppRepository.Provider provider : providers) packages.add(provider.packageName);
            Map<Long, Channel> channels = new HashMap<>();
            List<Program> result = new ArrayList<>();
            try (Cursor cursor = context.getContentResolver().query(TvContract.Channels.CONTENT_URI,
                    null, null, null, null)) {
                if (cursor == null) return result;
                int read = 0;
                while (read++ < 300 && cursor.moveToNext()) {
                    String pkg = text(cursor, "package_name");
                    if (!packages.contains(pkg) || number(cursor, "browsable", 1) == 0) continue;
                    long id = number(cursor, "_id", -1);
                    String name = text(cursor, "display_name");
                    if (id >= 0 && !name.isEmpty()) channels.put(id, new Channel(name, pkg));
                }
            } catch (RuntimeException ignored) { return result; }
            if (channels.isEmpty()) return result;
            long now = System.currentTimeMillis();
            try (Cursor cursor = context.getContentResolver().query(TvContract.Programs.CONTENT_URI,
                    null, "start_time_utc_millis <= ? AND end_time_utc_millis > ?",
                    new String[]{Long.toString(now), Long.toString(now)}, null)) {
                if (cursor == null) return result;
                int read = 0;
                while (read++ < 400 && result.size() < MAX_PROGRAMS && cursor.moveToNext()) {
                    long channelId = number(cursor, "channel_id", -1);
                    Channel channel = channels.get(channelId);
                    if (channel == null) continue;
                    String title = text(cursor, "title");
                    if (title.isEmpty()) continue;
                    long start = number(cursor, "start_time_utc_millis", 0);
                    long end = number(cursor, "end_time_utc_millis", 0);
                    if (start > now || end <= now) continue;
                    String artwork = text(cursor, "poster_art_uri");
                    if (artwork.isEmpty()) artwork = text(cursor, "thumbnail_uri");
                    result.add(new Program(text(cursor, "_id"), channel.name, title, channel.packageName,
                            artwork.isEmpty() ? null : Uri.parse(artwork), TvContract.buildChannelUri(channelId), start, end));
                }
            } catch (RuntimeException ignored) {
                // TV listings are only read when the firmware grants access to this app.
            }
            return result;
        }
    }
    private static String text(Cursor cursor, String name) {
        int index = cursor.getColumnIndex(name);
        return index < 0 || cursor.isNull(index) ? "" : cursor.getString(index);
    }
    private static long number(Cursor cursor, String name, long fallback) {
        try { String value = text(cursor, name); return value.isEmpty() ? fallback : Long.parseLong(value); }
        catch (RuntimeException ignored) { return fallback; }
    }
}
