package org.asamk.signal.manager.api;


import com.fasterxml.jackson.annotation.JsonProperty;

public class ReceiveConfig {
    private final boolean ignoreAttachments;
    private final boolean ignoreStories;
    private final boolean sendReadReceipts;

    public ReceiveConfig(@JsonProperty("ignoreAttachments") boolean ignoreAttachments, @JsonProperty("ignoreStories") boolean ignoreStories, @JsonProperty("sendReadReceipts") boolean sendReadReceipts) {
        super();
        this.ignoreAttachments = ignoreAttachments;
        this.ignoreStories = ignoreStories;
        this.sendReadReceipts = sendReadReceipts;
    }

    public boolean ignoreAttachments() {
        return ignoreAttachments;
    }

    public boolean ignoreStories() {
        return ignoreStories;
    }

    public boolean sendReadReceipts() {
        return sendReadReceipts;
    }

}
