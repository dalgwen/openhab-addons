/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.smslib.groups;

import java.util.HashMap;

/**
 *
 * Extracted from SMSLib
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */

public class GroupManager {
    HashMap<String, Group> groupList = null;

    public GroupManager() {
        this.groupList = new HashMap<>();
    }

    public void addGroup(Group g) {
        this.groupList.put(g.getName(), g);
    }

    public void removeGroup(Group g) {
        removeGroup(g.getName());
    }

    public void removeGroup(String groupName) {
        this.groupList.remove(groupName);
    }

    public Group getGroup(String groupName) {
        return this.groupList.get(groupName);
    }

    public boolean exist(String groupName) {
        return (this.groupList.containsKey(groupName));
    }
}
