package de.tcl.c9khome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
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
import android.widget.GridLayout;
import android.widget.ScrollView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;
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
    private MainActivity originalActivity;

    @Before public void launchLauncher() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        scenario = ActivityScenario.launch(MainActivity.class);
        scenario.onActivity(activity -> originalActivity = activity);
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

    @Test public void allInternalTabsOpenWithOkAndKeepTheirSelectionWhileFocusMoves() {
        int[] pages = {R.id.page_home, R.id.page_apps, R.id.page_movies,
                R.id.page_series, R.id.page_live_tv};
        key(KeyEvent.KEYCODE_DPAD_UP);
        for (int index = 0; index < pages.length; index++) {
            if (index > 0) key(KeyEvent.KEYCODE_DPAD_RIGHT);
            assertFocused(TOP_BAR[index]);
            key(KeyEvent.KEYCODE_DPAD_CENTER);
            assertPageShown(pages[index]);
            assertActiveTab(TOP_BAR[index]);
            assertFocused(TOP_BAR[index]);
            screenshot("page-" + index);

            if (index < pages.length - 1) {
                key(KeyEvent.KEYCODE_DPAD_RIGHT);
                assertFocused(TOP_BAR[index + 1]);
                assertActiveTab(TOP_BAR[index]);
                key(KeyEvent.KEYCODE_DPAD_LEFT);
                assertFocused(TOP_BAR[index]);
            }
            key(KeyEvent.KEYCODE_DPAD_DOWN);
            final int pageIndex = index;
            awaitActivity("Down must enter the current page", activity -> {
                View focused = activity.getCurrentFocus();
                if (pageIndex == 0) return focused == appsRow(activity).getChildAt(0);
                if (pageIndex == 1) return focused == appsGrid(activity).getChildAt(0);
                int target = pageIndex == 4 ? R.id.live_tv_open : R.id.page_back_home;
                return focused == activity.findViewById(target);
            });
            assertActiveTab(TOP_BAR[index]);
            key(KeyEvent.KEYCODE_DPAD_UP);
            assertFocused(TOP_BAR[index]);
        }
        assertSameActivity();
    }

    @Test public void appsPageContainsEveryTestEntryIncludingMobileAppsAndHasNoTwelveAppLimit() {
        activateFromHome(1);
        assertPageShown(R.id.page_apps);
        scenario.onActivity(activity -> {
            GridLayout grid = appsGrid(activity);
            assertTrue("Apps page must not use the twelve-app home limit", grid.getChildCount() > 12);
            Set<String> components = new HashSet<>();
            Set<String> labels = new HashSet<>();
            for (int index = 0; index < grid.getChildCount(); index++) {
                View tile = grid.getChildAt(index);
                assertTrue("Every app entry must be focusable", tile.isFocusable());
                assertNotNull("Every tile must identify its launch component", tile.getTag());
                assertTrue("The same component must appear only once", components.add(tile.getTag().toString()));
                assertNotNull("Every tile must have an app label", tile.getContentDescription());
                labels.add(tile.getContentDescription().toString());
            }
            for (int index = 1; index <= 12; index++) {
                assertTrue("Missing installed TV app fixture " + index,
                        labels.contains(String.format(java.util.Locale.ROOT, "Focus fixture %02d", index)));
            }
            assertTrue("Apps with a regular LAUNCHER entry must also be included", labels.contains("Mobile fixture"));
            assertTrue("Use a complete app grid", grid.getColumnCount() >= 4);
        });
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        assertGridItemFocused(0);
        AtomicInteger columns = new AtomicInteger();
        scenario.onActivity(activity -> columns.set(appsGrid(activity).getColumnCount()));
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        assertGridItemFocused(columns.get());
        key(KeyEvent.KEYCODE_DPAD_UP);
        assertGridItemFocused(0);
        key(KeyEvent.KEYCODE_DPAD_UP);
        assertFocused(R.id.top_bar_apps);
        assertSameActivity();
    }

    @Test public void aMobileAppCanBeStartedWithOkAndBackReturnsToAppsPage() {
        activateFromHome(1);
        AtomicInteger mobileIndex = new AtomicInteger(-1);
        AtomicInteger columns = new AtomicInteger();
        scenario.onActivity(activity -> {
            GridLayout grid = appsGrid(activity);
            columns.set(grid.getColumnCount());
            for (int index = 0; index < grid.getChildCount(); index++) {
                if ("Mobile fixture".contentEquals(grid.getChildAt(index).getContentDescription())) {
                    mobileIndex.set(index);
                    break;
                }
            }
        });
        assertTrue("Mobile-only fixture must be available", mobileIndex.get() >= 0);
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        for (int row = 0; row < mobileIndex.get() / columns.get(); row++) key(KeyEvent.KEYCODE_DPAD_DOWN);
        for (int column = 0; column < mobileIndex.get() % columns.get(); column++) key(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertGridItemFocused(mobileIndex.get());
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        awaitPackage(false);
        assertTrue("OK must start the selected fixture activity", InstrumentationRegistry.getInstrumentation()
                .getContext().getPackageName().equals(device.getCurrentPackageName()));
        key(KeyEvent.KEYCODE_BACK);
        awaitPackage(true);
        assertPageShown(R.id.page_apps);
        assertActiveTab(R.id.top_bar_apps);
        assertSameActivity();
    }

    @Test public void backFromEveryInternalPageReturnsHomeWithoutClosingTheLauncher() {
        for (int index = 1; index <= 4; index++) {
            activateFromHome(index);
            key(KeyEvent.KEYCODE_DPAD_DOWN);
            key(KeyEvent.KEYCODE_BACK);
            assertPageShown(R.id.page_home);
            assertActiveTab(R.id.top_bar_home);
            assertFocused(R.id.top_bar_home);
            assertSameActivity();
        }
        key(KeyEvent.KEYCODE_BACK);
        assertPageShown(R.id.page_home);
        assertFocused(R.id.top_bar_home);
        assertTrue("Back on Home must keep C9KHome open", APP_PACKAGE.equals(device.getCurrentPackageName()));
    }

    @Test public void homeAllAppsShortcutOpensTheInternalFullAppPage() {
        AtomicInteger lastIndex = new AtomicInteger();
        scenario.onActivity(activity -> lastIndex.set(appsRow(activity).getChildCount() - 1));
        for (int index = 0; index < lastIndex.get(); index++) key(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertFocused(R.id.home_all_apps);
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        assertPageShown(R.id.page_apps);
        assertActiveTab(R.id.top_bar_apps);
        assertTrue("The shortcut must stay inside C9KHome", APP_PACKAGE.equals(device.getCurrentPackageName()));
        assertSameActivity();
    }

    @Test public void settingsAndBackPreserveTheActiveInternalPage() {
        activateFromHome(2);
        assertPageShown(R.id.page_movies);
        for (int index = 0; index < 3; index++) key(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertFocused(R.id.top_bar_settings);
        assertActiveTab(R.id.top_bar_movies);
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        awaitPackage(false);
        key(KeyEvent.KEYCODE_BACK);
        awaitPackage(true);
        assertPageShown(R.id.page_movies);
        assertActiveTab(R.id.top_bar_movies);
        assertFocused(R.id.top_bar_settings);
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        assertFocused(R.id.page_back_home);
        key(KeyEvent.KEYCODE_DPAD_UP);
        assertFocused(R.id.top_bar_movies);
        key(KeyEvent.KEYCODE_BACK);
        assertPageShown(R.id.page_home);
        assertSameActivity();
    }

    private void activateFromHome(int topIndex) {
        key(KeyEvent.KEYCODE_BACK);
        assertPageShown(R.id.page_home);
        assertFocused(R.id.top_bar_home);
        for (int index = 0; index < topIndex; index++) key(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertFocused(TOP_BAR[topIndex]);
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        assertActiveTab(TOP_BAR[topIndex]);
    }

    private void assertPageShown(int pageId) {
        awaitActivity("Expected internal page " + pageId, activity -> {
            View page = activity.findViewById(pageId);
            return page != null && page.isShown();
        });
        assertSameActivity();
    }

    private void assertActiveTab(int activeId) {
        scenario.onActivity(activity -> {
            for (int index = 0; index < TOP_BAR.length; index++) {
                View tab = activity.findViewById(TOP_BAR[index]);
                assertTrue("Only the current page must keep its menu selection", tab.isSelected() == (TOP_BAR[index] == activeId));
            }
        });
    }

    private void assertSameActivity() {
        scenario.onActivity(activity -> assertSame("Changing pages must not restart C9KHome", originalActivity, activity));
    }

    private GridLayout appsGrid(MainActivity activity) {
        GridLayout grid = activity.findViewById(R.id.all_apps_grid);
        assertNotNull("Complete app grid must exist", grid);
        return grid;
    }

    private void assertGridItemFocused(int index) {
        awaitActivity("Expected focus on app grid entry " + index, activity ->
                activity.getCurrentFocus() == appsGrid(activity).getChildAt(index));
        assertNoScrollContainerFocused();
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
