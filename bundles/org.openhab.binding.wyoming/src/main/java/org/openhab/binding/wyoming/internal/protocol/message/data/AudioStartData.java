package org.openhab.binding.wyoming.internal.protocol.message.data;

public class AudioStartData extends WyomingData {
    private int rate;         // Sample rate in hertz
    private int width;        // Sample width in bytes
    private int channels;     // Number of channels
    private Integer timestamp; // Timestamp in milliseconds (optional)

    // Constructor (Required fields)
    public AudioStartData(int rate, int width, int channels) {
        this.rate = rate;
        this.width = width;
        this.channels = channels;
    }

    // Getters and Setters
    public int getRate() {
        return rate;
    }

    public void setRate(int rate) {
        this.rate = rate;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getChannels() {
        return channels;
    }

    public void setChannels(int channels) {
        this.channels = channels;
    }

    public Integer getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Integer timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "AudioStart{" +
                "rate=" + rate +
                ", width=" + width +
                ", channels=" + channels +
                ", timestamp=" + timestamp +
                '}';
    }
}