package de.tcl.c9khome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.media.tv.TvContract;
import android.net.Uri;
import android.provider.Settings;
import android.widget.Toast;

public final class LauncherUtils {
    private LauncherUtils() {}

    public static void openSettings(Activity a) {
        try { a.startActivity(new Intent(Settings.ACTION_SETTINGS)); }
        catch (Exception e) { Toast.makeText(a, "Einstellungen konnten nicht geöffnet werden.", Toast.LENGTH_SHORT).show(); }
    }

    public static void openPlayStore(Activity a) {
        try {
            Intent direct = a.getPackageManager().getLeanbackLaunchIntentForPackage("com.android.vending");
            if (direct == null) direct = a.getPackageManager().getLaunchIntentForPackage("com.android.vending");
            if (direct != null) { a.startActivity(direct); return; }
            Intent market = new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=tv"));
            market.setPackage("com.android.vending");
            a.startActivity(market);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(a, "Google Play Store wurde nicht gefunden.", Toast.LENGTH_SHORT).show();
        }
    }

    public static void openHdmi(Activity a, String inputId) {
        try {
            Uri uri = TvContract.buildChannelUriForPassthroughInput(inputId);
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            a.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(a, "Eingang konnte nicht direkt geöffnet werden. Nutze die TCL-Eingangstaste.", Toast.LENGTH_LONG).show();
        }
    }
}
