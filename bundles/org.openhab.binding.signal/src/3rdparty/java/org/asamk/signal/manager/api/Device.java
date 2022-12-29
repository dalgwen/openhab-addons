package org.asamk.signal.manager.api;


import com.fasterxml.jackson.annotation.JsonProperty;

public class Device {
    private final int id;
    private final String name;
    private final long created;
    private final long lastSeen;
    private final boolean isThisDevice;

    public Device(@JsonProperty("id") int id, @JsonProperty("name") String name, @JsonProperty("created") long created, @JsonProperty("lastSeen") long lastSeen, @JsonProperty("isThisDevice") boolean isThisDevice) {
        super();
        this.id = id;
        this.name = name;
        this.created = created;
        this.lastSeen = lastSeen;
        this.isThisDevice = isThisDevice;
    }

    public int id() {
        return id;
    }

    public String name() {
        return name;
    }

    public long created() {
        return created;
    }

    public long lastSeen() {
        return lastSeen;
    }

    public boolean isThisDevice() {
        return isThisDevice;
    }

}
