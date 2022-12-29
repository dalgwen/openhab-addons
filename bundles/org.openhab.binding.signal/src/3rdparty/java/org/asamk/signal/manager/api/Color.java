package org.asamk.signal.manager.api;


import com.fasterxml.jackson.annotation.JsonProperty;

public class Color {

    private final int color;

    public Color(@JsonProperty("color") int color) {
        super();
        this.color = color;
    }

    public int alpha() {
        return color >>> 24;
    }

    public int red() {
        return (color >> 16) & 0xFF;
    }

    public int green() {
        return (color >> 8) & 0xFF;
    }

    public int blue() {
        return color & 0xFF;
    }

    public String toHexColor() {
        return String.format("#%08x", color);
    }

    public int color() {
        return color;
    }
}
