/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
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
package org.openhab.binding.signal.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.signal.internal.protocol.RegistrationType;
import org.openhab.binding.signal.internal.protocol.SignalService;

/**
 * The {@link SignalBridgeConfiguration} class contains fields mapping bridge configuration parameters.
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
public class SignalBridgeConfiguration {

    // account bridge configuration
    public String phoneNumber = "";
    public String captcha = "";
    public RegistrationType verificationCodeMethod = RegistrationType.PhoneCall;
    public String verificationCode = "";
    public String userAgent = SignalService.DEFAULT_USER_AGENT;

    // linked bridge configuration
    public String deviceName = "";
}
