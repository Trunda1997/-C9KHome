package de.tcl.c9khome;

import android.content.Intent;
import android.net.Uri;
import android.widget.LinearLayout;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

/** Movie, series, episode and person details. Missing metadata stays missing. */
public final class MediaDetailScreen {
    private MediaDetailScreen() {}

    public static ScreenView build(ScreenHost host, MediaItem item) {
        ScreenView screen = new ScreenView(host.activity(), R.id.page_detail);
        if (item.backgroundUri != null) screen.root.addView(host.ui().artwork(item.backgroundUri, 220));
        host.ui().hero(screen, item.title, metadata(item));
        if (item.posterUri != null) screen.root.addView(host.ui().artwork(item.posterUri, 200));
        if (item.description != null && !item.description.isEmpty()) {
            host.ui().description(screen.root, item.description);
        }
        String providerName = null;
        EntertainmentAppRepository.Provider provider = null;
        for (EntertainmentAppRepository.Provider candidate : host.providers()) {
            if (candidate.packageName.equals(item.providerPackage)) {
                provider = candidate;
                providerName = candidate.name;
                break;
            }
        }
        if (providerName != null) host.ui().description(screen.root, "Anbieter: " + providerName);
        LinearLayout actions = screen.row(host.ui(), "Aktionen");
        if (item.deepLinkUri != null) {
            String watch = item.progress > 0 ? "Weiterschauen" : "Jetzt ansehen";
            actions.addView(host.ui().button(watch, () -> openLink(host, item.deepLinkUri, item.providerPackage)));
        } else if (provider != null) {
            EntertainmentAppRepository.Provider target = provider;
            actions.addView(host.ui().button(target.name + " öffnen", () -> target.app.launch(host.activity())));
        }
        if (item.trailerUri != null) {
            actions.addView(host.ui().button("Trailer", () -> openLink(host, item.trailerUri, null)));
        }
        if (item.kind != MediaItem.Kind.PERSON) {
            actions.addView(host.ui().button(host.media().isSaved(item) ? "Aus meiner Liste entfernen" : "Zu meiner Liste",
                    () -> {
                        host.media().toggleSaved(item);
                        host.refreshScreen();
                    }));
        }
        if (actions.getChildCount() == 0) {
            actions.addView(host.ui().button("Zur Startseite", () -> host.navigate(ScreenId.HOME)));
        }
        if (item.progress > 0) {
            int percent = Math.round(Math.max(0f, Math.min(1f, item.progress)) * 100);
            host.ui().description(screen.root, "Fortschritt: " + percent + " %");
        }
        boolean series = item.kind == MediaItem.Kind.SERIES || item.kind == MediaItem.Kind.EPISODE;
        if (series) {
            if (item.nextEpisode != null) {
                List<MediaItem> next = new ArrayList<>();
                next.add(item.nextEpisode);
                host.ui().mediaRow(screen, "Nächste Folge", next, host);
            }
            if (item.episodes == null || item.episodes.isEmpty()) {
                host.ui().emptyRow(screen, "Staffeln und Episoden", "Noch keine Staffel- oder Episodendaten verfügbar");
            } else {
                // The source owns episode titles and numbers; never synthesize a season.
                host.ui().mediaRow(screen, "Staffeln und Episoden", item.episodes, host);
            }
        }
        return screen;
    }

    private static String metadata(MediaItem item) {
        List<String> values = new ArrayList<>();
        if (item.year > 0) values.add(Integer.toString(item.year));
        if (item.durationMinutes > 0) values.add(item.durationMinutes + " Min.");
        if (item.rating != null && !item.rating.isEmpty()) values.add(item.rating);
        if (item.genres != null && !item.genres.isEmpty()) values.add(android.text.TextUtils.join(" · ", item.genres));
        if (item.seasonNumber > 0) values.add("Staffel " + item.seasonNumber);
        if (item.episodeNumber > 0) values.add("Folge " + item.episodeNumber);
        if (item.personRole != null && !item.personRole.isEmpty()) values.add(item.personRole);
        return android.text.TextUtils.join("  •  ", values);
    }

    static void openLink(ScreenHost host, Uri uri, String providerPackage) {
        if (uri == null) return;
        try {
            Intent intent;
            if ("intent".equalsIgnoreCase(uri.getScheme())) {
                intent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME);
                intent.setAction(Intent.ACTION_VIEW);
                intent.setSelector(null);
                if (intent.getComponent() != null && (providerPackage == null
                        || !providerPackage.equals(intent.getComponent().getPackageName()))) {
                    intent.setComponent(null);
                }
            } else {
                String scheme = uri.getScheme();
                if (scheme == null || "file".equalsIgnoreCase(scheme) || "javascript".equalsIgnoreCase(scheme)
                        || "data".equalsIgnoreCase(scheme)) throw new IllegalArgumentException("Unsupported media link");
                intent = new Intent(Intent.ACTION_VIEW, uri);
            }
            if (providerPackage != null && !providerPackage.isEmpty()) intent.setPackage(providerPackage);
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            host.activity().startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(host.activity(), "Dieser Inhalt konnte nicht geöffnet werden.", Toast.LENGTH_SHORT).show();
        }
    }
}
