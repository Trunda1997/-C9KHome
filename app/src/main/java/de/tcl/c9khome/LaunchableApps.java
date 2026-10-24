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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Discovers public launcher entry points without requesting package-wide visibility. */
public final class LaunchableApps {
    private LaunchableApps() {}

    public static final class Entry {
        public final String label;
        public final ComponentName component;
        private final ResolveInfo resolveInfo;
        private final String category;

        private Entry(String label, ComponentName component, ResolveInfo resolveInfo, String category) {
            this.label = label;
            this.component = component;
            this.resolveInfo = resolveInfo;
            this.category = category;
        }

        public Drawable loadIcon(PackageManager pm) {
            try { return resolveInfo.loadIcon(pm); }
            catch (RuntimeException e) { return pm.getDefaultActivityIcon(); }
        }

        public void launch(Activity activity) {
            Intent intent = new Intent(Intent.ACTION_MAIN)
                    .addCategory(category)
                    .setComponent(component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            try { activity.startActivity(intent); }
            catch (RuntimeException e) {
                Toast.makeText(activity, label + " konnte nicht geöffnet werden.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public static List<Entry> load(Context context) {
        PackageManager pm = context.getPackageManager();
        Map<ComponentName, Entry> entries = new LinkedHashMap<>();
        // Prefer the TV entry point when one activity advertises both categories.
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo info : pm.queryIntentActivities(query, 0)) {
                ActivityInfo activity = info.activityInfo;
                if (activity == null || !activity.exported || !activity.enabled
                        || !activity.applicationInfo.enabled
                        || context.getPackageName().equals(activity.packageName)) continue;
                if (activity.permission != null && pm.checkPermission(activity.permission,
                        context.getPackageName()) != PackageManager.PERMISSION_GRANTED) continue;
                ComponentName component = new ComponentName(activity.packageName, activity.name);
                if (entries.containsKey(component)) continue;
                CharSequence label = null;
                try { label = info.loadLabel(pm); } catch (RuntimeException ignored) {}
                String name = label == null || label.length() == 0 ? activity.packageName : label.toString();
                entries.put(component, new Entry(name, component, info, category));
            }
        }
        List<Entry> result = new ArrayList<>(entries.values());
        Collator collator = Collator.getInstance(Locale.GERMANY);
        result.sort((left, right) -> {
            int labels = collator.compare(left.label, right.label);
            return labels != 0 ? labels : left.component.flattenToString().compareTo(right.component.flattenToString());
        });
        return result;
    }
}
