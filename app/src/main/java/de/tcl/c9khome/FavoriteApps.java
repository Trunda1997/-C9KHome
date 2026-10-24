package de.tcl.c9khome;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;

/** Persists an ordered package list. An explicitly empty list stays empty. */
public final class FavoriteApps {
    public static final String PREFERENCES_NAME = "home_favorites";
    public static final String KEY_PACKAGES = "packages";
    private final SharedPreferences preferences;

    public FavoriteApps(Context context) {
        preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    public List<String> getPackages() {
        Set<String> packages = new LinkedHashSet<>();
        try {
            JSONArray stored = new JSONArray(preferences.getString(KEY_PACKAGES, "[]"));
            for (int i = 0; i < stored.length(); i++) {
                Object value = stored.opt(i);
                if (value instanceof String && !((String) value).isEmpty()) packages.add((String) value);
            }
        } catch (JSONException ignored) {
            // A damaged stored value is not permission to overwrite the user's
            // chosen favorites with automatically generated defaults.
        }
        return new ArrayList<>(packages);
    }

    public boolean contains(String packageName) { return getPackages().contains(packageName); }

    public void add(String packageName) {
        List<String> packages = getPackages();
        packages.add(packageName);
        setPackages(packages);
    }

    public void remove(String packageName) {
        List<String> packages = getPackages();
        packages.remove(packageName);
        setPackages(packages);
    }

    public void setPackages(List<String> packageNames) {
        Set<String> unique = new LinkedHashSet<>();
        for (String packageName : packageNames) {
            if (packageName != null && !packageName.isEmpty()) unique.add(packageName);
        }
        // apply updates the in-process preferences immediately and serializes
        // disk writes, so both pages see changes as soon as the menu closes.
        preferences.edit().putString(KEY_PACKAGES, new JSONArray(unique).toString()).apply();
    }

    public void initializeIfMissing(List<LaunchableApps.Entry> installedApps) {
        if (preferences.contains(KEY_PACKAGES)) return;
        Set<String> installed = new LinkedHashSet<>();
        for (LaunchableApps.Entry entry : installedApps) installed.add(entry.packageName);
        List<String> defaults = new ArrayList<>();
        for (String packageName : Arrays.asList(
                "com.google.android.youtube.tv", "com.netflix.ninja", "com.amazon.amazonvideo.livingroom",
                "com.disney.disneyplus", "com.apple.atve.androidtv.appletv", "com.plexapp.android",
                "com.spotify.tv.android", "com.google.android.videos", "com.android.vending")) {
            if (installed.contains(packageName)) defaults.add(packageName);
        }
        // This fallback only chooses a small initial selection, never limits
        // the number of favorites the user can subsequently add or display.
        if (defaults.isEmpty()) {
            for (String packageName : installed) {
                if (isSettings(packageName)) continue;
                defaults.add(packageName);
                if (defaults.size() == 4) break;
            }
        }
        setPackages(defaults);
    }

    private static boolean isSettings(String packageName) {
        String name = packageName.toLowerCase(java.util.Locale.ROOT);
        return name.contains("settings") || name.contains("setting");
    }
}
