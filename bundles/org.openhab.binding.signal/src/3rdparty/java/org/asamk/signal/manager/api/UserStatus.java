package org.asamk.signal.manager.api;


import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public class UserStatus {
    private final String number;
    private final UUID uuid;
    private final boolean unrestrictedUnidentifiedAccess;

    public UserStatus(@JsonProperty("number") String number, @JsonProperty("uuid") UUID uuid, @JsonProperty("unrestrictedUnidentifiedAccess") boolean unrestrictedUnidentifiedAccess) {
        super();
        this.number = number;
        this.uuid = uuid;
        this.unrestrictedUnidentifiedAccess = unrestrictedUnidentifiedAccess;
    }

    public String number() {
        return number;
    }

    public UUID uuid() {
        return uuid;
    }

    public boolean unrestrictedUnidentifiedAccess() {
        return unrestrictedUnidentifiedAccess;
    }

}
