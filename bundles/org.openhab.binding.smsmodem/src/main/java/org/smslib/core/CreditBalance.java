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

package org.smslib.core;

import java.util.Date;

import org.smslib.helper.Common;

/**
 *
 * Extracted from SMSLib
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */

public class CreditBalance {
    double credits;

    Date lastUpdate;

    public double getCredits() {
        return this.credits;
    }

    public void setCredits(double credits) {
        this.credits = credits;
        this.lastUpdate = new Date();
    }

    public Date getLastUpdate() {
        return (Date) this.lastUpdate.clone();
    }

    public CreditBalance() {
        this.credits = 0;
        this.lastUpdate = Common.getMinDate();
    }

    @Override
    public String toString() {
        StringBuffer b = new StringBuffer(256);
        b.append(String.format("Credits: %f%n", getCredits()));
        b.append(String.format("Last update: %s%n", getLastUpdate().toString()));
        return b.toString();
    }
}
