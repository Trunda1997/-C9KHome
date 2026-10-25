package de.tcl.c9khome;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.List;

/** A page plus its visual rows. The focus map is derived from current children. */
public final class ScreenView {
    public final LinearLayout root;
    private final List<ViewGroup> groups = new ArrayList<>();

    public ScreenView(Activity activity, int pageId) {
        root = new LinearLayout(activity);
        root.setId(pageId);
        root.setOrientation(LinearLayout.VERTICAL);
        passive(root);
    }

    public LinearLayout row(TvUi ui, String title) {
        if (title != null && !title.isEmpty()) ui.heading(root, title);
        HorizontalScrollView scroll = new HorizontalScrollView(root.getContext());
        passive(scroll);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        int pad = ui.dp(8);
        scroll.setPadding(pad, pad, pad, pad);
        LinearLayout row = new LinearLayout(root.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        passive(row);
        scroll.addView(row, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = ui.dp(22);
        root.addView(scroll, lp);
        registerRow(row);
        return row;
    }

    public void registerRow(ViewGroup row) {
        if (!groups.contains(row)) groups.add(row);
    }

    public void registerGrid(GridLayout grid) { registerRow(grid); }

    public List<List<View>> focusRows() {
        List<List<View>> rows = new ArrayList<>();
        for (ViewGroup group : groups) {
            if (!group.isShown()) continue;
            int columns = group instanceof GridLayout ? Math.max(1, ((GridLayout) group).getColumnCount()) : Integer.MAX_VALUE;
            List<View> row = new ArrayList<>();
            for (int i = 0; i < group.getChildCount(); i++) {
                View item = group.getChildAt(i);
                if (item.getVisibility() != View.VISIBLE || !item.isFocusable() || !item.isEnabled()) continue;
                row.add(item);
                if (row.size() == columns) { rows.add(row); row = new ArrayList<>(); }
            }
            if (!row.isEmpty()) rows.add(row);
        }
        return rows;
    }

    public static void passive(ViewGroup group) {
        group.setFocusable(false);
        group.setFocusableInTouchMode(false);
        group.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
    }
}
