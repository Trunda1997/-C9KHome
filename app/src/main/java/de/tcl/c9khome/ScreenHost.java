package de.tcl.c9khome;

import android.app.Activity;
import android.view.KeyEvent;
import android.view.View;
import java.util.List;

/** Small navigation/data boundary shared by the plain Java TV screens. */
public interface ScreenHost {
    Activity activity();
    TvUi ui();
    List<LaunchableApps.Entry> installedApps();
    List<EntertainmentAppRepository.Provider> providers();
    FavoriteApps favorites();
    MediaRepository media();
    LiveTvRepository liveTv();
    void navigate(ScreenId screen);
    void openGenre(String genre);
    void openMedia(MediaItem item);
    void addFavorites(ScreenView page);
    void addHdmi(ScreenView page);
    View appTile(LaunchableApps.Entry entry, boolean homeFavorite);
    void rebindFocus();
    void reveal(View target, int direction);
    boolean handleSpecialKey(View view, int keyCode, KeyEvent event);
    void refreshScreen();
    String providerFilter();
    void setProviderFilter(String packageName);
}
