package de.tcl.c9khome;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.tv.TvContract;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

/** Opens an advertised TV app, without relying on firmware-specific package names. */
public final class LiveTvLauncher {
    private static final String CATEGORY_LIVE_TV = "android.intent.category.APP_LIVE_TV";

    private LiveTvLauncher() {}

    public static boolean open(Activity activity) {
        PackageManager pm = activity.getPackageManager();
        Intent[] candidates = {
                new Intent(Intent.ACTION_MAIN).addCategory(CATEGORY_LIVE_TV),
                new Intent(Intent.ACTION_VIEW).setDataAndType(
                        TvContract.Channels.CONTENT_URI, TvContract.Channels.CONTENT_TYPE)
        };
        for (Intent candidate : candidates) {
            int flags = Intent.ACTION_VIEW.equals(candidate.getAction()) ? PackageManager.MATCH_DEFAULT_ONLY : 0;
            List<ResolveInfo> handlers = new ArrayList<>(pm.queryIntentActivities(candidate, flags));
            // A firmware's original TV application should take precedence over
            // third-party players that also advertise a public TV intent.
            handlers.sort((left, right) -> Boolean.compare(isSystemApp(right), isSystemApp(left)));
            for (ResolveInfo handler : handlers) {
                if (handler.activityInfo == null || !handler.activityInfo.exported
                        || !handler.activityInfo.enabled || !handler.activityInfo.applicationInfo.enabled
                        || activity.getPackageName().equals(handler.activityInfo.packageName)) continue;
                Intent launch = new Intent(candidate).setComponent(new ComponentName(
                        handler.activityInfo.packageName, handler.activityInfo.name));
                try {
                    activity.startActivity(launch);
                    return true;
                } catch (RuntimeException ignored) {
                    // A handler can disappear or require a firmware permission.
                    // Try the next public handler before showing the fallback.
                }
            }
        }
        Toast.makeText(activity, "Die TV-/Tuner-Funktion ist auf diesem Gerät nicht direkt verfügbar.",
                Toast.LENGTH_LONG).show();
        return false;
    }

    private static boolean isSystemApp(ResolveInfo info) {
        return info.activityInfo != null && (info.activityInfo.applicationInfo.flags
                & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
    }
}
