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
package org.smslib.gateway.modem;

/**
 *
 * Extracted from SMSLib
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */

public class WatchdogUtil {

    private static ISmsWatchdog smsWatchdog;

    public static void setWatchdog(ISmsWatchdog watchdog) {
        smsWatchdog = watchdog;
    }

    public static void announceRssiPolled() {
        if (smsWatchdog != null) {
            smsWatchdog.rssiPolled();
        }
    }
}
