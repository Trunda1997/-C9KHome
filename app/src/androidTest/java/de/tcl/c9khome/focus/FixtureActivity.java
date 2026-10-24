package de.tcl.c9khome.focus;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** Test-APK-only TV app entries make the apps row wider than the screen. */
public final class FixtureActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView text = new TextView(this);
        text.setText("D-Pad test fixture");
        setContentView(text);
    }
}
