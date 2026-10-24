package de.tcl.c9khome;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class HdmiRepository {
    public interface Listener { void onChanged(); }

    private final Context context;
    private final TvInputManager manager;
    private final SharedPreferences prefs;
    private Listener listener;

    private final TvInputManager.TvInputCallback callback = new TvInputManager.TvInputCallback() {
        @Override public void onInputAdded(String inputId) { notifyChanged(); }
        @Override public void onInputRemoved(String inputId) { notifyChanged(); }
        @Override public void onInputUpdated(String inputId) { notifyChanged(); }
        @Override public void onInputStateChanged(String inputId, int state) { notifyChanged(); }
    };

    public HdmiRepository(Context context) {
        this.context = context;
        this.manager = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
        this.prefs = context.getSharedPreferences("hdmi_overrides", Context.MODE_PRIVATE);
    }

    public void start(Listener listener) {
        this.listener = listener;
        if (manager != null) manager.registerCallback(callback, new Handler(Looper.getMainLooper()));
    }

    public void stop() {
        if (manager != null) manager.unregisterCallback(callback);
        this.listener = null;
    }

    public List<HdmiInput> list() {
        List<HdmiInput> out = new ArrayList<>();
        if (manager == null) return out;
        for (TvInputInfo info : manager.getTvInputList()) {
            boolean hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
            CharSequence custom = null;
            try { custom = info.loadCustomLabel(context); } catch (Exception ignored) {}
            CharSequence normal = null;
            try { normal = info.loadLabel(context); } catch (Exception ignored) {}
            String label = custom != null ? custom.toString() : (normal != null ? normal.toString() : info.getId());
            boolean looksHdmi = label.toLowerCase().contains("hdmi") || info.getId().toLowerCase().contains("hdmi");
            if (!hdmi && !looksHdmi) continue;

            String saved = prefs.getString(info.getId(), null);
            DeviceBrand brand;
            if (saved != null) {
                try { brand = DeviceBrand.valueOf(saved); }
                catch (Exception e) { brand = LogoResolver.resolve(label + " " + info.getId()); }
            } else {
                brand = LogoResolver.resolve(label + " " + info.getId());
            }
            int state = manager.getInputState(info.getId());
            out.add(new HdmiInput(info.getId(), label, state, brand));
        }
        out.sort(Comparator.comparing(a -> a.label));
        return out;
    }

    public void setOverride(String inputId, DeviceBrand brand) {
        prefs.edit().putString(inputId, brand.name()).apply();
        notifyChanged();
    }

    public void clearOverride(String inputId) {
        prefs.edit().remove(inputId).apply();
        notifyChanged();
    }

    private void notifyChanged() {
        if (listener != null) listener.onChanged();
    }
}
