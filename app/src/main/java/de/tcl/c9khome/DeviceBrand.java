package de.tcl.c9khome;

public enum DeviceBrand {
    HDMI("hdmi", "HDMI", "HDMI"),
    PLAYSTATION("playstation", "PlayStation / PS5", "PS5"),
    XBOX("xbox", "Xbox", "XBOX"),
    SWITCH2("switch2", "Nintendo Switch 2", "SWITCH 2"),
    NINTENDO("nintendo", "Nintendo", "NINTENDO"),
    APPLE_TV("appletv", "Apple TV", "Apple TV"),
    FIRE_TV("firetv", "Amazon Fire TV", "fire tv"),
    CHROMECAST("chromecast", "Chromecast / Google TV", "Google TV"),
    NVIDIA("nvidia", "NVIDIA Shield", "NVIDIA"),
    ROKU("roku", "Roku", "Roku"),
    STEAM("steam", "Steam Deck", "STEAM"),
    ASUS_ROG("rog", "ASUS ROG", "ROG"),
    LENOVO("lenovo", "Lenovo / Legion", "LEGION"),
    ALIENWARE("alienware", "Alienware", "ALIENWARE"),
    MSI("msi", "MSI", "MSI"),
    ACER("acer", "Acer", "acer"),
    HP("hp", "HP", "HP"),
    DELL("dell", "Dell", "DELL"),
    SONY("sony", "Sony", "SONY"),
    SAMSUNG("samsung", "Samsung", "SAMSUNG"),
    LG("lg", "LG", "LG"),
    PANASONIC("panasonic", "Panasonic", "Panasonic"),
    PHILIPS("philips", "Philips", "PHILIPS"),
    HISENSE("hisense", "Hisense", "Hisense"),
    SONOS("sonos", "Sonos", "SONOS"),
    BOSE("bose", "Bose", "BOSE"),
    JBL("jbl", "JBL", "JBL"),
    DENON("denon", "Denon", "DENON"),
    YAMAHA("yamaha", "Yamaha", "YAMAHA"),
    MARANTZ("marantz", "Marantz", "Marantz"),
    PIONEER("pioneer", "Pioneer", "Pioneer"),
    ONKYO("onkyo", "Onkyo", "ONKYO"),
    TEUFEL("teufel", "Teufel", "TEUFEL"),
    SENNHEISER("sennheiser", "Sennheiser", "SENNHEISER"),
    SKY("sky", "Sky", "sky"),
    MAGENTA("magenta", "MagentaTV", "MAGENTA TV"),
    VODAFONE("vodafone", "Vodafone", "vodafone");

    public final String key;
    public final String displayName;
    public final String logoText;

    DeviceBrand(String key, String displayName, String logoText) {
        this.key = key;
        this.displayName = displayName;
        this.logoText = logoText;
    }
}
