package de.tcl.c9khome;

import android.content.Context;
import java.util.Locale;

public final class LogoResolver {
    private LogoResolver() {}

    public static DeviceBrand resolve(String raw) {
        String s = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        if (containsAny(s, "playstation", "ps5", "ps4", "ps 5", "sony interactive")) return DeviceBrand.PLAYSTATION;
        if (containsAny(s, "xbox", "series x", "series s")) return DeviceBrand.XBOX;
        if (containsAny(s, "switch 2", "switch2", "nintendo switch 2")) return DeviceBrand.SWITCH2;
        if (containsAny(s, "nintendo", "switch")) return DeviceBrand.NINTENDO;
        if (containsAny(s, "apple tv", "appletv")) return DeviceBrand.APPLE_TV;
        if (containsAny(s, "fire tv", "firetv", "amazon")) return DeviceBrand.FIRE_TV;
        if (containsAny(s, "chromecast", "google tv", "google streamer")) return DeviceBrand.CHROMECAST;
        if (containsAny(s, "shield", "nvidia")) return DeviceBrand.NVIDIA;
        if (s.contains("roku")) return DeviceBrand.ROKU;
        if (containsAny(s, "steam deck", "steam")) return DeviceBrand.STEAM;
        if (containsAny(s, "rog", "republic of gamers", "asus")) return DeviceBrand.ASUS_ROG;
        if (containsAny(s, "legion", "lenovo")) return DeviceBrand.LENOVO;
        if (s.contains("alienware")) return DeviceBrand.ALIENWARE;
        if (s.contains("msi")) return DeviceBrand.MSI;
        if (s.contains("acer")) return DeviceBrand.ACER;
        if (containsAny(s, "hewlett", " hp ", "hp-")) return DeviceBrand.HP;
        if (s.contains("dell")) return DeviceBrand.DELL;
        if (s.contains("sonos")) return DeviceBrand.SONOS;
        if (s.contains("bose")) return DeviceBrand.BOSE;
        if (s.contains("jbl")) return DeviceBrand.JBL;
        if (s.contains("denon")) return DeviceBrand.DENON;
        if (s.contains("yamaha")) return DeviceBrand.YAMAHA;
        if (s.contains("marantz")) return DeviceBrand.MARANTZ;
        if (s.contains("pioneer")) return DeviceBrand.PIONEER;
        if (s.contains("onkyo")) return DeviceBrand.ONKYO;
        if (s.contains("teufel")) return DeviceBrand.TEUFEL;
        if (s.contains("sennheiser")) return DeviceBrand.SENNHEISER;
        if (s.contains("samsung")) return DeviceBrand.SAMSUNG;
        if (s.contains("panasonic")) return DeviceBrand.PANASONIC;
        if (s.contains("philips")) return DeviceBrand.PHILIPS;
        if (s.contains("hisense")) return DeviceBrand.HISENSE;
        if (s.contains("sony")) return DeviceBrand.SONY;
        if (containsAny(s, " lg ", "lg-", "lg_")) return DeviceBrand.LG;
        if (containsAny(s, "magenta", "telekom")) return DeviceBrand.MAGENTA;
        if (s.contains("vodafone")) return DeviceBrand.VODAFONE;
        if (s.contains("sky")) return DeviceBrand.SKY;
        return DeviceBrand.HDMI;
    }

    private static boolean containsAny(String s, String... words) {
        for (String word : words) if (s.contains(word)) return true;
        return false;
    }
}
