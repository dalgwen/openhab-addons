package org.asamk.signal.manager.storage.recipients;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.asamk.signal.manager.storage.Utils;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.profiles.ExpiringProfileKeyCredential;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.push.ServiceId;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LegacyRecipientStore2 {

    private final static Logger logger = LoggerFactory.getLogger(LegacyRecipientStore2.class);

    public static void migrate(File file, RecipientStore recipientStore) {
        final var objectMapper = Utils.createStorageObjectMapper();
        try (var inputStream = new FileInputStream(file)) {
            final var storage = objectMapper.readValue(inputStream, Storage.class);

            final var recipients = storage.recipients.stream().map(r -> {
                final var recipientId = new RecipientId(r.id, recipientStore);
                final var address = new RecipientAddress(Optional.ofNullable(r.uuid).map(ServiceId::parseOrThrow),
                        Optional.ofNullable(r.number));

                Contact contact = null;
                if (r.contact != null) {
                    contact = new Contact(r.contact.name, null, r.contact.color, r.contact.messageExpirationTime,
                            r.contact.blocked, r.contact.archived, r.contact.profileSharingEnabled);
                }

                ProfileKey profileKey = null;
                if (r.profileKey != null) {
                    try {
                        profileKey = new ProfileKey(Base64.getDecoder().decode(r.profileKey));
                    } catch (InvalidInputException ignored) {
                    }
                }

                ExpiringProfileKeyCredential expiringProfileKeyCredential = null;
                if (r.expiringProfileKeyCredential != null) {
                    try {
                        expiringProfileKeyCredential = new ExpiringProfileKeyCredential(
                                Base64.getDecoder().decode(r.expiringProfileKeyCredential));
                    } catch (Throwable ignored) {
                    }
                }

                Profile profile = null;
                if (r.profile != null) {
                    profile = new Profile(r.profile.lastUpdateTimestamp, r.profile.givenName, r.profile.familyName,
                            r.profile.about, r.profile.aboutEmoji, r.profile.avatarUrlPath,
                            r.profile.mobileCoinAddress == null ? null
                                    : Base64.getDecoder().decode(r.profile.mobileCoinAddress),
                            Profile.UnidentifiedAccessMode.valueOfOrUnknown(r.profile.unidentifiedAccessMode),
                            r.profile.capabilities.stream().map(Profile.Capability::valueOfOrNull)
                                    .filter(Objects::nonNull).collect(Collectors.toSet()));
                }

                return new Recipient(recipientId, address, contact, profileKey, expiringProfileKeyCredential, profile);
            }).collect(Collectors.toMap(Recipient::getRecipientId, r -> r));

            recipientStore.addLegacyRecipients(recipients);
        } catch (FileNotFoundException e) {
            // nothing to migrate
        } catch (IOException e) {
            logger.warn("Failed to load recipient store", e);
            throw new RuntimeException(e);
        }
        try {
            Files.delete(file.toPath());
        } catch (IOException e) {
            logger.warn("Failed to load recipient store", e);
            throw new RuntimeException(e);
        }
    }

    private static class Storage {

        private final List<Recipient> recipients;
        private final long lastId;

        @SuppressWarnings("unused")
        public Storage(@JsonProperty("recipients") List<Recipient> recipients, @JsonProperty("lastId") long lastId) {
            super();
            this.recipients = recipients;
            this.lastId = lastId;
        }

        public static class Recipient {
            private final long id;
            private final String number;
            private final String uuid;
            private final String profileKey;
            private final String expiringProfileKeyCredential;
            private final Contact contact;
            private final Profile profile;

            @SuppressWarnings("unused")
            public Recipient(@JsonProperty("id") long id, @JsonProperty("number") String number,
                    @JsonProperty("uuid") String uuid, @JsonProperty("profileKey") String profileKey,
                    @JsonProperty("expiringProfileKeyCredential") String expiringProfileKeyCredential,
                    @JsonProperty("contact") Contact contact, @JsonProperty("profile") Profile profile) {
                super();
                this.id = id;
                this.number = number;
                this.uuid = uuid;
                this.profileKey = profileKey;
                this.expiringProfileKeyCredential = expiringProfileKeyCredential;
                this.contact = contact;
                this.profile = profile;
            }

            static class Contact {
                private final String name;
                private final String color;
                private final int messageExpirationTime;
                private final boolean blocked;
                private final boolean archived;
                private final boolean profileSharingEnabled;

                @SuppressWarnings("unused")
                public Contact(@JsonProperty("name") String name, @JsonProperty("color") String color,
                        @JsonProperty("messageExpirationTime") int messageExpirationTime,
                        @JsonProperty("blocked") boolean blocked, @JsonProperty("archived") boolean archived,
                        @JsonProperty("profileSharingEnabled") boolean profileSharingEnabled) {
                    super();
                    this.name = name;
                    this.color = color;
                    this.messageExpirationTime = messageExpirationTime;
                    this.blocked = blocked;
                    this.archived = archived;
                    this.profileSharingEnabled = profileSharingEnabled;
                }

                @SuppressWarnings("unused")
                public String name() {
                    return name;
                }

                @SuppressWarnings("unused")
                public String color() {
                    return color;
                }

                @SuppressWarnings("unused")
                public int messageExpirationTime() {
                    return messageExpirationTime;
                }

                @SuppressWarnings("unused")
                public boolean blocked() {
                    return blocked;
                }

                @SuppressWarnings("unused")
                public boolean archived() {
                    return archived;
                }

                @SuppressWarnings("unused")
                public boolean profileSharingEnabled() {
                    return profileSharingEnabled;
                }
            }

            static class Profile {

                private final long lastUpdateTimestamp;
                private final String givenName;
                private final String familyName;
                private final String about;
                private final String aboutEmoji;
                private final String avatarUrlPath;
                private final String mobileCoinAddress;
                private final String unidentifiedAccessMode;
                private final Set<String> capabilities;

                @SuppressWarnings("unused")
                public Profile(@JsonProperty("lastUpdateTimestamp") long lastUpdateTimestamp,
                        @JsonProperty("givenName") String givenName, @JsonProperty("familyName") String familyName,
                        @JsonProperty("about") String about, @JsonProperty("aboutEmoji") String aboutEmoji,
                        @JsonProperty("avatarUrlPath") String avatarUrlPath,
                        @JsonProperty("mobileCoinAddress") String mobileCoinAddress,
                        @JsonProperty("unidentifiedAccessMode") String unidentifiedAccessMode,
                        @JsonProperty("capabilities") Set<String> capabilities) {
                    super();
                    this.lastUpdateTimestamp = lastUpdateTimestamp;
                    this.givenName = givenName;
                    this.familyName = familyName;
                    this.about = about;
                    this.aboutEmoji = aboutEmoji;
                    this.avatarUrlPath = avatarUrlPath;
                    this.mobileCoinAddress = mobileCoinAddress;
                    this.unidentifiedAccessMode = unidentifiedAccessMode;
                    this.capabilities = capabilities;
                }

                @SuppressWarnings("unused")
                public long lastUpdateTimestamp() {
                    return lastUpdateTimestamp;
                }

                @SuppressWarnings("unused")
                public String givenName() {
                    return givenName;
                }

                @SuppressWarnings("unused")
                public String familyName() {
                    return familyName;
                }

                @SuppressWarnings("unused")
                public String about() {
                    return about;
                }

                @SuppressWarnings("unused")
                public String aboutEmoji() {
                    return aboutEmoji;
                }

                @SuppressWarnings("unused")
                public String avatarUrlPath() {
                    return avatarUrlPath;
                }

                @SuppressWarnings("unused")
                public String mobileCoinAddress() {
                    return mobileCoinAddress;
                }

                @SuppressWarnings("unused")
                public String unidentifiedAccessMode() {
                    return unidentifiedAccessMode;
                }

                @SuppressWarnings("unused")
                public Set<String> capabilities() {
                    return capabilities;
                }

            }

            @SuppressWarnings("unused")
            public long id() {
                return id;
            }

            @SuppressWarnings("unused")
            public String number() {
                return number;
            }

            @SuppressWarnings("unused")
            public String uuid() {
                return uuid;
            }

            @SuppressWarnings("unused")
            public String profileKey() {
                return profileKey;
            }

            @SuppressWarnings("unused")
            public String expiringProfileKeyCredential() {
                return expiringProfileKeyCredential;
            }

            @SuppressWarnings("unused")
            public Contact contact() {
                return contact;
            }

            @SuppressWarnings("unused")
            public Profile profile() {
                return profile;
            }
        }

        @SuppressWarnings("unused")
        public List<Recipient> getRecipients() {
            return recipients;
        }

        @SuppressWarnings("unused")
        public long getLastId() {
            return lastId;
        }
    }
}
