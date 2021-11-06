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

/**
 *
 * Extracted from SMSLib
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
public class Statistics {
    int totalSent = 0;

    int totalFailed = 0;

    int totalReceived = 0;

    int totalFailures = 0;

    Date startTime = new Date();

    public int getTotalSent() {
        return this.totalSent;
    }

    public int getTotalFailed() {
        return this.totalFailed;
    }

    public int getTotalReceived() {
        return this.totalReceived;
    }

    public int getTotalFailures() {
        return this.totalFailures;
    }

    public Date getStartTime() {
        return new Date(this.startTime.getTime());
    }

    public synchronized void increaseTotalSent() {
        this.totalSent++;
    }

    public synchronized void increaseTotalFailed() {
        this.totalFailed++;
    }

    public synchronized void increaseTotalReceived() {
        this.totalReceived++;
    }

    public synchronized void increaseTotalFailures() {
        this.totalFailures++;
    }
}
