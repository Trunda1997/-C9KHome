package de.tcl.c9khome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Navigation uses real remote-control key events only. View access below is for
 * assertions, never for moving focus, clicking, scrolling or injecting touch.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public final class FocusNavigationTest {
    private static final long TIMEOUT_MS = 8000;
    private static final String APP_PACKAGE = "de.tcl.c9khome";
    private static final int[] TOP_BAR = {
            R.id.top_bar_home, R.id.top_bar_apps, R.id.top_bar_movies,
            R.id.top_bar_series, R.id.top_bar_live_tv, R.id.top_bar_settings
    };

    private ActivityScenario<MainActivity> scenario;
    private UiDevice device;

    @Before public void launchLauncher() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        scenario = ActivityScenario.launch(MainActivity.class);
        assertFirstAppFocused();
    }

    @After public void closeLauncher() {
        if (scenario != null) scenario.close();
    }

    @Test public void topBarTraversesAllItemsAndKeepsFocusAtItsEdges() {
        key(KeyEvent.KEYCODE_DPAD_UP);
        assertFocused(TOP_BAR[0]);
        key(KeyEvent.KEYCODE_DPAD_LEFT);
        assertFocused(TOP_BAR[0]);

        for (int index = 0; index < TOP_BAR.length; index++) {
            if (index > 0) key(KeyEvent.KEYCODE_DPAD_RIGHT);
            assertFocused(TOP_BAR[index]);
            final int viewId = TOP_BAR[index];
            scenario.onActivity(activity -> {
                View item = activity.findViewById(viewId);
                assertTrue("Top-bar item must be focusable", item.isFocusable());
                assertTrue("Top-bar item must also work in touch mode", item.isFocusableInTouchMode());
            });
            key(KeyEvent.KEYCODE_DPAD_UP);
            assertFocused(TOP_BAR[index]);
            screenshot("top-bar-" + index);
        }

        key(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertFocused(R.id.top_bar_settings);
        key(KeyEvent.KEYCODE_DPAD_LEFT);
        assertFocused(R.id.top_bar_live_tv);
        for (int index = TOP_BAR.length - 3; index >= 0; index--) {
            key(KeyEvent.KEYCODE_DPAD_LEFT);
            assertFocused(TOP_BAR[index]);
        }
        key(KeyEvent.KEYCODE_DPAD_LEFT);
        assertFocused(R.id.top_bar_home);
    }

    @Test public void everyTopBarItemReturnsToTheCurrentFirstApp() {
        for (int index = 0; index < TOP_BAR.length; index++) {
            key(KeyEvent.KEYCODE_DPAD_UP);
            assertFocused(R.id.top_bar_home);
            for (int step = 0; step < index; step++) key(KeyEvent.KEYCODE_DPAD_RIGHT);
            assertFocused(TOP_BAR[index]);
            key(KeyEvent.KEYCODE_DPAD_DOWN);
            assertFirstAppFocused();
            assertFirstAppVisible();
        }
    }

    @Test public void horizontallyScrolledAppsCanExitUpAndReturnToTheVisibleFirstApp() {
        AtomicInteger appCount = new AtomicInteger();
        scenario.onActivity(activity -> appCount.set(appsRow(activity).getChildCount()));
        assertTrue("The test APK must supply enough TV app entries to exercise scrolling", appCount.get() >= 9);

        int lastIndex = Math.min(9, appCount.get() - 1);
        for (int index = 1; index <= lastIndex; index++) {
            key(KeyEvent.KEYCODE_DPAD_RIGHT);
            final int expectedIndex = index;
            awaitActivity("App focus did not move to tile " + index,
                    activity -> activity.getCurrentFocus() == appsRow(activity).getChildAt(expectedIndex));
        }
        awaitActivity("The apps row did not scroll horizontally", activity -> {
            HorizontalScrollView scroll = (HorizontalScrollView) appsRow(activity).getParent();
            return scroll.getScrollX() > 0;
        });
        screenshot("apps-scrolled-right");

        key(KeyEvent.KEYCODE_DPAD_UP);
        assertFocused(R.id.top_bar_home);
        for (int index = 1; index < TOP_BAR.length; index++) key(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertFocused(R.id.top_bar_settings);
        screenshot("settings-after-scrolled-apps");
        key(KeyEvent.KEYCODE_DPAD_LEFT);
        assertFocused(R.id.top_bar_live_tv);
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        assertFirstAppFocused();
        assertFirstAppVisible();
        screenshot("first-app-restored");
    }

    @Test public void settingsOpensWithOkAndBackKeepsTheFocusFlowAfterOnStart() {
        key(KeyEvent.KEYCODE_DPAD_UP);
        assertFocused(R.id.top_bar_home);
        for (int index = 1; index < TOP_BAR.length; index++) key(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertFocused(R.id.top_bar_settings);

        key(KeyEvent.KEYCODE_DPAD_CENTER);
        awaitPackage(false);
        key(KeyEvent.KEYCODE_BACK);
        awaitPackage(true);
        assertFocused(R.id.top_bar_settings);
        screenshot("settings-returned-with-back");

        key(KeyEvent.KEYCODE_DPAD_LEFT);
        assertFocused(R.id.top_bar_live_tv);
        key(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertFocused(R.id.top_bar_settings);
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        assertFirstAppFocused();
        assertFirstAppVisible();
        key(KeyEvent.KEYCODE_DPAD_UP);
        assertFocused(R.id.top_bar_home);
    }

    private void key(int keyCode) {
        assertTrue("Could not inject remote-control key " + keyCode, device.pressKeyCode(keyCode));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        device.waitForIdle();
    }

    private void assertFocused(int viewId) {
        awaitActivity("Expected focus on view " + viewId, activity ->
                activity.getCurrentFocus() == activity.findViewById(viewId));
        awaitActivity("Focused top-bar item must be visible", activity -> {
            View item = activity.findViewById(viewId);
            Rect visible = new Rect();
            return item.getGlobalVisibleRect(visible)
                    && visible.width() >= item.getWidth() * 0.95f
                    && visible.height() >= item.getHeight() * 0.95f;
        });
        assertNoScrollContainerFocused();
    }

    private void assertFirstAppFocused() {
        awaitActivity("Expected focus on the first app tile", activity -> {
            ViewGroup row = appsRow(activity);
            return row.getChildCount() > 0 && activity.getCurrentFocus() == row.getChildAt(0);
        });
        assertNoScrollContainerFocused();
    }

    private void assertNoScrollContainerFocused() {
        scenario.onActivity(activity -> {
            View focused = activity.getCurrentFocus();
            assertNotNull("Focus must stay on a usable item", focused);
            assertFalse("ScrollView must not consume remote focus", focused instanceof ScrollView);
            assertFalse("HorizontalScrollView must not consume remote focus", focused instanceof HorizontalScrollView);
        });
    }

    private void assertFirstAppVisible() {
        awaitActivity("The first app tile must be visible again", activity -> {
            View first = appsRow(activity).getChildAt(0);
            Rect visible = new Rect();
            return first.getGlobalVisibleRect(visible)
                    && visible.width() >= first.getWidth() * 0.95f
                    && visible.height() >= first.getHeight() * 0.95f;
        });
    }

    private ViewGroup appsRow(MainActivity activity) {
        ViewGroup row = activity.findViewById(R.id.apps_row);
        assertNotNull("Apps row must exist", row);
        return row;
    }

    private interface ActivityCheck {
        boolean matches(MainActivity activity);
    }

    private void awaitActivity(String message, ActivityCheck check) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        AtomicBoolean matched = new AtomicBoolean();
        do {
            scenario.onActivity(activity -> matched.set(check.matches(activity)));
            if (matched.get()) return;
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        assertTrue(message, matched.get());
    }

    private void awaitPackage(boolean expectLauncher) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        do {
            String currentPackage = device.getCurrentPackageName();
            if (currentPackage != null && APP_PACKAGE.equals(currentPackage) == expectLauncher) return;
            SystemClock.sleep(100);
        } while (SystemClock.uptimeMillis() < deadline);
        assertTrue(expectLauncher ? "Back did not return to the launcher" : "OK did not open system settings", false);
    }

    private void screenshot(String name) {
        // Shared Pictures survive Gradle uninstalling the test apps at the end
        // of connectedDebugAndroidTest; app-specific external files do not.
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull("Could not capture focus screenshot", bitmap);
        android.content.ContentResolver resolver = InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name + ".png");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/C9KHome-focus");
        Uri image = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        assertNotNull("Could not create screenshot output", image);
        try (OutputStream output = resolver.openOutputStream(image)) {
            assertNotNull("Could not open screenshot output", output);
            assertTrue("Could not save screenshot", bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        } catch (IOException e) {
            throw new AssertionError("Could not save screenshot", e);
        } finally {
            bitmap.recycle();
        }
    }
}
