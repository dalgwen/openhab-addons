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

import java.util.LinkedList;
import java.util.List;

import org.smslib.message.MsIsdn;

/**
 *
 * Extracted from SMSLib
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */

public class Group {
    String name = null;

    String description = null;

    List<MsIsdn> addressList = null;

    public Group(String name, String description) {
        this.name = name;
        this.description = description;
        this.addressList = new LinkedList<>();
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return this.description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean addAddress(String msisdn) {
        return addAddress(new MsIsdn(msisdn));
    }

    public boolean addAddress(MsIsdn msisdn) {
        return this.addressList.add(msisdn);
    }

    public boolean removeAddress(String msisdn) {
        return removeAddress(new MsIsdn(msisdn));
    }

    public boolean removeAddress(MsIsdn msisdn) {
        return this.addressList.remove(msisdn);
    }

    public List<MsIsdn> getRecipients() {
        return this.addressList;
    }
}
