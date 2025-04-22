package org.openhab.binding.wyoming.internal.protocol;

public class WyomingProtocolException extends Exception {

    private String json;

    public WyomingProtocolException(String message) {
        super(message);
    }

    public WyomingProtocolException(String message, Exception cause) {
        super(message, cause);
    }

    public void setJson(String json) {
        this.json = json;
    }

    public String getJson() {
        return json;
    }
}
