package de.tcl.c9khome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Store navigation and a named app directory, never a fabricated recommendation feed. */
public final class AppStoreRepository {
    public enum Category {
        ALL("Alle"), ENTERTAINMENT("Unterhaltung"), MOVIES("Filme & Serien"),
        MUSIC("Musik"), GAMES("Spiele"), KIDS("Kinder"), LIFESTYLE("Lifestyle"),
        TOOLS("Tools"), SMART_HOME("Smart Home");
        public final String label;
        Category(String label) { this.label = label; }
    }

    public static final class StoreApp {
        public final String label;
        public final String packageName;
        public final Category category;
        private final List<String> aliases;
        StoreApp(String label, String packageName, Category category, String... aliases) {
            this.label = label;
            this.packageName = packageName;
            this.category = category;
            List<String> names = new ArrayList<>();
            names.add(packageName);
            names.addAll(Arrays.asList(aliases));
            this.aliases = Collections.unmodifiableList(names);
        }
        public LaunchableApps.Entry installedEntry(List<LaunchableApps.Entry> installed) {
            for (LaunchableApps.Entry entry : installed) if (aliases.contains(entry.packageName)) return entry;
            return null;
        }
        boolean matchesPackage(String name) { return aliases.contains(name); }
    }

    private static final List<StoreApp> DIRECTORY = Collections.unmodifiableList(Arrays.asList(
            new StoreApp("Netflix", "com.netflix.ninja", Category.MOVIES, "com.netflix.mediaclient"),
            new StoreApp("Disney+", "com.disney.disneyplus", Category.MOVIES),
            new StoreApp("Prime Video", "com.amazon.amazonvideo.livingroom", Category.MOVIES, "com.amazon.avod.thirdpartyclient"),
            new StoreApp("WOW", "de.sky.online", Category.MOVIES),
            new StoreApp("Paramount+", "com.cbs.app", Category.MOVIES, "com.cbs.ott", "com.cbs.ca"),
            new StoreApp("Apple TV", "com.apple.atve.androidtv.appletv", Category.MOVIES),
            new StoreApp("Plex", "com.plexapp.android", Category.MOVIES),
            new StoreApp("YouTube", "com.google.android.youtube.tv", Category.ENTERTAINMENT, "com.google.android.youtube"),
            new StoreApp("Twitch", "tv.twitch.android.app", Category.ENTERTAINMENT),
            new StoreApp("waipu.tv", "de.exaring.waipu", Category.ENTERTAINMENT),
            new StoreApp("Joyn", "de.prosiebensat1digital.seventv", Category.ENTERTAINMENT),
            new StoreApp("Zattoo", "com.zattoo.player", Category.ENTERTAINMENT),
            new StoreApp("Spotify", "com.spotify.tv.android", Category.MUSIC, "com.spotify.music")
    ));

    private static final ExecutorService CATEGORY_WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "app-store-categories");
        thread.setDaemon(true);
        return thread;
    });
    private final Context context;
    private final Map<String, Category> categories = new HashMap<>();
    private final Handler main = new Handler(Looper.getMainLooper());

    public AppStoreRepository(Context context) { this.context = context.getApplicationContext(); }

    public List<StoreApp> directory(Category category) {
        List<StoreApp> result = new ArrayList<>();
        for (StoreApp app : DIRECTORY) if (category == Category.ALL || app.category == category) result.add(app);
        return result;
    }

    /** The Android category lookup stays off the UI thread and is bounded by installed packages. */
    public void loadCategories(List<LaunchableApps.Entry> installed, Runnable changed) {
        List<LaunchableApps.Entry> snapshot = new ArrayList<>(installed);
        CATEGORY_WORKER.execute(() -> {
            Map<String, Category> found = new HashMap<>();
            PackageManager pm = context.getPackageManager();
            for (LaunchableApps.Entry entry : snapshot) {
                Category known = knownCategory(entry.packageName);
                if (known != null) { found.put(entry.packageName, known); continue; }
                try {
                    ApplicationInfo info = pm.getApplicationInfo(entry.packageName, PackageManager.GET_META_DATA);
                    found.put(entry.packageName, androidCategory(info));
                } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
                    found.put(entry.packageName, Category.TOOLS);
                }
            }
            main.post(() -> {
                categories.clear();
                categories.putAll(found);
                changed.run();
            });
        });
    }

    public boolean matches(LaunchableApps.Entry entry, Category category, String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.GERMANY);
        boolean search = needle.isEmpty() || entry.label.toLowerCase(Locale.GERMANY).contains(needle)
                || entry.packageName.toLowerCase(Locale.ROOT).contains(needle);
        Category known = knownCategory(entry.packageName);
        Category detected = known != null ? known : categories.get(entry.packageName);
        return search && (category == Category.ALL || detected == category);
    }

    private static Category knownCategory(String packageName) {
        for (StoreApp app : DIRECTORY) if (app.matchesPackage(packageName)) return app.category;
        if (packageName.equals("com.google.android.youtube.tvkids")
                || packageName.equals("com.google.android.apps.youtube.kids")) return Category.KIDS;
        if (packageName.equals("com.google.android.apps.chromecast.app")
                || packageName.equals("com.amazon.dee.app")
                || packageName.equals("com.xiaomi.smarthome")) return Category.SMART_HOME;
        return null;
    }

    private static Category androidCategory(ApplicationInfo info) {
        if (info.metaData != null) {
            // Optional app-supplied flags refine Android's broad app categories.
            if (info.metaData.getBoolean("android.app.is_game", false)) return Category.GAMES;
        }
        switch (info.category) {
            case ApplicationInfo.CATEGORY_GAME: return Category.GAMES;
            case ApplicationInfo.CATEGORY_AUDIO: return Category.MUSIC;
            case ApplicationInfo.CATEGORY_VIDEO: return Category.ENTERTAINMENT;
            case ApplicationInfo.CATEGORY_SOCIAL: return Category.LIFESTYLE;
            default: return Category.TOOLS;
        }
    }

    public static void openSearch(Activity activity, String query) {
        String text = query == null || query.trim().isEmpty() ? "Android TV" : query.trim();
        openStore(activity, Uri.parse("market://search").buildUpon().appendQueryParameter("q", text)
                        .appendQueryParameter("c", "apps").build(),
                Uri.parse("https://play.google.com/store/search").buildUpon().appendQueryParameter("q", text)
                        .appendQueryParameter("c", "apps").build());
    }

    public static void openDetails(Activity activity, String packageName) {
        openStore(activity, Uri.parse("market://details").buildUpon().appendQueryParameter("id", packageName).build(),
                Uri.parse("https://play.google.com/store/apps/details").buildUpon().appendQueryParameter("id", packageName).build());
    }

    private static void openStore(Activity activity, Uri market, Uri web) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, market).setPackage("com.android.vending"));
            return;
        } catch (ActivityNotFoundException | SecurityException ignored) {}
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, web));
        } catch (ActivityNotFoundException | SecurityException ignored) {
            Toast.makeText(activity, "Google Play konnte nicht geöffnet werden.", Toast.LENGTH_LONG).show();
        }
    }
}
