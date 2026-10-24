package de.tcl.c9khome;

public final class HdmiInput {
    public final String id;
    public final String label;
    public final int state;
    public final DeviceBrand brand;

    public HdmiInput(String id, String label, int state, DeviceBrand brand) {
        this.id = id;
        this.label = label;
        this.state = state;
        this.brand = brand;
    }
}
