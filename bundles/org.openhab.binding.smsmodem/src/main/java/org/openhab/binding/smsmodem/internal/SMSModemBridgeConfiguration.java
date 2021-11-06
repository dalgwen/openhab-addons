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
package org.openhab.binding.smsmodem.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * The {@link SMSModemBridgeConfiguration} class contains fields mapping bridge configuration parameters.
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
public class SMSModemBridgeConfiguration {

    public String serialPortOrIP = "";
    public Integer baudOrNetworkPort = 9800;
    @Nullable
    public String simPin;
}
