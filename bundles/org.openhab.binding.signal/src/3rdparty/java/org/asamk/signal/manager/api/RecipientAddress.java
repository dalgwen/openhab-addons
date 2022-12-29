package org.asamk.signal.manager.api;

import java.util.Optional;
import java.util.UUID;

import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RecipientAddress {
    private final Optional<UUID> uuid;
    private final Optional<String> number;

    public static final UUID UNKNOWN_UUID = ServiceId.UNKNOWN.uuid();

    public RecipientAddress(@JsonProperty("uuid") Optional<UUID> uuid,
            @JsonProperty("number") Optional<String> number) {
        super();
        Optional<UUID> _uuid = uuid;

        _uuid = _uuid.isPresent() && _uuid.get().equals(UNKNOWN_UUID) ? Optional.empty() : _uuid;
        if (_uuid.isEmpty() && number.isEmpty()) {
            throw new AssertionError("Must have either a UUID or E164 number!");
        }

        this.uuid = _uuid;
        this.number = number;
    }

    public RecipientAddress(@JsonProperty("uuid") UUID uuid, @JsonProperty("e164") String e164) {
        this(Optional.ofNullable(uuid), Optional.ofNullable(e164));
    }

    public RecipientAddress(@JsonProperty("address") SignalServiceAddress address) {
        this(Optional.of(address.getServiceId().uuid()), address.getNumber());
    }

    public RecipientAddress(@JsonProperty("uuid") UUID uuid) {
        this(Optional.of(uuid), Optional.empty());
    }

    public ServiceId getServiceId() {
        return ServiceId.from(uuid.orElse(UNKNOWN_UUID));
    }

    public String getIdentifier() {
        if (uuid.isPresent()) {
            return uuid.get().toString();
        } else if (number.isPresent()) {
            return number.get();
        } else {
            throw new AssertionError("Given the checks in the constructor, this should not be possible.");
        }
    }

    public String getLegacyIdentifier() {
        if (number.isPresent()) {
            return number.get();
        } else if (uuid.isPresent()) {
            return uuid.get().toString();
        } else {
            throw new AssertionError("Given the checks in the constructor, this should not be possible.");
        }
    }

    public boolean matches(RecipientAddress other) {
        return (uuid.isPresent() && other.uuid.isPresent() && uuid.get().equals(other.uuid.get()))
                || (number.isPresent() && other.number.isPresent() && number.get().equals(other.number.get()));
    }

    public Optional<UUID> uuid() {
        return uuid;
    }

    public Optional<String> number() {
        return number;
    }

    public static UUID getUnknownUuid() {
        return UNKNOWN_UUID;
    }
}
