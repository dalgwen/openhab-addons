package org.openhab.binding.wyoming.internal.protocol.message.data;

public class AudioStopData extends WyomingData {
    private Integer timestamp; // Timestamp in milliseconds (optional)

    public Integer getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Integer timestamp) {
        this.timestamp = timestamp;
    }
}
