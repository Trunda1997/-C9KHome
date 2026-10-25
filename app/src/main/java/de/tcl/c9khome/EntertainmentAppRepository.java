package de.tcl.c9khome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Bundle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Classifies installed, launchable apps using several independent platform signals. */
public final class EntertainmentAppRepository {
    public static final class Provider {
        public final String packageName, name;
        public final boolean liveTv;
        public final LaunchableApps.Entry app;
        public Provider(String packageName, String name, boolean liveTv, LaunchableApps.Entry app) {
            this.packageName = packageName; this.name = name; this.liveTv = liveTv; this.app = app;
        }
    }
    private final Context context;
    private static final Map<String, String> NAMES = new HashMap<>();
    private static final Set<String> LIVE = new HashSet<>();
    static {
        known("Netflix", false, "com.netflix.ninja", "com.netflix.mediaclient");
        known("Disney+", false, "com.disney.disneyplus");
        known("Prime Video", false, "com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient");
        known("Apple TV", false, "com.apple.atve.androidtv.appletv", "com.apple.atve.sony.appletv");
        known("WOW", true, "de.sky.online", "de.sky.bw");
        known("Paramount+", false, "com.cbs.ca", "com.cbs.ott");
        known("YouTube", false, "com.google.android.youtube.tv", "com.google.android.youtube");
        known("Plex", true, "com.plexapp.android");
        known("Joyn", true, "de.prosiebensat1digital.seventv");
        known("waipu.tv", true, "de.exaring.waipu");
        known("Zattoo", true, "com.zattoo.player", "com.zattoo.android");
        known("MagentaTV", true, "de.telekom.magentatv", "de.telekom.magentatv.tv", "de.telekom.magentatv.firetv");
        known("GigaTV", true, "com.vodafone.tv", "com.vodafone.gigatv");
        known("Twitch", true, "tv.twitch.android.app");
        known("Spotify", false, "com.spotify.tv.android", "com.spotify.music");
    }
    private static void known(String name, boolean live, String... packages) {
        for (String pkg : packages) { NAMES.put(pkg, name); if (live) LIVE.add(pkg); }
    }
    public EntertainmentAppRepository(Context context) { this.context = context.getApplicationContext(); }

    /** Call on the same worker as installed-app and TV-provider discovery. */
    public List<Provider> detect(List<LaunchableApps.Entry> installed) {
        PackageManager pm = context.getPackageManager();
        Set<String> tvDataPackages = new HashSet<>();
        Set<String> channelPackages = new HashSet<>();
        collectPackages(TvContract.WatchNextPrograms.CONTENT_URI, tvDataPackages);
        collectPackages(TvContract.PreviewPrograms.CONTENT_URI, tvDataPackages);
        collectPackages(TvContract.Channels.CONTENT_URI, channelPackages);
        Set<String> liveIntents = new HashSet<>();
        for (ResolveInfo info : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN)
                .addCategory("android.intent.category.APP_LIVE_TV"), 0)) {
            if (info.activityInfo != null) liveIntents.add(info.activityInfo.packageName);
        }
        List<Provider> result = new ArrayList<>();
        for (LaunchableApps.Entry entry : installed) {
            try {
                ApplicationInfo app = pm.getApplicationInfo(entry.packageName, PackageManager.GET_META_DATA);
                boolean mediaCategory = app.category == ApplicationInfo.CATEGORY_VIDEO
                        || app.category == ApplicationInfo.CATEGORY_AUDIO;
                boolean hasTvEntry = pm.getLeanbackLaunchIntentForPackage(entry.packageName) != null;
                boolean dataOwner = tvDataPackages.contains(entry.packageName) || channelPackages.contains(entry.packageName);
                boolean live = LIVE.contains(entry.packageName) || channelPackages.contains(entry.packageName)
                        || liveIntents.contains(entry.packageName);
                if (NAMES.containsKey(entry.packageName) || mediaCategory || dataOwner || live
                        || (hasTvEntry && mediaMetadata(app.metaData))) {
                    result.add(new Provider(entry.packageName,
                            NAMES.containsKey(entry.packageName) ? NAMES.get(entry.packageName) : entry.label, live, entry));
                }
            } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
                // A package can be removed between the package broadcast and this scan.
            }
        }
        return result;
    }

    private boolean mediaMetadata(Bundle metadata) {
        if (metadata == null) return false;
        for (String key : metadata.keySet()) {
            String value = key.toLowerCase(Locale.ROOT);
            if (value.startsWith("android.media.") || value.contains("media_content")
                    || value.contains("tv_input") || value.contains("video_provider")
                    || value.contains("streaming")) return true;
        }
        return false;
    }

    private void collectPackages(Uri uri, Set<String> packages) {
        try (Cursor cursor = context.getContentResolver().query(uri, new String[]{"package_name"}, null, null, null)) {
            if (cursor == null) return;
            int count = 0;
            while (count++ < 200 && cursor.moveToNext()) {
                String pkg = cursor.getString(0);
                if (pkg != null && !pkg.isEmpty()) packages.add(pkg);
            }
        } catch (RuntimeException ignored) {
            // Public TV data may be absent or protected on a particular firmware.
        }
    }
}
