package de.tcl.c9khome;

import android.view.View;
import android.widget.LinearLayout;
import java.util.List;

/** Installed TV providers, actual now-playing data, and a future guide surface. */
public final class LiveTvScreen {
    private LiveTvScreen() {}

    public static ScreenView build(ScreenHost host) {
        ScreenView screen = new ScreenView(host.activity(), R.id.page_live_tv);
        host.ui().hero(screen, "Live TV", "Deine Sender und Live-Anbieter an einem Ort");
        MoviesScreen.providerFilters(host, screen, true);
        LinearLayout actions = screen.row(host.ui(), "Fernsehen");
        View tuner = host.ui().button("TV / Tuner öffnen", () -> LiveTvLauncher.open(host.activity()));
        tuner.setId(R.id.live_tv_open);
        actions.addView(tuner);
        for (EntertainmentAppRepository.Provider provider : host.providers()) {
            if (!provider.liveTv) continue;
            actions.addView(host.ui().button(provider.name + " öffnen", () -> provider.app.launch(host.activity())));
        }
        List<LiveTvRepository.Program> now = host.liveTv().getNow(host.providerFilter());
        if (now.isEmpty()) {
            host.ui().emptyRow(screen, "Läuft jetzt", "Noch keine aktuellen Sendungsdaten verfügbar");
        } else {
            LinearLayout programs = screen.row(host.ui(), "Läuft jetzt");
            for (LiveTvRepository.Program program : now) {
                programs.addView(host.ui().liveCard(program, () -> {
                    if (program.deepLinkUri != null) {
                        MediaDetailScreen.openLink(host, program.deepLinkUri, program.providerPackage);
                    } else {
                        boolean launched = false;
                        for (EntertainmentAppRepository.Provider provider : host.providers()) {
                            if (provider.packageName.equals(program.providerPackage)) {
                                provider.app.launch(host.activity());
                                launched = true;
                                break;
                            }
                        }
                        if (!launched) LiveTvLauncher.open(host.activity());
                    }
                }));
            }
        }
        host.ui().emptyRow(screen, "TV-Guide / Timeline",
                "Der Programmführer wird verfügbar, sobald eine passende EPG-Datenquelle verbunden ist");
        return screen;
    }
}
