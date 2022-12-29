package org.asamk.signal.manager.api;


import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;
import java.util.stream.Collectors;

import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupInviteLinkUrl;
import org.asamk.signal.manager.groups.GroupPermission;
import org.asamk.signal.manager.helper.RecipientAddressResolver;
import org.asamk.signal.manager.storage.groups.GroupInfo;
import org.asamk.signal.manager.storage.recipients.RecipientId;

public class Group {
    private final GroupId groupId;
    private final String title;
    private final String description;
    private final GroupInviteLinkUrl groupInviteLinkUrl;
    private final Set<RecipientAddress> members;
    private final Set<RecipientAddress> pendingMembers;
    private final Set<RecipientAddress> requestingMembers;
    private final Set<RecipientAddress> adminMembers;
    private final Set<RecipientAddress> bannedMembers;
    private final boolean isBlocked;
    private final int messageExpirationTimer;
    private final GroupPermission permissionAddMember;
    private final GroupPermission permissionEditDetails;
    private final GroupPermission permissionSendMessage;
    private final boolean isMember;
    private final boolean isAdmin;

    public Group(@JsonProperty("groupId") GroupId groupId, @JsonProperty("title") String title, @JsonProperty("description") String description, @JsonProperty("groupInviteLinkUrl") GroupInviteLinkUrl groupInviteLinkUrl,
            @JsonProperty("members") Set<RecipientAddress> members, @JsonProperty("pendingMembers") Set<RecipientAddress> pendingMembers,
            @JsonProperty("requestingMembers") Set<RecipientAddress> requestingMembers, @JsonProperty("adminMembers") Set<RecipientAddress> adminMembers,
            @JsonProperty("bannedMembers") Set<RecipientAddress> bannedMembers, @JsonProperty("isBlocked") boolean isBlocked, @JsonProperty("messageExpirationTimer") int messageExpirationTimer,
            @JsonProperty("permissionAddMember") GroupPermission permissionAddMember, @JsonProperty("permissionEditDetails") GroupPermission permissionEditDetails,
            @JsonProperty("permissionSendMessage") GroupPermission permissionSendMessage, @JsonProperty("isMember") boolean isMember, @JsonProperty("isAdmin") boolean isAdmin) {
        super();
        this.groupId = groupId;
        this.title = title;
        this.description = description;
        this.groupInviteLinkUrl = groupInviteLinkUrl;
        this.members = members;
        this.pendingMembers = pendingMembers;
        this.requestingMembers = requestingMembers;
        this.adminMembers = adminMembers;
        this.bannedMembers = bannedMembers;
        this.isBlocked = isBlocked;
        this.messageExpirationTimer = messageExpirationTimer;
        this.permissionAddMember = permissionAddMember;
        this.permissionEditDetails = permissionEditDetails;
        this.permissionSendMessage = permissionSendMessage;
        this.isMember = isMember;
        this.isAdmin = isAdmin;
    }

    public static Group from(final GroupInfo groupInfo, final RecipientAddressResolver recipientStore,
            final RecipientId selfRecipientId) {
        return new Group(groupInfo.getGroupId(), groupInfo.getTitle(), groupInfo.getDescription(),
                groupInfo.getGroupInviteLink(),
                groupInfo.getMembers().stream().map(recipientStore::resolveRecipientAddress)
                        .map(org.asamk.signal.manager.storage.recipients.RecipientAddress::toApiRecipientAddress)
                        .collect(Collectors.toSet()),
                groupInfo.getPendingMembers().stream().map(recipientStore::resolveRecipientAddress)
                        .map(org.asamk.signal.manager.storage.recipients.RecipientAddress::toApiRecipientAddress)
                        .collect(Collectors.toSet()),
                groupInfo.getRequestingMembers().stream().map(recipientStore::resolveRecipientAddress)
                        .map(org.asamk.signal.manager.storage.recipients.RecipientAddress::toApiRecipientAddress)
                        .collect(Collectors.toSet()),
                groupInfo.getAdminMembers().stream().map(recipientStore::resolveRecipientAddress)
                        .map(org.asamk.signal.manager.storage.recipients.RecipientAddress::toApiRecipientAddress)
                        .collect(Collectors.toSet()),
                groupInfo.getBannedMembers().stream().map(recipientStore::resolveRecipientAddress)
                        .map(org.asamk.signal.manager.storage.recipients.RecipientAddress::toApiRecipientAddress)
                        .collect(Collectors.toSet()),
                groupInfo.isBlocked(), groupInfo.getMessageExpirationTimer(), groupInfo.getPermissionAddMember(),
                groupInfo.getPermissionEditDetails(), groupInfo.getPermissionSendMessage(),
                groupInfo.isMember(selfRecipientId), groupInfo.isAdmin(selfRecipientId));
    }

    public GroupId groupId() {
        return groupId;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public GroupInviteLinkUrl groupInviteLinkUrl() {
        return groupInviteLinkUrl;
    }

    public Set<RecipientAddress> members() {
        return members;
    }

    public Set<RecipientAddress> pendingMembers() {
        return pendingMembers;
    }

    public Set<RecipientAddress> requestingMembers() {
        return requestingMembers;
    }

    public Set<RecipientAddress> adminMembers() {
        return adminMembers;
    }

    public Set<RecipientAddress> bannedMembers() {
        return bannedMembers;
    }

    public boolean isBlocked() {
        return isBlocked;
    }

    public int messageExpirationTimer() {
        return messageExpirationTimer;
    }

    public GroupPermission permissionAddMember() {
        return permissionAddMember;
    }

    public GroupPermission permissionEditDetails() {
        return permissionEditDetails;
    }

    public GroupPermission permissionSendMessage() {
        return permissionSendMessage;
    }

    public boolean isMember() {
        return isMember;
    }

    public boolean isAdmin() {
        return isAdmin;
    }
}
