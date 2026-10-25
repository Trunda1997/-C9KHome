package de.tcl.c9khome;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.util.List;
import java.util.function.Consumer;

/** Shared, lightweight TV design system and asynchronous artwork entry point. */
public final class TvUi implements AutoCloseable {
    public static final int TEXT = Color.rgb(243, 246, 252);
    public static final int MUTED = Color.rgb(161, 174, 197);
    private static final String[] GENRES = {"Action", "Abenteuer", "Animation", "Comedy", "Crime",
            "Dokumentation", "Drama", "Familie", "Fantasy", "Horror", "Mystery", "Reality",
            "Romantik", "Science-Fiction", "Thriller"};
    private final Activity activity;
    private final AsyncImageLoader images;

    public TvUi(Activity activity) {
        this.activity = activity;
        images = new AsyncImageLoader(activity);
    }

    public int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }

    public TextView text(String value, int sizeSp, boolean bold) {
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextColor(TEXT);
        text.setTextSize(sizeSp);
        text.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setLetterSpacing(0.008f);
        text.setLineSpacing(dp(2), 1f);
        return text;
    }

    public LinearLayout card() {
        LinearLayout card = new LinearLayout(activity);
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        card.setClickable(true);
        card.setClipToOutline(true);
        card.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        styleFocus(card);
        return card;
    }

    public void styleFocus(View view) {
        view.setOnFocusChangeListener((changed, focused) -> applyFocus(changed));
        applyFocus(view);
    }

    private void applyFocus(View view) {
        boolean focused = view.hasFocus();
        if (view instanceof EditText && !focused) ((EditText) view).setShowSoftInputOnFocus(false);
        boolean selected = view.isSelected();
        boolean moving = view.isActivated();
        boolean card = view instanceof LinearLayout;
        float scale = focused && card ? 1.035f : 1f;
        view.animate().cancel();
        view.animate().scaleX(scale).scaleY(scale).setDuration(110).start();
        int top = moving ? Color.rgb(104, 72, 18) : focused ? Color.rgb(48, 64, 89)
                : selected ? Color.argb(225, 24, 70, 105) : Color.argb(186, 24, 35, 55);
        int bottom = moving ? Color.rgb(67, 47, 18) : focused ? Color.rgb(34, 48, 70)
                : selected ? Color.argb(220, 19, 47, 75) : Color.argb(160, 14, 23, 39);
        GradientDrawable surface = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{top, bottom});
        surface.setCornerRadius(dp(card ? 22 : 14));
        surface.setStroke(dp(moving ? 3 : focused || selected ? 2 : 1), moving ? Color.rgb(255, 203, 71)
                : focused ? Color.WHITE : selected ? Color.rgb(96, 195, 246) : Color.argb(55, 139, 165, 204));
        view.setBackground(surface);
        view.setElevation(dp(focused ? 9 : 2));
        if (view instanceof TextView && !(view instanceof EditText)) {
            ((TextView) view).setTextColor(selected && !focused ? Color.rgb(148, 220, 255) : TEXT);
        }
    }

    public void heading(LinearLayout parent, String title) {
        if (title == null || title.isEmpty()) return;
        TextView heading = text(title, 24, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(4), dp(20), 0, dp(12));
        parent.addView(heading, lp);
    }

    public void description(LinearLayout parent, String value) {
        if (value == null || value.isEmpty()) return;
        TextView description = text(value, 15, false);
        description.setTextColor(MUTED);
        description.setPadding(dp(4), dp(5), dp(4), dp(15));
        parent.addView(description, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    public View button(String title, Runnable action) {
        TextView button = text(title, 16, true);
        button.setGravity(Gravity.CENTER);
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        button.setClickable(true);
        button.setPadding(dp(24), dp(14), dp(24), dp(14));
        button.setMinWidth(dp(146));
        button.setMaxLines(2);
        button.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(64));
        lp.setMargins(dp(6), dp(9), dp(12), dp(9));
        button.setLayoutParams(lp);
        styleFocus(button);
        return button;
    }

    public void emptyRow(ScreenView screen, String title, String message) {
        LinearLayout row = screen.row(this, title);
        LinearLayout empty = card();
        empty.setClickable(false);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER_VERTICAL);
        empty.setPadding(dp(26), dp(20), dp(26), dp(20));
        TextView label = text(message, 17, false);
        label.setTextColor(MUTED);
        empty.addView(label);
        empty.setContentDescription(message);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(480), dp(126));
        lp.setMargins(dp(6), dp(9), dp(14), dp(9));
        row.addView(empty, lp);
    }

    public void hero(ScreenView screen, String title, String subtitle) {
        LinearLayout banner = new LinearLayout(activity);
        banner.setOrientation(LinearLayout.VERTICAL);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setPadding(dp(32), dp(26), dp(32), dp(26));
        GradientDrawable surface = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(230, 34, 55, 83), Color.argb(195, 17, 27, 44), Color.argb(140, 30, 27, 50)});
        surface.setCornerRadius(dp(26));
        surface.setStroke(dp(1), Color.argb(58, 139, 165, 204));
        banner.setBackground(surface);
        TextView eyebrow = text("ENTDECKEN", 11, true);
        eyebrow.setLetterSpacing(0.2f);
        eyebrow.setTextColor(Color.rgb(151, 198, 231));
        banner.addView(eyebrow);
        TextView headline = text(title, 34, true);
        headline.setPadding(0, dp(12), 0, dp(10));
        banner.addView(headline);
        TextView caption = text(subtitle, 16, false);
        caption.setTextColor(MUTED);
        banner.addView(caption);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(205));
        lp.setMargins(dp(6), dp(14), dp(6), dp(12));
        screen.root.addView(banner, lp);
    }

    public View artwork(Uri uri, int heightDp) {
        ImageView image = new ImageView(activity);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(180, 20, 31, 48));
        background.setCornerRadius(dp(20));
        image.setBackground(background);
        image.setClipToOutline(true);
        image.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp)));
        images.load(uri, image);
        return image;
    }

    public View poster(MediaItem item, Runnable action) {
        LinearLayout poster = card();
        poster.setOrientation(LinearLayout.VERTICAL);
        poster.setPadding(dp(8), dp(8), dp(8), dp(12));
        poster.addView(artwork(item.posterUri, 224));
        TextView name = text(item.title, 15, true);
        name.setPadding(dp(8), dp(10), dp(8), dp(4));
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        poster.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        poster.setContentDescription(item.title);
        poster.setTag("media:" + item.providerPackage + ":" + item.id);
        poster.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(184), dp(306));
        lp.setMargins(dp(6), dp(9), dp(14), dp(9));
        poster.setLayoutParams(lp);
        return poster;
    }

    public void mediaRow(ScreenView screen, String title, List<MediaItem> items, ScreenHost host) {
        if (items == null || items.isEmpty()) {
            String message = title.toLowerCase(java.util.Locale.GERMANY).contains("weiter")
                    ? "Noch keine Inhalte zum Weiterschauen verfügbar"
                    : "Noch keine Inhalte verfügbar";
            emptyRow(screen, title, message);
            return;
        }
        LinearLayout row = screen.row(this, title);
        for (MediaItem item : items) row.addView(poster(item, () -> host.openMedia(item)));
    }

    public void genreRows(ScreenView screen, ScreenHost host) {
        LinearLayout row = screen.row(this, "Nach Genre suchen");
        for (String genre : GENRES) {
            View tile = button(genre, () -> host.openGenre(genre));
            tile.setTag("genre:" + genre);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(184), dp(112));
            lp.setMargins(dp(6), dp(9), dp(14), dp(9));
            row.addView(tile, lp);
        }
    }

    public EditText addSearch(ScreenView screen, int inputId, int submitId, String hint,
            Consumer<String> onSubmit) {
        LinearLayout searchRow = new LinearLayout(activity);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setFocusable(false);
        searchRow.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        EditText input = new EditText(activity);
        input.setId(inputId);
        input.setHint(hint);
        input.setHintTextColor(MUTED);
        input.setTextColor(TEXT);
        input.setTextSize(19);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setShowSoftInputOnFocus(false);
        input.setSelectAllOnFocus(false);
        input.setFocusable(true);
        input.setFocusableInTouchMode(true);
        input.setPadding(dp(24), dp(18), dp(24), dp(18));
        input.setContentDescription(hint);
        input.setOnClickListener(view -> {
            InputMethodManager keyboard = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            input.setShowSoftInputOnFocus(true);
            input.requestFocus();
            if (keyboard != null) keyboard.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        });
        Consumer<String> submit = query -> {
            InputMethodManager keyboard = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (keyboard != null) keyboard.hideSoftInputFromWindow(input.getWindowToken(), 0);
            onSubmit.accept(query.trim());
        };
        input.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP) {
                submit.accept(input.getText().toString());
                return true;
            }
            return false;
        });
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(0, dp(76), 1);
        inputLp.setMargins(dp(6), dp(12), dp(10), dp(12));
        searchRow.addView(input, inputLp);
        styleFocus(input);
        View search = button("Suchen", () -> submit.accept(input.getText().toString()));
        search.setId(submitId);
        searchRow.addView(search);
        screen.root.addView(searchRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        screen.registerRow(searchRow);
        return input;
    }

    public View liveCard(LiveTvRepository.Program program, Runnable action) {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(10), dp(10), dp(14));
        card.addView(artwork(program.artworkUri, 118));
        TextView station = text(program.channelName + "   ● LIVE", 12, true);
        station.setTextColor(Color.rgb(142, 208, 246));
        station.setPadding(dp(8), dp(10), dp(8), dp(4));
        card.addView(station);
        TextView title = text(program.title, 17, true);
        title.setMaxLines(2);
        title.setPadding(dp(8), 0, dp(8), dp(6));
        card.addView(title);
        if (program.startMillis > 0 && program.endMillis > program.startMillis) {
            ProgressBar progress = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
            progress.setMax(100);
            progress.setProgress((int) Math.max(0, Math.min(100,
                    100L * (System.currentTimeMillis() - program.startMillis) / (program.endMillis - program.startMillis))));
            progress.setProgressTintList(ColorStateList.valueOf(Color.rgb(114, 202, 248)));
            card.addView(progress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4)));
        }
        card.setContentDescription(program.channelName + ", " + program.title + ", live");
        card.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(304), dp(240));
        lp.setMargins(dp(6), dp(9), dp(14), dp(9));
        card.setLayoutParams(lp);
        return card;
    }

    public void loadAppIcon(LaunchableApps.Entry entry, ImageView image) { images.loadIcon(entry, image); }
    public void clearPackage(String packageName) { images.clearPackage(packageName); }
    @Override public void close() { images.close(); }
}
