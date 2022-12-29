package org.asamk.signal.manager.groups;

public class LastGroupAdminException extends Exception {

    /**
     *
     */
    private static final long serialVersionUID = 6721613414539398397L;

    public LastGroupAdminException(GroupId groupId, String groupName) {
        super("User is last admin in group: " + groupName + " (" + groupId.toBase64() + ")");
    }
}
