package org.asamk.signal.manager.api;


import com.fasterxml.jackson.annotation.JsonProperty;

import org.signal.libsignal.protocol.IdentityKey;

public class Identity {
    private final RecipientAddress recipient;
    private final IdentityKey identityKey;
    private final String safetyNumber;
    private final byte[] scannableSafetyNumber;
    private final TrustLevel trustLevel;
    private final long dateAddedTimestamp;

    public Identity(@JsonProperty("recipient") RecipientAddress recipient, @JsonProperty("identityKey") IdentityKey identityKey, @JsonProperty("safetyNumber") String safetyNumber,
            @JsonProperty("scannableSafetyNumber") byte[] scannableSafetyNumber, @JsonProperty("trustLevel") TrustLevel trustLevel, @JsonProperty("dateAddedTimestamp") long dateAddedTimestamp) {
        super();
        this.recipient = recipient;
        this.identityKey = identityKey;
        this.safetyNumber = safetyNumber;
        this.scannableSafetyNumber = scannableSafetyNumber;
        this.trustLevel = trustLevel;
        this.dateAddedTimestamp = dateAddedTimestamp;
    }

    public byte[] getFingerprint() {
        return identityKey.getPublicKey().serialize();
    }

    public RecipientAddress recipient() {
        return recipient;
    }

    public IdentityKey identityKey() {
        return identityKey;
    }

    public String safetyNumber() {
        return safetyNumber;
    }

    public byte[] getScannableSafetyNumber() {
        return scannableSafetyNumber;
    }

    public TrustLevel trustLevel() {
        return trustLevel;
    }

    public long dateAddedTimestamp() {
        return dateAddedTimestamp;
    }
}
