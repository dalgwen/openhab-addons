package org.asamk.signal.manager.storage.recipients;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RecipientWithAddress {
    RecipientId id;
    RecipientAddress address;

    public RecipientWithAddress(@JsonProperty("id") RecipientId id, @JsonProperty("address") RecipientAddress address) {
        super();
        this.id = id;
        this.address = address;
    }

    public RecipientId getId() {
        return id;
    }

    public RecipientId id() {
        return id;
    }

    public void setId(RecipientId id) {
        this.id = id;
    }

    public RecipientAddress getAddress() {
        return address;
    }

    public RecipientAddress address() {
        return address;
    }

    public void setAddress(RecipientAddress address) {
        this.address = address;
    }

}
