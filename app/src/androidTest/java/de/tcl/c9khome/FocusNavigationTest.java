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
import android.widget.EditText;

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
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.EnumMap;
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
    private static final long TIMEOUT_MS = 15000;
    private static final String APP_PACKAGE = "de.tcl.c9khome";
    private static final int[] TOP_BAR = {
            R.id.top_bar_search, R.id.top_bar_home, R.id.top_bar_live_tv,
            R.id.top_bar_movies, R.id.top_bar_series, R.id.top_bar_apps, R.id.top_bar_settings
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
        assertFocused(R.id.top_bar_home);
        key(KeyEvent.KEYCODE_DPAD_LEFT);
        assertFocused(R.id.top_bar_search);
        key(KeyEvent.KEYCODE_DPAD_LEFT);
        assertFocused(R.id.top_bar_search);
        for (int index = 0; index < TOP_BAR.length; index++) {
            if (index > 0) key(KeyEvent.KEYCODE_DPAD_RIGHT);
            assertFocused(TOP_BAR[index]);
            final int viewId = TOP_BAR[index];
            scenario.onActivity(activity -> {
                View item = activity.findViewById(viewId);
                assertTrue(item.isFocusable());
                assertTrue(item.isFocusableInTouchMode());
            });
            key(KeyEvent.KEYCODE_DPAD_UP);
            assertFocused(viewId);
            screenshot("top-bar-" + index);
        }
        key(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertFocused(R.id.top_bar_settings);
        key(KeyEvent.KEYCODE_DPAD_LEFT);
        assertFocused(R.id.top_bar_apps);
        for (int index = TOP_BAR.length - 3; index >= 0; index--) {
            key(KeyEvent.KEYCODE_DPAD_LEFT);
            assertFocused(TOP_BAR[index]);
        }
        key(KeyEvent.KEYCODE_DPAD_LEFT);
        assertFocused(R.id.top_bar_search);
    }

    @Test public void everyTopBarItemReturnsToTheCurrentFirstApp() {
        for (int id : TOP_BAR) {
            focusTop(id);
            key(KeyEvent.KEYCODE_DPAD_DOWN);
            assertFirstAppFocused();
            assertFirstAppVisible();
            key(KeyEvent.KEYCODE_DPAD_UP);
            assertFocused(R.id.top_bar_home);
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
        focusTop(R.id.top_bar_settings);
        assertFocused(R.id.top_bar_settings);
        screenshot("settings-after-scrolled-apps");
        key(KeyEvent.KEYCODE_DPAD_LEFT);
        assertFocused(R.id.top_bar_apps);
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        assertFirstAppFocused();
        assertFirstAppVisible();
        screenshot("first-app-restored");
    }

    @Test public void settingsOpensWithOkAndBackKeepsTheFocusFlowAfterOnStart() {
        key(KeyEvent.KEYCODE_DPAD_UP);
        assertFocused(R.id.top_bar_home);
        focusTop(R.id.top_bar_settings);
        assertFocused(R.id.top_bar_settings);

        key(KeyEvent.KEYCODE_DPAD_CENTER);
        awaitPackage(false);
        key(KeyEvent.KEYCODE_BACK);
        awaitPackage(true);
        assertFocused(R.id.top_bar_settings);
        screenshot("settings-returned-with-back");

        key(KeyEvent.KEYCODE_DPAD_LEFT);
        assertFocused(R.id.top_bar_apps);
        key(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertFocused(R.id.top_bar_settings);
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        assertFirstAppFocused();
        assertFirstAppVisible();
        key(KeyEvent.KEYCODE_DPAD_UP);
        assertFocused(R.id.top_bar_home);
    }

    @Test public void allInternalTabsOpenWithOkAndKeepTheirSelectionWhileFocusMoves() {
        int[] pages = {R.id.page_search, R.id.page_home, R.id.page_live_tv,
                R.id.page_movies, R.id.page_series, R.id.page_apps};
        for (int index = 0; index < pages.length; index++) {
            activateTab(TOP_BAR[index]);
            assertPageShown(pages[index]);
            assertActiveTab(TOP_BAR[index]);
            assertFocused(TOP_BAR[index]);
            screenshot("page-" + index);
            if (index < pages.length - 1) {
                key(KeyEvent.KEYCODE_DPAD_RIGHT);
                assertFocused(TOP_BAR[index + 1]);
                assertActiveTab(TOP_BAR[index]);
                key(KeyEvent.KEYCODE_DPAD_LEFT);
            }
            key(KeyEvent.KEYCODE_DPAD_DOWN);
            awaitActivity("Down must enter the page rather than a scroll container", activity ->
                    activity.getCurrentFocus() != null && !isTopId(activity.getCurrentFocus().getId()));
            assertNoScrollContainerFocused();
            assertActiveTab(TOP_BAR[index]);
            focusTop(TOP_BAR[index]);
        }
        assertSameActivity();
    }

    @Test public void appsPageMergesAllActivitiesOfEachPackageAndIncludesBothLauncherCategories() {
        activateTab(R.id.top_bar_apps);
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
        enterInstalledGrid();
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
        focusTop(R.id.top_bar_apps);
        assertSameActivity();
    }

    @Test public void aMergedPackageCanBeStartedWithOkAndBackReturnsToAppsPage() {
        activateTab(R.id.top_bar_apps);
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
        for (int id : new int[]{R.id.top_bar_search, R.id.top_bar_live_tv,
                R.id.top_bar_movies, R.id.top_bar_series, R.id.top_bar_apps}) {
            activateTab(id);
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
        assertTrue(APP_PACKAGE.equals(device.getCurrentPackageName()));
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
        activateTab(R.id.top_bar_movies);
        assertPageShown(R.id.page_movies);
        focusTop(R.id.top_bar_settings);
        assertActiveTab(R.id.top_bar_movies);
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        awaitPackage(false);
        key(KeyEvent.KEYCODE_BACK);
        awaitPackage(true);
        assertPageShown(R.id.page_movies);
        assertActiveTab(R.id.top_bar_movies);
        assertFocused(R.id.top_bar_settings);
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        assertNoScrollContainerFocused();
        focusTop(R.id.top_bar_movies);
        key(KeyEvent.KEYCODE_BACK);
        assertPageShown(R.id.page_home);
        assertSameActivity();
    }

    @Test public void longOkAddsAndRemovesFavoritesImmediatelyOnBothPages() {
        String pkg = fixturePackage();
        activateTab(R.id.top_bar_apps);
        focusGridPackage(pkg);
        longOk();
        awaitDialog("Von Startseite entfernen");
        chooseDialogItem(0);
        assertFalse(new FavoriteApps(targetContext()).contains(pkg));
        goHome();
        assertHomePackageAbsent(pkg);

        activateTab(R.id.top_bar_apps);
        focusGridPackage(pkg);
        longOk();
        awaitDialog("Zur Startseite hinzufügen");
        chooseDialogItem(0);
        assertTrue(new FavoriteApps(targetContext()).contains(pkg));
        goHome();
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
        // Re-selecting Home must not detach the existing favorites controller.
        focusTop(R.id.top_bar_home);
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        assertFirstAppFocused();
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
            assertNotNull("The original settings gear stays available", activity.findViewById(R.id.top_bar_settings));
            assertEquals("Home must not add another dedicated settings control", 0,
                    countDedicatedSettings(activity.findViewById(R.id.page_home)));
            View home = activity.findViewById(R.id.page_home);
            for (String title : new String[]{"Meine Apps", "Weiter anschauen", "Beliebte Filme",
                    "Beliebte Serien", "Neuerscheinungen", "Empfehlungen aus YouTube", "Nach Genre suchen", "HDMI & Eingänge"}) {
                assertTrue("Missing home section: " + title, hasText(home, title));
            }
        });
        for (int id : new int[]{R.id.top_bar_movies, R.id.top_bar_series}) {
            activateTab(id);
            scenario.onActivity(activity -> {
                View page = activity.findViewById(id == R.id.top_bar_movies ? R.id.page_movies : R.id.page_series);
                assertFalse("Installed app entries must not be presented as movies or episodes", containsAppPackageTile(page));
                assertTrue("Movie/series page must contain genuine empty states or real media", page instanceof ViewGroup
                        && ((ViewGroup) page).getChildCount() > 2);
            });
        }
    }

    @Test public void mediaSearchDoesNotSearchInstalledAppsAndGenresReturnToSearch() {
        activateTab(R.id.top_bar_search);
        assertPageShown(R.id.page_search);
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        assertFocused(R.id.media_search_input);
        // Text is test data, navigation/submit still use the remote keys.
        scenario.onActivity(activity -> ((EditText) activity.findViewById(R.id.media_search_input))
                .setText("Focus fixture"));
        key(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertFocused(R.id.media_search_submit);
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        scenario.onActivity(activity -> {
            View results = activity.findViewById(R.id.media_search_results);
            assertNotNull(results);
            assertTrue("Media search must show an honest empty state for app-name queries",
                    hasTextContaining(results, "Keine Ergebnisse in den verfügbaren Mediendaten"));
            assertFalse("Media/person search must not return installed-app cards", containsAppPackageTile(results));
        });
        focusTop(R.id.top_bar_search);
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        boolean found = false;
        for (int step = 0; step < 10; step++) {
            AtomicBoolean genre = new AtomicBoolean();
            scenario.onActivity(activity -> genre.set(activity.getCurrentFocus() != null
                    && "genre:Action".equals(activity.getCurrentFocus().getTag())));
            if (genre.get()) { found = true; break; }
            key(KeyEvent.KEYCODE_DPAD_DOWN);
        }
        assertTrue("The Action genre must be D-Pad reachable", found);
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        assertPageShown(R.id.page_genre);
        scenario.onActivity(activity -> assertTrue(hasText(activity.findViewById(R.id.page_genre), "Action")));
        key(KeyEvent.KEYCODE_BACK);
        assertPageShown(R.id.page_search);
        assertSameActivity();
    }

    @Test public void appSearchFiltersInstalledPackagesAndOffersRealPlayStoreSearch() {
        activateTab(R.id.top_bar_apps);
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        assertFocused(R.id.apps_search_input);
        String fixtureLabel = null;
        for (LaunchableApps.Entry entry : LaunchableApps.load(targetContext())) {
            if (fixturePackage().equals(entry.packageName)) fixtureLabel = entry.label;
        }
        assertNotNull(fixtureLabel);
        final String query = fixtureLabel;
        scenario.onActivity(activity -> ((EditText) activity.findViewById(R.id.apps_search_input)).setText(query));
        key(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertFocused(R.id.apps_search_submit);
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        awaitActivity("App search must filter the installed package grid", activity -> {
            GridLayout grid = appsGrid(activity);
            return grid.getChildCount() == 1 && fixturePackage().equals(grid.getChildAt(0).getTag());
        });
        scenario.onActivity(activity -> {
            assertEquals("Query must remain visible after submit", query,
                    ((EditText) activity.findViewById(R.id.apps_search_input)).getText().toString());
            assertNotNull("Non-installed app queries must have a real store search action",
                    activity.findViewById(R.id.store_search));
        });
        key(KeyEvent.KEYCODE_DPAD_LEFT);
        assertFocused(R.id.apps_search_input);
        scenario.onActivity(activity -> ((EditText) activity.findViewById(R.id.apps_search_input))
                .setText("__not_installed_v05_fixture__"));
        key(KeyEvent.KEYCODE_DPAD_RIGHT);
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        awaitActivity("Unknown apps must not create invented installed entries", activity ->
                appsGrid(activity).getChildCount() == 0);
        screenshot("app-search-empty");
        assertSameActivity();
    }

    @Test public void okOpensTheTvKeyboardAndBackReturnsToMediaSearch() {
        activateTab(R.id.top_bar_search);
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        assertFocused(R.id.media_search_input);
        String configured = android.provider.Settings.Secure.getString(targetContext().getContentResolver(),
                android.provider.Settings.Secure.DEFAULT_INPUT_METHOD);
        assertNotNull("The TV emulator must provide a keyboard", configured);
        String keyboardPackage = configured.split("/")[0];
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (!device.hasObject(By.pkg(keyboardPackage)) && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(100);
        }
        assertTrue("OK must open the native TV keyboard without touch", device.hasObject(By.pkg(keyboardPackage)));
        key(KeyEvent.KEYCODE_BACK);
        assertPageShown(R.id.page_search);
        assertFocused(R.id.media_search_input);
        key(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertFocused(R.id.media_search_submit);
    }

    @Test public void entertainmentDiscoveryUsesInstalledAppCategoryBeyondKnownMappings() {
        List<EntertainmentAppRepository.Provider> providers = new EntertainmentAppRepository(targetContext())
                .detect(LaunchableApps.load(targetContext()));
        boolean fixtureRecognized = false;
        Set<String> packages = new HashSet<>();
        for (EntertainmentAppRepository.Provider provider : providers) {
            assertTrue("Provider packages must be unique", packages.add(provider.packageName));
            assertNotNull("Every provider must have an installed launch entry", provider.app);
            if (fixturePackage().equals(provider.packageName)) fixtureRecognized = true;
        }
        assertTrue("A video-category fixture must be recognized without a fixed package mapping", fixtureRecognized);
    }

    @Test public void mediaSourcesRespectInstalledProvidersTypesAndPackageRemoval() {
        LaunchableApps.Entry fixture = null;
        for (LaunchableApps.Entry entry : LaunchableApps.load(targetContext())) {
            if (fixturePackage().equals(entry.packageName)) fixture = entry;
        }
        assertNotNull(fixture);
        EntertainmentAppRepository.Provider provider = new EntertainmentAppRepository.Provider(
                fixturePackage(), "Test provider", false, fixture);
        MediaItem movie = new MediaItem.Builder("test-movie", "Source test movie", MediaItem.Kind.MOVIE,
                fixturePackage()).genres(Collections.singletonList("Action")).build();
        MediaItem episode = new MediaItem.Builder("test-episode", "Source test episode", MediaItem.Kind.EPISODE,
                fixturePackage()).seasonNumber(1).episodeNumber(2).progress(0.5f).build();
        MediaItem person = new MediaItem.Builder("test-person", "Source test person", MediaItem.Kind.PERSON,
                fixturePackage()).personRole("Regisseur").build();
        MediaItem notInstalled = new MediaItem.Builder("not-installed", "Never admitted", MediaItem.Kind.MOVIE,
                "test.provider.not.installed").build();
        MediaRepository repository = new MediaRepository(targetContext());
        repository.addSource((context, installed) -> {
            Map<MediaRepository.Section, List<MediaItem>> records = new EnumMap<>(MediaRepository.Section.class);
            records.put(MediaRepository.Section.CONTINUE, Collections.singletonList(episode));
            records.put(MediaRepository.Section.POPULAR_MOVIES, Arrays.asList(movie, notInstalled));
            records.put(MediaRepository.Section.PERSONAL, Collections.singletonList(person));
            return records;
        });
        repository.refresh(Collections.singletonList(provider));
        assertEquals(1, repository.get(MediaRepository.Section.POPULAR_MOVIES, MediaItem.Kind.MOVIE, null, "Action").size());
        assertTrue("Episode progress must not invent popular-series rankings",
                repository.get(MediaRepository.Section.POPULAR_SERIES, null, null, null).isEmpty());
        assertEquals(1, repository.search("Regisseur").size());
        assertEquals(MediaItem.Kind.PERSON, repository.search("Regisseur").get(0).kind);
        assertTrue(repository.search("Never admitted").isEmpty());
        assertTrue("App package names are not media records", repository.search(fixturePackage()).isEmpty());
        repository.toggleSaved(movie);
        assertTrue(repository.isSaved(movie));
        repository.purgePackage(fixturePackage());
        assertTrue("Removing a provider purges cached content", repository.search("Source test").isEmpty());
        assertFalse("Provider removal must purge the provider's saved links", repository.isSaved(movie));
    }

    @Test public void mediaCacheHasAFixedUpperBound() {
        LaunchableApps.Entry fixture = null;
        for (LaunchableApps.Entry entry : LaunchableApps.load(targetContext())) {
            if (fixturePackage().equals(entry.packageName)) fixture = entry;
        }
        assertNotNull(fixture);
        EntertainmentAppRepository.Provider provider = new EntertainmentAppRepository.Provider(
                fixturePackage(), "Test provider", false, fixture);
        MediaRepository repository = new MediaRepository(targetContext());
        repository.addSource((context, providers) -> {
            List<MediaItem> many = new ArrayList<>();
            for (int index = 0; index < 150; index++) many.add(new MediaItem.Builder("item-" + index,
                    "Cache fixture " + index, MediaItem.Kind.MOVIE, fixturePackage()).build());
            Map<MediaRepository.Section, List<MediaItem>> records = new EnumMap<>(MediaRepository.Section.class);
            records.put(MediaRepository.Section.POPULAR_MOVIES, many);
            return records;
        });
        repository.refresh(Collections.singletonList(provider));
        int count = repository.get(MediaRepository.Section.POPULAR_MOVIES, MediaItem.Kind.MOVIE, null, null).size();
        assertTrue("Admit real-source records", count > 0);
        assertTrue("Artwork/media cache must remain bounded", count <= 120);
    }

    private int countDedicatedSettings(View view) {
        if (view == null) return 0;
        int count = view.isFocusable() && "Einstellungen".equals(String.valueOf(view.getContentDescription())) ? 1 : 0;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) count += countDedicatedSettings(group.getChildAt(i));
        }
        return count;
    }

    private boolean containsAppPackageTile(View view) {
        if (view == null) return false;
        Object tag = view.getTag();
        if (view.isFocusable() && tag instanceof String) {
            for (LaunchableApps.Entry entry : LaunchableApps.load(targetContext())) {
                if (entry.packageName.equals(tag)) return true;
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (containsAppPackageTile(group.getChildAt(i))) return true;
        }
        return false;
    }

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
        goHome();
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
        enterInstalledGrid();
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

    private boolean isTopId(int id) {
        for (int top : TOP_BAR) if (top == id) return true;
        return false;
    }

    private void goHome() {
        for (int tries = 0; tries < 12; tries++) {
            AtomicBoolean home = new AtomicBoolean();
            scenario.onActivity(activity -> {
                View page = activity.findViewById(R.id.page_home);
                home.set(page != null && page.isShown());
            });
            if (home.get()) {
                key(KeyEvent.KEYCODE_BACK);
                assertFocused(R.id.top_bar_home);
                return;
            }
            key(KeyEvent.KEYCODE_BACK);
        }
        assertPageShown(R.id.page_home);
    }

    private void focusTop(int id) {
        AtomicInteger focusedId = new AtomicInteger();
        for (int tries = 0; tries < 30; tries++) {
            scenario.onActivity(activity -> focusedId.set(activity.getCurrentFocus() == null
                    ? View.NO_ID : activity.getCurrentFocus().getId()));
            if (isTopId(focusedId.get())) break;
            key(KeyEvent.KEYCODE_DPAD_UP);
        }
        int from = -1, to = -1;
        for (int i = 0; i < TOP_BAR.length; i++) {
            if (TOP_BAR[i] == focusedId.get()) from = i;
            if (TOP_BAR[i] == id) to = i;
        }
        assertTrue("D-Pad Up must reach the top bar", from >= 0);
        assertTrue("Unknown top-bar target", to >= 0);
        for (int i = from; i < to; i++) key(KeyEvent.KEYCODE_DPAD_RIGHT);
        for (int i = from; i > to; i--) key(KeyEvent.KEYCODE_DPAD_LEFT);
        assertFocused(id);
    }

    private void activateTab(int id) {
        goHome();
        focusTop(id);
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        assertActiveTab(id);
    }

    private void enterInstalledGrid() {
        focusTop(R.id.top_bar_apps);
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        for (int tries = 0; tries < 30; tries++) {
            AtomicBoolean inGrid = new AtomicBoolean();
            scenario.onActivity(activity -> {
                View focused = activity.getCurrentFocus();
                inGrid.set(focused != null && focused.getParent() == appsGrid(activity));
            });
            if (inGrid.get()) return;
            key(KeyEvent.KEYCODE_DPAD_DOWN);
        }
        assertTrue("All installed apps must be reachable using D-Pad Down", false);
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
