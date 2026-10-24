package de.tcl.c9khome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.view.KeyEvent;
import android.view.ViewConfiguration;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.GridLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.By;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.text.Collator;
import java.util.Locale;
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
        // Test setup seeds real packages; UI navigation below uses keys only.
        List<String> favorites = new ArrayList<>();
        for (LaunchableApps.Entry entry : LaunchableApps.load(targetContext())) {
            if (!favorites.contains(entry.packageName)) favorites.add(entry.packageName);
        }
        new FavoriteApps(targetContext()).setPackages(favorites);
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
        assertTrue("The installed apps and All Apps shortcut must be reachable", appCount.get() > 1);

        int lastIndex = appCount.get() - 1;
        for (int index = 1; index <= lastIndex; index++) {
            key(KeyEvent.KEYCODE_DPAD_RIGHT);
            final int expectedIndex = index;
            awaitActivity("App focus did not move to tile " + index,
                    activity -> activity.getCurrentFocus() == appsRow(activity).getChildAt(expectedIndex));
        }
        awaitActivity("A wide favorites row must scroll to reveal its last tile", activity -> {
            ViewGroup row = appsRow(activity);
            HorizontalScrollView scroll = (HorizontalScrollView) row.getParent();
            return row.getWidth() <= scroll.getWidth() || scroll.getScrollX() > 0;
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

    @Test public void appsPageMergesAllActivitiesOfEachPackageAndIncludesBothLauncherCategories() {
        activateFromHome(1);
        assertPageShown(R.id.page_apps);
        Set<String> expected = new LinkedHashSet<>();
        PackageManager pm = targetContext().getPackageManager();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo info : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                if (info.activityInfo == null || !info.activityInfo.exported || !info.activityInfo.enabled
                        || !info.activityInfo.applicationInfo.enabled) continue;
                String pkg = info.activityInfo.packageName;
                if (APP_PACKAGE.equals(pkg)) continue;
                if (info.activityInfo.permission != null && pm.checkPermission(info.activityInfo.permission,
                        APP_PACKAGE) != PackageManager.PERMISSION_GRANTED) continue;
                if (pm.getLeanbackLaunchIntentForPackage(pkg) != null || pm.getLaunchIntentForPackage(pkg) != null) {
                    expected.add(pkg);
                }
            }
        }
        scenario.onActivity(activity -> {
            GridLayout grid = appsGrid(activity);
            Set<String> packages = new HashSet<>();
            Collator collator = Collator.getInstance(Locale.GERMANY);
            String previousLabel = null;
            for (int index = 0; index < grid.getChildCount(); index++) {
                View tile = grid.getChildAt(index);
                assertTrue("Every app entry must be focusable", tile.isFocusable());
                assertNotNull("Every tile must identify its package", tile.getTag());
                assertTrue("A package with several launcher activities must appear once",
                        packages.add(tile.getTag().toString()));
                String label = tile.getContentDescription().toString();
                if (previousLabel != null) assertTrue("Full app list must be alphabetical",
                        collator.compare(previousLabel, label) <= 0);
                previousLabel = label;
            }
            assertEquals("Include all startable packages from both launcher categories", expected, packages);
            assertFalse("C9KHome must not list itself", packages.contains(APP_PACKAGE));
            assertTrue("All fixture aliases must merge into their single package",
                    packages.contains(fixturePackage()));
            assertTrue("Use a complete app grid", grid.getColumnCount() >= 4);
        });
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        assertGridItemFocused(0);
        AtomicInteger columns = new AtomicInteger();
        AtomicInteger count = new AtomicInteger();
        scenario.onActivity(activity -> {
            columns.set(appsGrid(activity).getColumnCount());
            count.set(appsGrid(activity).getChildCount());
        });
        if (count.get() > columns.get()) {
            key(KeyEvent.KEYCODE_DPAD_DOWN);
            assertGridItemFocused(columns.get());
            key(KeyEvent.KEYCODE_DPAD_UP);
            assertGridItemFocused(0);
        }
        key(KeyEvent.KEYCODE_DPAD_UP);
        assertFocused(R.id.top_bar_apps);
        assertSameActivity();
    }

    @Test public void aMergedPackageCanBeStartedWithOkAndBackReturnsToAppsPage() {
        activateFromHome(1);
        focusGridPackage(fixturePackage());
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        awaitPackage(false);
        assertEquals("OK must start the package's launcher entry point", fixturePackage(), device.getCurrentPackageName());
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

    @Test public void longOkAddsAndRemovesFavoritesImmediatelyOnBothPages() {
        String pkg = fixturePackage();
        activateFromHome(1);
        focusGridPackage(pkg);
        longOk();
        awaitDialog("Von Startseite entfernen");
        chooseDialogItem(0);
        assertFalse(new FavoriteApps(targetContext()).contains(pkg));
        key(KeyEvent.KEYCODE_BACK);
        assertHomePackageAbsent(pkg);

        activateFromHome(1);
        focusGridPackage(pkg);
        longOk();
        awaitDialog("Zur Startseite hinzufügen");
        chooseDialogItem(0);
        assertTrue(new FavoriteApps(targetContext()).contains(pkg));
        key(KeyEvent.KEYCODE_BACK);
        assertHomePackagePresent(pkg);

        focusHomePackage(pkg);
        longOk();
        awaitDialog("Verschieben");
        awaitDialog("Von Startseite entfernen");
        chooseDialogItem(1);
        assertFalse(new FavoriteApps(targetContext()).contains(pkg));
        assertHomePackageAbsent(pkg);
        assertTrue("Long OK must open a menu rather than launch the app", APP_PACKAGE.equals(device.getCurrentPackageName()));
    }

    @Test public void moveModeSavesOrderWithOkAndRetainsItAfterRecreation() {
        List<String> before = new FavoriteApps(targetContext()).getPackages();
        assertTrue("At least two installed packages are needed to reorder", before.size() >= 2);
        String moving = before.get(0);
        longOk();
        awaitDialog("Verschieben");
        chooseDialogItem(0);
        assertMovingPackage(moving);
        screenshot("favorite-moving");
        key(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertMovingPackage(moving);
        assertHomePackageAt(moving, 1);
        assertEquals("Reorder is only a draft until OK", before, new FavoriteApps(targetContext()).getPackages());
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        List<String> reordered = new ArrayList<>(before);
        java.util.Collections.swap(reordered, 0, 1);
        assertEquals(reordered, new FavoriteApps(targetContext()).getPackages());
        assertNotMoving();
        scenario.recreate();
        awaitActivity("Saved favorite order must survive activity recreation", activity ->
                homePackages(activity).equals(reordered));
        assertEquals(reordered, new FavoriteApps(targetContext()).getPackages());
    }

    @Test public void backCancelsMoveAndRestoresTheOriginalOrder() {
        List<String> before = new FavoriteApps(targetContext()).getPackages();
        String moving = before.get(0);
        longOk();
        awaitDialog("Verschieben");
        chooseDialogItem(0);
        assertMovingPackage(moving);
        key(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertHomePackageAt(moving, 1);
        key(KeyEvent.KEYCODE_BACK);
        assertNotMoving();
        scenario.onActivity(activity -> assertEquals(before, homePackages(activity)));
        assertEquals("Back must discard the draft order", before, new FavoriteApps(targetContext()).getPackages());
        assertPageShown(R.id.page_home);
        assertSameActivity();
    }

    @Test public void favoritesPersistEmptyListsAndUnlimitedOrderedPackagesWithoutDuplicates() {
        FavoriteApps favorites = new FavoriteApps(targetContext());
        favorites.setPackages(new ArrayList<>());
        favorites.initializeIfMissing(LaunchableApps.load(targetContext()));
        assertTrue("An intentionally empty list must not seed defaults again", favorites.getPackages().isEmpty());
        scenario.recreate();
        awaitActivity("Empty favorites show only the All Apps shortcut", activity ->
                homePackages(activity).isEmpty() && activity.findViewById(R.id.home_all_apps) != null);
        assertTrue(new FavoriteApps(targetContext()).getPackages().isEmpty());

        List<String> many = new ArrayList<>();
        for (int i = 0; i < 40; i++) many.add("test.favorite.package" + i);
        favorites.setPackages(many);
        favorites.add(many.get(0));
        assertEquals("No twelve-app or other artificial favorites limit", many,
                new FavoriteApps(targetContext()).getPackages());
        favorites.remove(many.get(7));
        many.remove(7);
        assertEquals("Removal must preserve the order of the remaining favorites", many,
                new FavoriteApps(targetContext()).getPackages());
    }

    @Test public void homeHasNoFixedSettingsTileAndMediaPagesShowNeutralInformation() {
        scenario.onActivity(activity -> {
            assertFalse("The lower fixed Settings tile must be removed", hasText(activity.findViewById(R.id.system_row), "Einstellungen"));
            assertTrue("Play Store stays available", hasText(activity.findViewById(R.id.system_row), "Google Play Store"));
            assertTrue("TCL inputs stay available", hasText(activity.findViewById(R.id.system_row), "TCL Eingänge"));
        });
        for (int index = 2; index <= 3; index++) {
            activateFromHome(index);
            final int pageId = indexPageId(index);
            scenario.onActivity(activity -> {
                View page = activity.findViewById(pageId);
                assertTrue("Media pages must explain that content arrives later",
                        hasTextContaining(page, "Medieninhalte werden später integriert"));
                assertFalse("No installed app is presented as media", containsAppTile(page));
            });
        }
    }

    private int indexPageId(int index) { return index == 2 ? R.id.page_movies : R.id.page_series; }

    private boolean hasText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (hasText(group.getChildAt(i), text)) return true;
        }
        return false;
    }

    private boolean hasTextContaining(View view, String text) {
        if (view instanceof TextView && ((TextView) view).getText().toString().contains(text)) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (hasTextContaining(group.getChildAt(i), text)) return true;
        }
        return false;
    }

    private boolean containsAppTile(View view) {
        if (view.getTag() instanceof String && view.isFocusable()) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (containsAppTile(group.getChildAt(i))) return true;
        }
        return false;
    }

    private Context targetContext() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
    private String fixturePackage() { return InstrumentationRegistry.getInstrumentation().getContext().getPackageName(); }

    private List<String> homePackages(MainActivity activity) {
        List<String> packages = new ArrayList<>();
        ViewGroup row = appsRow(activity);
        for (int i = 0; i < row.getChildCount(); i++) {
            Object tag = row.getChildAt(i).getTag();
            if (tag instanceof String) packages.add((String) tag);
        }
        return packages;
    }

    private void assertHomePackagePresent(String pkg) {
        awaitActivity("Favorite must appear on Home immediately", activity -> homePackages(activity).contains(pkg));
    }

    private void assertHomePackageAbsent(String pkg) {
        awaitActivity("Removed favorite must disappear from Home immediately", activity -> !homePackages(activity).contains(pkg));
    }

    private void assertHomePackageAt(String pkg, int index) {
        awaitActivity("Expected favorite at position " + index, activity ->
                pkg.equals(appsRow(activity).getChildAt(index).getTag()));
    }

    private void assertMovingPackage(String pkg) {
        awaitActivity("Moving favorite must stay focused and visibly activated", activity -> {
            View focused = activity.getCurrentFocus();
            return focused != null && pkg.equals(focused.getTag()) && focused.isActivated();
        });
    }

    private void assertNotMoving() {
        awaitActivity("Moving highlight must end after OK or Back", activity -> {
            ViewGroup row = appsRow(activity);
            for (int i = 0; i < row.getChildCount(); i++) if (row.getChildAt(i).isActivated()) return false;
            return true;
        });
    }

    private void focusHomePackage(String pkg) {
        key(KeyEvent.KEYCODE_BACK);
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        AtomicInteger index = new AtomicInteger(-1);
        scenario.onActivity(activity -> index.set(homePackages(activity).indexOf(pkg)));
        assertTrue("Favorite package must exist", index.get() >= 0);
        for (int i = 0; i < index.get(); i++) key(KeyEvent.KEYCODE_DPAD_RIGHT);
        awaitActivity("Expected favorite package focus", activity -> pkg.equals(activity.getCurrentFocus().getTag()));
    }

    private void focusGridPackage(String pkg) {
        AtomicInteger index = new AtomicInteger(-1);
        AtomicInteger columns = new AtomicInteger();
        scenario.onActivity(activity -> {
            GridLayout grid = appsGrid(activity);
            columns.set(grid.getColumnCount());
            for (int i = 0; i < grid.getChildCount(); i++) if (pkg.equals(grid.getChildAt(i).getTag())) index.set(i);
        });
        assertTrue("Installed fixture package must exist", index.get() >= 0);
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        for (int r = 0; r < index.get() / columns.get(); r++) key(KeyEvent.KEYCODE_DPAD_DOWN);
        for (int c = 0; c < index.get() % columns.get(); c++) key(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertGridItemFocused(index.get());
    }

    private void longOk() {
        long downTime = SystemClock.uptimeMillis();
        InstrumentationRegistry.getInstrumentation().sendKeySync(
                new KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0));
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout() + 250L);
        InstrumentationRegistry.getInstrumentation().sendKeySync(
                new KeyEvent(downTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        device.waitForIdle();
    }

    private void awaitDialog(String label) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        do {
            if (device.hasObject(By.text(label))) return;
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        assertTrue("Expected long-OK menu action: " + label, false);
    }

    private void chooseDialogItem(int index) {
        // Clamp to the first menu item before moving to the desired item.
        key(KeyEvent.KEYCODE_DPAD_UP);
        key(KeyEvent.KEYCODE_DPAD_UP);
        for (int i = 0; i < index; i++) key(KeyEvent.KEYCODE_DPAD_DOWN);
        key(KeyEvent.KEYCODE_DPAD_CENTER);
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
