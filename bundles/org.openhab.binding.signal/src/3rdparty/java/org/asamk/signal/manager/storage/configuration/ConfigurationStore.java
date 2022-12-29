package org.asamk.signal.manager.storage.configuration;


import com.fasterxml.jackson.annotation.JsonProperty;

import org.asamk.signal.manager.api.PhoneNumberSharingMode;

public class ConfigurationStore {

    private final Saver saver;

    private Boolean readReceipts;
    private Boolean unidentifiedDeliveryIndicators;
    private Boolean typingIndicators;
    private Boolean linkPreviews;
    private Boolean phoneNumberUnlisted;
    private PhoneNumberSharingMode phoneNumberSharingMode;

    public ConfigurationStore(final Saver saver) {
        this.saver = saver;
    }

    public static ConfigurationStore fromStorage(Storage storage, Saver saver) {
        final var store = new ConfigurationStore(saver);
        store.readReceipts = storage.readReceipts;
        store.unidentifiedDeliveryIndicators = storage.unidentifiedDeliveryIndicators;
        store.typingIndicators = storage.typingIndicators;
        store.linkPreviews = storage.linkPreviews;
        store.phoneNumberSharingMode = storage.phoneNumberSharingMode;
        return store;
    }

    public Boolean getReadReceipts() {
        return readReceipts;
    }

    public void setReadReceipts(final boolean readReceipts) {
        this.readReceipts = readReceipts;
        saver.save(toStorage());
    }

    public Boolean getUnidentifiedDeliveryIndicators() {
        return unidentifiedDeliveryIndicators;
    }

    public void setUnidentifiedDeliveryIndicators(final boolean unidentifiedDeliveryIndicators) {
        this.unidentifiedDeliveryIndicators = unidentifiedDeliveryIndicators;
        saver.save(toStorage());
    }

    public Boolean getTypingIndicators() {
        return typingIndicators;
    }

    public void setTypingIndicators(final boolean typingIndicators) {
        this.typingIndicators = typingIndicators;
        saver.save(toStorage());
    }

    public Boolean getLinkPreviews() {
        return linkPreviews;
    }

    public void setLinkPreviews(final boolean linkPreviews) {
        this.linkPreviews = linkPreviews;
        saver.save(toStorage());
    }

    public Boolean getPhoneNumberUnlisted() {
        return phoneNumberUnlisted;
    }

    public void setPhoneNumberUnlisted(final boolean phoneNumberUnlisted) {
        this.phoneNumberUnlisted = phoneNumberUnlisted;
        saver.save(toStorage());
    }

    public PhoneNumberSharingMode getPhoneNumberSharingMode() {
        return phoneNumberSharingMode;
    }

    public void setPhoneNumberSharingMode(final PhoneNumberSharingMode phoneNumberSharingMode) {
        this.phoneNumberSharingMode = phoneNumberSharingMode;
        saver.save(toStorage());
    }

    private Storage toStorage() {
        return new Storage(readReceipts, unidentifiedDeliveryIndicators, typingIndicators, linkPreviews,
                phoneNumberUnlisted, phoneNumberSharingMode);
    }

    public static class Storage {
        private final Boolean readReceipts;
        private final Boolean unidentifiedDeliveryIndicators;
        private final Boolean typingIndicators;
        private final Boolean linkPreviews;
        private final Boolean phoneNumberUnlisted;
        private final PhoneNumberSharingMode phoneNumberSharingMode;

        public Storage(@JsonProperty("readReceipts") Boolean readReceipts, @JsonProperty("unidentifiedDeliveryIndicators") Boolean unidentifiedDeliveryIndicators, @JsonProperty("typingIndicators") Boolean typingIndicators,
                @JsonProperty("linkPreviews") Boolean linkPreviews, @JsonProperty("phoneNumberUnlisted") Boolean phoneNumberUnlisted, @JsonProperty("phoneNumberSharingMode") PhoneNumberSharingMode phoneNumberSharingMode) {
            super();
            this.readReceipts = readReceipts;
            this.unidentifiedDeliveryIndicators = unidentifiedDeliveryIndicators;
            this.typingIndicators = typingIndicators;
            this.linkPreviews = linkPreviews;
            this.phoneNumberUnlisted = phoneNumberUnlisted;
            this.phoneNumberSharingMode = phoneNumberSharingMode;
        }

        public Boolean readReceipts() {
            return readReceipts;
        }

        public Boolean unidentifiedDeliveryIndicators() {
            return unidentifiedDeliveryIndicators;
        }

        public Boolean typingIndicators() {
            return typingIndicators;
        }

        public Boolean linkPreviews() {
            return linkPreviews;
        }

        public Boolean phoneNumberUnlisted() {
            return phoneNumberUnlisted;
        }

        public PhoneNumberSharingMode phoneNumberSharingMode() {
            return phoneNumberSharingMode;
        }

    }

    public interface Saver {

        void save(Storage storage);
    }
}
