package org.asamk.signal.manager.storage.recipients;

import java.util.Optional;

import org.whispersystems.signalservice.api.push.PNI;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RecipientAddress {

    private final Optional<ServiceId> serviceId;
    private final Optional<PNI> pni;
    private final Optional<String> number;

    @SuppressWarnings("null")
    public RecipientAddress(@JsonProperty("serviceId") Optional<ServiceId> serviceId,
            @JsonProperty("pni") Optional<PNI> pni, @JsonProperty("number") Optional<String> number) {
        super();
        Optional<PNI> _pni = pni;
        Optional<ServiceId> _serviceId = serviceId;
        if (_serviceId.isPresent() && _serviceId.get().equals(ServiceId.UNKNOWN)) {
            _serviceId = Optional.empty();
        }
        if (_pni.isPresent() && _pni.get().equals(ServiceId.UNKNOWN)) {
            _pni = Optional.empty();
        }
        if (_serviceId.isEmpty() && _pni.isPresent()) {
            _serviceId = Optional.of(_pni.get());
        }
        if (_serviceId.isPresent() && _serviceId.get() instanceof PNI) {
            PNI sPNI = (PNI) serviceId.get();
            if (_pni.isPresent() && !sPNI.equals(_pni.get())) {
                throw new AssertionError("Must not have two different PNIs!");
            }
            if (_pni.isEmpty()) {
                _pni = Optional.of(sPNI);
            }
        }
        if (_serviceId.isEmpty() && number.isEmpty()) {
            throw new AssertionError("Must have either a ServiceId or E164 number!");
        }

        this.serviceId = _serviceId;
        this.pni = _pni;
        this.number = number;

    }

    public RecipientAddress(@JsonProperty("serviceId") Optional<ServiceId> serviceId,
            @JsonProperty("number") Optional<String> number) {
        this(serviceId, Optional.empty(), number);
    }

    public RecipientAddress(@JsonProperty("serviceId") ServiceId serviceId, @JsonProperty("e164") String e164) {
        this(Optional.ofNullable(serviceId), Optional.empty(), Optional.ofNullable(e164));
    }

    public RecipientAddress(@JsonProperty("serviceId") ServiceId serviceId, @JsonProperty("pni") PNI pni,
            @JsonProperty("e164") String e164) {
        this(Optional.ofNullable(serviceId), Optional.ofNullable(pni), Optional.ofNullable(e164));
    }

    public RecipientAddress(@JsonProperty("address") SignalServiceAddress address) {
        this(Optional.of(address.getServiceId()), Optional.empty(), address.getNumber());
    }

    public RecipientAddress(@JsonProperty("address") org.asamk.signal.manager.api.RecipientAddress address) {
        this(address.uuid().map(ServiceId::from), Optional.empty(), address.number());
    }

    public RecipientAddress(@JsonProperty("serviceId") ServiceId serviceId) {
        this(Optional.of(serviceId), Optional.empty());
    }

    public RecipientAddress withIdentifiersFrom(RecipientAddress address) {
        return new RecipientAddress(
                (this.serviceId.isEmpty() || this.isServiceIdPNI() || this.serviceId.equals(address.pni))
                        && !address.isServiceIdPNI() ? address.serviceId : this.serviceId,
                address.pni.or(this::pni), address.number.or(this::number));
    }

    public RecipientAddress removeIdentifiersFrom(RecipientAddress address) {
        return new RecipientAddress(
                address.serviceId.equals(this.serviceId) || address.pni.equals(this.serviceId) ? Optional.empty()
                        : this.serviceId,
                address.pni.equals(this.pni) || address.serviceId.equals(this.pni) ? Optional.empty() : this.pni,
                address.number.equals(this.number) ? Optional.empty() : this.number);
    }

    public ServiceId getServiceId() {
        return serviceId.orElse(ServiceId.UNKNOWN);
    }

    public Optional<ServiceId> serviceId() {
        return serviceId;
    }

    public String getIdentifier() {
        if (serviceId.isPresent()) {
            return serviceId.get().toString();
        } else if (number.isPresent()) {
            return number.get();
        } else {
            throw new AssertionError("Given the checks in the constructor, this should not be possible.");
        }
    }

    public String getLegacyIdentifier() {
        if (number.isPresent()) {
            return number.get();
        } else if (serviceId.isPresent()) {
            return serviceId.get().toString();
        } else {
            throw new AssertionError("Given the checks in the constructor, this should not be possible.");
        }
    }

    public boolean matches(RecipientAddress other) {
        return (serviceId.isPresent() && other.serviceId.isPresent() && serviceId.get().equals(other.serviceId.get()))
                || (pni.isPresent() && other.serviceId.isPresent() && pni.get().equals(other.serviceId.get()))
                || (serviceId.isPresent() && other.pni.isPresent() && serviceId.get().equals(other.pni.get()))
                || (pni.isPresent() && other.pni.isPresent() && pni.get().equals(other.pni.get()))
                || (number.isPresent() && other.number.isPresent() && number.get().equals(other.number.get()));
    }

    public boolean hasSingleIdentifier() {
        return serviceId().isEmpty() || number.isEmpty();
    }

    public boolean hasIdentifiersOf(RecipientAddress address) {
        return (address.serviceId.isEmpty() || address.serviceId.equals(serviceId) || address.serviceId.equals(pni))
                && (address.pni.isEmpty() || address.pni.equals(pni))
                && (address.number.isEmpty() || address.number.equals(number));
    }

    public boolean hasAdditionalIdentifiersThan(RecipientAddress address) {
        return (serviceId.isPresent() && (address.serviceId.isEmpty()
                || (!address.serviceId.equals(serviceId) && !address.pni.equals(serviceId))))
                || (pni.isPresent() && !address.serviceId.equals(pni)
                        && (address.pni.isEmpty() || !address.pni.equals(pni)))
                || (number.isPresent() && (address.number.isEmpty() || !address.number.equals(number)));
    }

    public boolean hasOnlyPniAndNumber() {
        return pni.isPresent() && serviceId.equals(pni) && number.isPresent();
    }

    public boolean isServiceIdPNI() {
        return serviceId.isPresent() && (pni.isPresent() && serviceId.equals(pni));
    }

    public SignalServiceAddress toSignalServiceAddress() {
        return new SignalServiceAddress(getServiceId(), number);
    }

    public org.asamk.signal.manager.api.RecipientAddress toApiRecipientAddress() {
        return new org.asamk.signal.manager.api.RecipientAddress(serviceId.map(ServiceId::uuid), number);
    }

    public Optional<PNI> pni() {
        return pni;
    }

    public Optional<String> number() {
        return number;
    }
}
