package de.tcl.c9khome;

import android.graphics.Rect;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import java.util.List;

/** Explicit row navigation prevents ScrollViews taking focus from TV controls. */
public final class FocusNavigator {
    private final ScreenHost host;
    public FocusNavigator(ScreenHost host) { this.host = host; }

    public void bind(List<View> top, View active, ScreenView page) {
        List<List<View>> rows = page.focusRows();
        for (List<View> row : rows) for (View v : row) {
            if (v.getId() == View.NO_ID) v.setId(View.generateViewId());
            v.setFocusableInTouchMode(true);
        }
        View first = rows.isEmpty() ? active : rows.get(0).get(0);
        for (int i = 0; i < top.size(); i++) {
            link(top.get(i), top.get(Math.max(0, i - 1)), top.get(Math.min(top.size() - 1, i + 1)), top.get(i), first);
        }
        for (int r = 0; r < rows.size(); r++) {
            List<View> row = rows.get(r);
            for (int c = 0; c < row.size(); c++) {
                View item = row.get(c);
                View up = r == 0 ? active : rows.get(r - 1).get(Math.min(c, rows.get(r - 1).size() - 1));
                View down = r == rows.size() - 1 ? item : rows.get(r + 1).get(Math.min(c, rows.get(r + 1).size() - 1));
                link(item, row.get(Math.max(0, c - 1)), row.get(Math.min(row.size() - 1, c + 1)), up, down);
            }
        }
    }

    private void link(View item, View left, View right, View up, View down) {
        item.setNextFocusLeftId(left.getId()); item.setNextFocusRightId(right.getId());
        item.setNextFocusUpId(up.getId()); item.setNextFocusDownId(down.getId());
        item.setOnKeyListener((v, code, event) -> {
            if (host.handleSpecialKey(v, code, event)) return true;
            View target; int direction;
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    // Native text editing retains cursor movement when selected.
                    if (item instanceof EditText && item.isSelected()) return false;
                    target = left; direction = View.FOCUS_LEFT; break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (item instanceof EditText && item.isSelected()) return false;
                    target = right; direction = View.FOCUS_RIGHT; break;
                case KeyEvent.KEYCODE_DPAD_UP: target = up; direction = View.FOCUS_UP; break;
                case KeyEvent.KEYCODE_DPAD_DOWN: target = down; direction = View.FOCUS_DOWN; break;
                default: return false;
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN) reveal(target, direction);
            return true;
        });
    }

    public static void reveal(View target, int direction) {
        if (target == null || !target.requestFocus(direction)) return;
        target.post(() -> {
            if (target.hasFocus()) target.requestRectangleOnScreen(
                    new Rect(0, 0, target.getWidth(), target.getHeight()), false);
        });
    }
}
