package org.asamk.signal.manager.api;


import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class SendGroupMessageResults {
    private final long timestamp;
    private final List<SendMessageResult> results;

    public SendGroupMessageResults(@JsonProperty("timestamp") long timestamp, @JsonProperty("results") List<SendMessageResult> results) {
        super();
        this.timestamp = timestamp;
        this.results = results;
    }

    public long timestamp() {
        return timestamp;
    }

    public List<SendMessageResult> results() {
        return results;
    }

}
