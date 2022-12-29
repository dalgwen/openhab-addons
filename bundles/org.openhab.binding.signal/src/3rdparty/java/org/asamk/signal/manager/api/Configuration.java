package org.asamk.signal.manager.api;


import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Optional;

import org.asamk.signal.manager.storage.configuration.ConfigurationStore;

public class Configuration {

    public Configuration(@JsonProperty("readReceipts") Optional<Boolean> readReceipts, @JsonProperty("unidentifiedDeliveryIndicators") Optional<Boolean> unidentifiedDeliveryIndicators,
            @JsonProperty("typingIndicators") Optional<Boolean> typingIndicators, @JsonProperty("linkPreviews") Optional<Boolean> linkPreviews) {
        super();
        this.readReceipts = readReceipts;
        this.unidentifiedDeliveryIndicators = unidentifiedDeliveryIndicators;
        this.typingIndicators = typingIndicators;
        this.linkPreviews = linkPreviews;
    }

    private final Optional<Boolean> readReceipts;
    private final Optional<Boolean> unidentifiedDeliveryIndicators;
    private final Optional<Boolean> typingIndicators;
    private final Optional<Boolean> linkPreviews;

    public static Configuration from(final ConfigurationStore configurationStore) {
        return new Configuration(Optional.ofNullable(configurationStore.getReadReceipts()),
                Optional.ofNullable(configurationStore.getUnidentifiedDeliveryIndicators()),
                Optional.ofNullable(configurationStore.getTypingIndicators()),
                Optional.ofNullable(configurationStore.getLinkPreviews()));
    }

    public Optional<Boolean> readReceipts() {
        return readReceipts;
    }

    public Optional<Boolean> unidentifiedDeliveryIndicators() {
        return unidentifiedDeliveryIndicators;
    }

    public Optional<Boolean> typingIndicators() {
        return typingIndicators;
    }

    public Optional<Boolean> linkPreviews() {
        return linkPreviews;
    }
}
