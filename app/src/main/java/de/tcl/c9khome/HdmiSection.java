package de.tcl.c9khome;

import android.app.AlertDialog;
import android.graphics.Color;
import android.media.tv.TvInputManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Presentation of the unchanged HDMI repository, switching and brand overrides. */
public final class HdmiSection {
    private final ScreenHost host;
    private final HdmiRepository repository;
    private LinearLayout row;

    public HdmiSection(ScreenHost host, HdmiRepository repository) {
        this.host = host;
        this.repository = repository;
    }

    public void addTo(ScreenView screen) {
        row = screen.row(host.ui(), "HDMI & Eingänge");
        populate();
    }

    public void refresh() {
        // An input callback may arrive while another screen is shown.
        if (row == null || !row.isShown()) return;
        populate();
        host.rebindFocus();
    }

    private void populate() {
        row.removeAllViews();
        List<HdmiInput> inputs = repository.list();
        if (inputs.isEmpty()) {
            TextView empty = host.ui().text("Keine HDMI-Eingänge wurden über Android TV gemeldet.", 16, false);
            empty.setPadding(host.ui().dp(22), host.ui().dp(30), host.ui().dp(22), host.ui().dp(30));
            empty.setTextColor(Color.rgb(168, 181, 201));
            row.addView(empty);
            return;
        }
        for (HdmiInput input : inputs) row.addView(createTile(input));
    }

    private View createTile(HdmiInput input) {
        TvUi ui = host.ui();
        LinearLayout tile = ui.card();
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(ui.dp(18), ui.dp(18), ui.dp(18), ui.dp(14));

        TextView logo = ui.text(input.brand.logoText, input.brand == DeviceBrand.HDMI ? 18 : 22, true);
        logo.setGravity(Gravity.CENTER);
        logo.setLetterSpacing(input.brand == DeviceBrand.HDMI ? 0.08f : 0.03f);
        logo.setAlpha(input.state == TvInputManager.INPUT_STATE_DISCONNECTED ? 0.35f : 1f);
        tile.addView(logo, new LinearLayout.LayoutParams(ui.dp(170), ui.dp(72)));

        TextView label = ui.text(input.brand == DeviceBrand.HDMI ? input.label : input.brand.displayName, 15, true);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        tile.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(34)));

        String stateText = input.state == TvInputManager.INPUT_STATE_CONNECTED ? "Verbunden"
                : input.state == TvInputManager.INPUT_STATE_CONNECTED_STANDBY ? "Standby" : "Nicht verbunden";
        TextView state = ui.text(stateText, 12, false);
        state.setTextColor(Color.LTGRAY);
        state.setGravity(Gravity.CENTER);
        tile.addView(state, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(24)));

        tile.setOnClickListener(v -> LauncherUtils.openHdmi(host.activity(), input.id));
        tile.setOnLongClickListener(v -> { showBrandPicker(input); return true; });
        tile.setContentDescription(input.label + ", " + stateText + ". Lang drücken zum Logo ändern.");
        ui.styleFocus(tile);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ui.dp(230), ui.dp(165));
        lp.setMargins(ui.dp(6), ui.dp(9), ui.dp(16), ui.dp(9));
        tile.setLayoutParams(lp);
        return tile;
    }

    private void showBrandPicker(HdmiInput input) {
        List<DeviceBrand> brands = new ArrayList<>();
        for (DeviceBrand b : DeviceBrand.values()) brands.add(b);
        String[] names = new String[brands.size() + 1];
        names[0] = "Automatisch erkennen";
        for (int i = 0; i < brands.size(); i++) names[i + 1] = brands.get(i).displayName;
        new AlertDialog.Builder(host.activity())
                .setTitle(input.label + " – Logo")
                .setItems(names, (dialog, which) -> {
                    if (which == 0) repository.clearOverride(input.id);
                    else repository.setOverride(input.id, brands.get(which - 1));
                })
                .show();
    }
}
