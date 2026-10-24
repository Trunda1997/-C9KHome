package de.tcl.c9khome;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.widget.Toast;
import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** One entry per startable package, with the system's TV launch intent preferred. */
public final class LaunchableApps {
    private LaunchableApps() {}

    public static final class Entry {
        public final String label;
        public final String packageName;
        public final ComponentName component;
        private final ResolveInfo resolveInfo;

        private Entry(String label, String packageName, ComponentName component, ResolveInfo resolveInfo) {
            this.label = label;
            this.packageName = packageName;
            this.component = component;
            this.resolveInfo = resolveInfo;
        }

        public Drawable loadIcon(PackageManager pm) {
            try { return resolveInfo.loadIcon(pm); }
            catch (RuntimeException e) { return pm.getDefaultActivityIcon(); }
        }

        public void launch(Activity activity) {
            PackageManager pm = activity.getPackageManager();
            for (boolean tv : new boolean[]{true, false}) {
                try {
                    Intent intent = launchIntent(pm, packageName, tv);
                    if (resolveLaunch(pm, intent, packageName, activity.getPackageName()) == null) continue;
                    activity.startActivity(new Intent(intent).addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
                    return;
                } catch (RuntimeException ignored) {
                    // The app may have changed since discovery. Still try its
                    // regular launcher before reporting a failed launch.
                }
            }
            Toast.makeText(activity, label + " konnte nicht geöffnet werden.", Toast.LENGTH_SHORT).show();
        }
    }

    public static List<Entry> load(Context context) {
        PackageManager pm = context.getPackageManager();
        Set<String> packages = new LinkedHashSet<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo info : pm.queryIntentActivities(query, 0)) {
                ActivityInfo activity = info.activityInfo;
                if (activity != null && !context.getPackageName().equals(activity.packageName)) {
                    packages.add(activity.packageName);
                }
            }
        }
        List<Entry> result = new ArrayList<>();
        for (String packageName : packages) {
            ResolveInfo launch = null;
            for (boolean tv : new boolean[]{true, false}) {
                try { launch = resolveLaunch(pm, launchIntent(pm, packageName, tv),
                        packageName, context.getPackageName()); }
                catch (RuntimeException ignored) {}
                if (launch != null) break;
            }
            if (launch == null) continue;
            CharSequence label = null;
            try { label = launch.loadLabel(pm); } catch (RuntimeException ignored) {}
            String name = label == null || label.length() == 0 ? packageName : label.toString();
            ComponentName component = new ComponentName(packageName, launch.activityInfo.name);
            result.add(new Entry(name, packageName, component, launch));
        }
        Collator collator = Collator.getInstance(Locale.GERMANY);
        result.sort((left, right) -> {
            int labels = collator.compare(left.label, right.label);
            return labels != 0 ? labels : left.packageName.compareTo(right.packageName);
        });
        return result;
    }

    private static Intent launchIntent(PackageManager pm, String packageName, boolean tv) {
        return tv ? pm.getLeanbackLaunchIntentForPackage(packageName) : pm.getLaunchIntentForPackage(packageName);
    }

    private static ResolveInfo resolveLaunch(PackageManager pm, Intent intent, String packageName, String caller) {
        if (intent == null) return null;
        ResolveInfo info = pm.resolveActivity(intent, 0);
        ActivityInfo activity = info == null ? null : info.activityInfo;
        if (activity == null || !packageName.equals(activity.packageName) || !activity.exported
                || !activity.enabled || activity.applicationInfo == null || !activity.applicationInfo.enabled) return null;
        if (activity.permission != null && pm.checkPermission(activity.permission, caller)
                != PackageManager.PERMISSION_GRANTED) return null;
        return info;
    }
}
