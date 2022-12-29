package org.asamk.signal.manager.storage.sendLog;


import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Optional;

import org.asamk.signal.manager.groups.GroupId;
import org.whispersystems.signalservice.api.crypto.ContentHint;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.Content;

public class MessageSendLogEntry {
    private final Optional<GroupId> groupId;
    private final SignalServiceProtos.Content content;
    private final ContentHint contentHint;
    private final boolean urgent;

    public MessageSendLogEntry(@JsonProperty("groupId") Optional<GroupId> groupId, @JsonProperty("content") Content content, @JsonProperty("contentHint") ContentHint contentHint, @JsonProperty("urgent") boolean urgent) {
        super();
        this.groupId = groupId;
        this.content = content;
        this.contentHint = contentHint;
        this.urgent = urgent;
    }

    public Optional<GroupId> groupId() {
        return groupId;
    }

    public SignalServiceProtos.Content content() {
        return content;
    }

    public ContentHint contentHint() {
        return contentHint;
    }

    public boolean urgent() {
        return urgent;
    }

}
