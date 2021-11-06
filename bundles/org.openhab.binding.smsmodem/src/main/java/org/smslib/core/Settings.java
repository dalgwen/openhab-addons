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

/**
 *
 * Extracted from SMSLib
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */

public class Settings {
    public static final String LIBRARY_INFO = "SMSLib - A universal API for sms messaging";

    public static final String LIBRARY_LICENSE = "This software is distributed under the terms of the\nApache v2.0 License (http://www.apache.org/licenses/LICENSE-2.0.html).";

    public static final String LIBRARY_COPYRIGHT = "Copyright (c) 2002-2015, smslib.org";

    public static final String LIBRARY_VERSION = "dev-SNAPSHOT";

    public static int serviceDispatcherQueueTimeout = 1000;

    public static int serviceDispatcherYield = 0;

    public static int gatewayDispatcherQueueTimeout = 1000;

    public static int gatewayDispatcherYield = 0;

    public static int callbackDispatcherQueueTimeout = 1000;

    public static int callbackDispatcherYield = 0;

    public static int daemonDispatcherYield = 10000;

    public static int modemPollingInterval = 15000;

    public static int modemRssiPollingInterval = 60000;

    public static boolean keepOutboundMessagesInQueue = true;

    public static int hoursToRetainOrphanedMessageParts = 72;

    public static boolean deleteMessagesAfterCallback = false;
}
