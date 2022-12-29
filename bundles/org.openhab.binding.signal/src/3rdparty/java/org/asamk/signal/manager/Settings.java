package org.asamk.signal.manager;

import org.asamk.signal.manager.storage.identities.TrustNewIdentity;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Settings {
    TrustNewIdentity trustNewIdentity;
    boolean disableMessageSendLog;

    public Settings(@JsonProperty("trustNewIdentity") TrustNewIdentity trustNewIdentity,
            @JsonProperty("disableMessageSendLog") boolean disableMessageSendLog) {
        this.trustNewIdentity = trustNewIdentity;
        this.disableMessageSendLog = disableMessageSendLog;
    }

    public static Settings DEFAULT = new Settings(TrustNewIdentity.ON_FIRST_USE, false);

    public TrustNewIdentity getTrustNewIdentity() {
        return trustNewIdentity;
    }

    public TrustNewIdentity trustNewIdentity() {
        return trustNewIdentity;
    }

    public void setTrustNewIdentity(TrustNewIdentity trustNewIdentity) {
        this.trustNewIdentity = trustNewIdentity;
    }

    public boolean isDisableMessageSendLog() {
        return disableMessageSendLog;
    }

    public boolean disableMessageSendLog() {
        return disableMessageSendLog;
    }

    public void setDisableMessageSendLog(boolean disableMessageSendLog) {
        this.disableMessageSendLog = disableMessageSendLog;
    }
}
