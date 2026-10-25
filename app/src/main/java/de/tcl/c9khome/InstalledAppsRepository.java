package de.tcl.c9khome;

import android.content.Context;
import java.util.List;

/** Package-deduplicated installed launchers; the caller runs discovery on a worker. */
public final class InstalledAppsRepository {
    private final Context context;
    public InstalledAppsRepository(Context context) { this.context = context.getApplicationContext(); }
    public List<LaunchableApps.Entry> load() { return LaunchableApps.load(context); }
}
