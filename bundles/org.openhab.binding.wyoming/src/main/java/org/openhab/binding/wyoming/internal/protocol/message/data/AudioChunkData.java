package org.openhab.binding.wyoming.internal.protocol.message.data;

public class AudioChunkData extends WyomingData {

    private int rate;          // sample rate in Hertz (required)
    private int width;         // sample width in bytes (required)
    private int channels;      // number of channels (required)
    private Integer timestamp; // timestamp of audio chunk in milliseconds (optional)

    public AudioChunkData(int rate, int width, int channels) {
        this.rate = rate;
        this.width = width;
        this.channels = channels;
    }

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
        return "AudioChunk{" +
                "rate=" + rate +
                ", width=" + width +
                ", channels=" + channels +
                ", timestamp=" + timestamp +
                '}';
    }
}