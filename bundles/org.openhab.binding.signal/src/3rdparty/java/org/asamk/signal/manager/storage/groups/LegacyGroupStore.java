package org.asamk.signal.manager.storage.groups;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupIdV1;
import org.asamk.signal.manager.groups.GroupIdV2;
import org.asamk.signal.manager.storage.recipients.RecipientAddress;
import org.asamk.signal.manager.storage.recipients.RecipientResolver;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.groups.GroupMasterKey;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.push.DistributionId;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.internal.util.Hex;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

public class LegacyGroupStore {

    private final static Logger logger = LoggerFactory.getLogger(LegacyGroupStore.class);

    public static void migrate(final Storage storage, final File groupCachePath,
            final RecipientResolver recipientResolver, final GroupStore groupStore) {
        final var groups = storage.groups.stream().map(g -> {
            if (g instanceof Storage.GroupV1) {
                Storage.GroupV1 g1 = ((Storage.GroupV1) g);
                final var members = g1.members.stream().map(m -> {
                    if (m.recipientId == null) {
                        return recipientResolver
                                .resolveRecipient(new RecipientAddress(ServiceId.parseOrNull(m.uuid), m.number));
                    }

                    return recipientResolver.resolveRecipient(m.recipientId);
                }).filter(Objects::nonNull).collect(Collectors.toSet());

                return new GroupInfoV1(GroupIdV1.fromBase64(g1.groupId),
                        g1.expectedV2Id == null ? null : GroupIdV2.fromBase64(g1.expectedV2Id), g1.name, members,
                        g1.color, g1.messageExpirationTime, g1.blocked, g1.archived);
            }

            final var g2 = (Storage.GroupV2) g;
            var groupId = GroupIdV2.fromBase64(g2.groupId);
            GroupMasterKey masterKey;
            try {
                masterKey = new GroupMasterKey(Base64.getDecoder().decode(g2.masterKey));
            } catch (InvalidInputException | IllegalArgumentException e) {
                throw new AssertionError("Invalid master key for group " + groupId.toBase64());
            }

            return new GroupInfoV2(groupId, masterKey, loadDecryptedGroupLocked(groupId, groupCachePath),
                    g2.distributionId == null ? DistributionId.create() : DistributionId.from(g2.distributionId),
                    g2.blocked, g2.permissionDenied, recipientResolver);
        }).collect(Collectors.toList());

        groupStore.addLegacyGroups(groups);
        removeGroupCache(groupCachePath);
    }

    private static void removeGroupCache(File groupCachePath) {
        final var files = groupCachePath.listFiles();
        if (files == null) {
            return;
        }

        for (var file : files) {
            try {
                Files.delete(file.toPath());
            } catch (IOException e) {
                logger.error("Failed to delete group cache file {}: {}", file, e.getMessage());
            }
        }
        try {
            Files.delete(groupCachePath.toPath());
        } catch (IOException e) {
            logger.error("Failed to delete group cache directory {}: {}", groupCachePath, e.getMessage());
        }
    }

    private static DecryptedGroup loadDecryptedGroupLocked(final GroupIdV2 groupIdV2, final File groupCachePath) {
        var groupFile = getGroupV2File(groupIdV2, groupCachePath);
        if (!groupFile.exists()) {
            groupFile = getGroupV2FileLegacy(groupIdV2, groupCachePath);
        }
        if (!groupFile.exists()) {
            return null;
        }
        try (var stream = new FileInputStream(groupFile)) {
            return DecryptedGroup.parseFrom(stream);
        } catch (IOException ignored) {
            return null;
        }
    }

    private static File getGroupV2FileLegacy(final GroupId groupId, final File groupCachePath) {
        return new File(groupCachePath, Hex.toStringCondensed(groupId.serialize()));
    }

    private static File getGroupV2File(final GroupId groupId, final File groupCachePath) {
        return new File(groupCachePath, groupId.toBase64().replace("/", "_"));
    }

    public static class Storage {
        @JsonDeserialize(using = GroupsDeserializer.class)
        public List<GroupVx> groups;

        private static class GroupV1 extends GroupVx {
            private final String groupId;
            private final String expectedV2Id;
            private final String name;
            private final String color;
            private final int messageExpirationTime;
            private final boolean blocked;
            private final boolean archived;
            @JsonDeserialize(using = MembersDeserializer.class)
            List<Member> members;

            @SuppressWarnings("unused")
            public GroupV1(@JsonProperty("groupId") String groupId, @JsonProperty("expectedV2Id") String expectedV2Id,
                    @JsonProperty("name") String name, @JsonProperty("color") String color,
                    @JsonProperty("messageExpirationTime") int messageExpirationTime,
                    @JsonProperty("blocked") boolean blocked, @JsonProperty("archived") boolean archived,
                    @JsonProperty("members") List<Member> members) {
                super();
                this.groupId = groupId;
                this.expectedV2Id = expectedV2Id;
                this.name = name;
                this.color = color;
                this.messageExpirationTime = messageExpirationTime;
                this.blocked = blocked;
                this.archived = archived;
                this.members = members;
            }

            static class Member {
                private final Long recipientId;
                private final String uuid;
                private final String number;

                public Member(@JsonProperty("recipientId") Long recipientId, @JsonProperty("uuid") String uuid,
                        @JsonProperty("number") String number) {
                    super();
                    this.recipientId = recipientId;
                    this.uuid = uuid;
                    this.number = number;
                }

                @SuppressWarnings("unused")
                public Long recipientId() {
                    return recipientId;
                }

                @SuppressWarnings("unused")
                public String uuid() {
                    return uuid;
                }

                @SuppressWarnings("unused")
                public String number() {
                    return number;
                }

            }

            static class JsonRecipientAddress {
                private final String uuid;
                private final String number;

                @SuppressWarnings("unused")
                public JsonRecipientAddress(@JsonProperty("uuid") String uuid, @JsonProperty("number") String number) {
                    super();
                    this.uuid = uuid;
                    this.number = number;
                }

                @SuppressWarnings("unused")
                public String uuid() {
                    return uuid;
                }

                @SuppressWarnings("unused")
                public String number() {
                    return number;
                }

            }

            static class MembersDeserializer extends JsonDeserializer<List<Member>> {

                @Override
                public List<Member> deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
                        throws IOException {
                    var addresses = new ArrayList<Member>();
                    JsonNode node = jsonParser.getCodec().readTree(jsonParser);
                    for (var n : node) {
                        if (n.isTextual()) {
                            addresses.add(new Member(null, null, n.textValue()));
                        } else if (n.isNumber()) {
                            addresses.add(new Member(n.numberValue().longValue(), null, null));
                        } else {
                            var address = jsonParser.getCodec().treeToValue(n, JsonRecipientAddress.class);
                            addresses.add(new Member(null, address.uuid, address.number));
                        }
                    }

                    return addresses;
                }
            }

            @SuppressWarnings("unused")
            public List<Member> getMembers() {
                return members;
            }

            @SuppressWarnings("unused")
            public void setMembers(List<Member> members) {
                this.members = members;
            }

            @SuppressWarnings("unused")
            public String groupId() {
                return groupId;
            }

            @SuppressWarnings("unused")
            public String expectedV2Id() {
                return expectedV2Id;
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
        }

        static class GroupVx {

        }

        static class GroupV2 extends GroupVx {
            private final String groupId;
            private final String masterKey;
            private final String distributionId;
            @JsonInclude(JsonInclude.Include.NON_DEFAULT)
            boolean blocked;
            @JsonInclude(JsonInclude.Include.NON_DEFAULT)
            boolean permissionDenied;

            public GroupV2(@JsonProperty("groupId") String groupId, @JsonProperty("masterKey") String masterKey,
                    @JsonProperty("distributionId") String distributionId, @JsonProperty("blocked") boolean blocked,
                    @JsonProperty("permissionDenied") boolean permissionDenied) {
                super();
                this.groupId = groupId;
                this.masterKey = masterKey;
                this.distributionId = distributionId;
                this.blocked = blocked;
                this.permissionDenied = permissionDenied;
            }

            public boolean isBlocked() {
                return blocked;
            }

            public void setBlocked(boolean blocked) {
                this.blocked = blocked;
            }

            public boolean isPermissionDenied() {
                return permissionDenied;
            }

            public void setPermissionDenied(boolean permissionDenied) {
                this.permissionDenied = permissionDenied;
            }

            public String groupId() {
                return groupId;
            }

            public String masterKey() {
                return masterKey;
            }

            public String distributionId() {
                return distributionId;
            }

        }
    }

    private static class GroupsDeserializer extends JsonDeserializer<List<Object>> {

        @Override
        public List<Object> deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
                throws IOException {
            var groups = new ArrayList<>();
            JsonNode node = jsonParser.getCodec().readTree(jsonParser);
            for (var n : node) {
                Object g;
                if (n.hasNonNull("masterKey")) {
                    // a v2 group
                    g = jsonParser.getCodec().treeToValue(n, Storage.GroupV2.class);
                } else {
                    g = jsonParser.getCodec().treeToValue(n, Storage.GroupV1.class);
                }
                groups.add(g);
            }

            return groups;
        }
    }
}
