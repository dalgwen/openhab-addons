package org.asamk.signal.manager.api;

import java.util.UUID;

import org.asamk.signal.manager.groups.GroupId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;
import org.whispersystems.signalservice.api.util.UuidUtil;

import com.fasterxml.jackson.annotation.JsonProperty;

public interface RecipientIdentifier {

    String getIdentifier();

    static class NoteToSelf implements RecipientIdentifier {

        public static final NoteToSelf INSTANCE = new NoteToSelf();

        @Override
        public String getIdentifier() {
            return "Note-To-Self";
        }
    }

    interface Single extends RecipientIdentifier {

        static Single fromString(String identifier, String localNumber) throws InvalidNumberException {
            try {
                if (UuidUtil.isUuid(identifier)) {
                    return new Uuid(UUID.fromString(identifier));
                }

                if (identifier.startsWith("u:")) {
                    return new Username(identifier.substring(2));
                }

                final var normalizedNumber = PhoneNumberFormatter.formatNumber(identifier, localNumber);
                if (!normalizedNumber.equals(identifier)) {
                    final Logger logger = LoggerFactory.getLogger(RecipientIdentifier.class);
                    logger.debug("Normalized number {} to {}.", identifier, normalizedNumber);
                }
                return new Number(normalizedNumber);
            } catch (org.whispersystems.signalservice.api.util.InvalidNumberException e) {
                throw new InvalidNumberException(e.getMessage(), e);
            }
        }

        static Single fromAddress(RecipientAddress address) {
            if (address.number().isPresent()) {
                return new Number(address.number().get());
            } else if (address.uuid().isPresent()) {
                return new Uuid(address.uuid().get());
            } else if (address.username().isPresent()) {
                return new Username(address.username().get());
            }
            throw new AssertionError("RecipientAddress without identifier");
        }

        RecipientAddress toPartialRecipientAddress();
    }

    public static class Uuid implements Single {
        private final UUID uuid;

        public Uuid(@JsonProperty("uuid") UUID uuid) {
            super();
            this.uuid = uuid;
        }

        @Override
        public String getIdentifier() {
            return uuid.toString();
        }

        @Override
        public RecipientAddress toPartialRecipientAddress() {
            return new RecipientAddress(uuid);
        }

        public UUID uuid() {
            return uuid;
        }
    }

    public static class Number implements Single {
        private final String number;

        public Number(@JsonProperty("number") String number) {
            super();
            this.number = number;
        }

        @Override
        public String getIdentifier() {
            return number;
        }

        @Override
        public RecipientAddress toPartialRecipientAddress() {
            return new RecipientAddress(null, number);
        }

        public String number() {
            return number;
        }
    }

    public static class Username implements Single {

        private final String username;

        public Username(String username) {
            this.username = username;
        }

        @Override
        public String getIdentifier() {
            return "u:" + username;
        }

        @Override
        public RecipientAddress toPartialRecipientAddress() {
            return new RecipientAddress(null, null, username);
        }

        public String username() {
            return username;
        }
    }

    public static class Group implements RecipientIdentifier {
        private final GroupId groupId;

        public Group(@JsonProperty("groupId") GroupId groupId) {
            super();
            this.groupId = groupId;
        }

        @Override
        public String getIdentifier() {
            return groupId.toBase64();
        }

        public GroupId groupId() {
            return groupId;
        }
    }
}
