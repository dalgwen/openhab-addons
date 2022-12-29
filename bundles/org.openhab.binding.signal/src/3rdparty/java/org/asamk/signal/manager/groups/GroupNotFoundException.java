package org.asamk.signal.manager.groups;

@SuppressWarnings("serial")
public class GroupNotFoundException extends Exception {

    public GroupNotFoundException(GroupId groupId) {
        super("Group not found: " + groupId.toBase64());
    }
}
