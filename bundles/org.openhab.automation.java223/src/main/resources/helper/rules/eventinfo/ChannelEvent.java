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
package helper.rules.eventinfo;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.automation.java223.common.InjectBinding;

/**
 * @author Gwendal Roulleau - Initial contribution
 *         DTO object to facilitate input injection when used as an argument in a rule annotated method
 */
@NonNullByDefault
public class ChannelEvent extends EventInfo {

    @InjectBinding
    protected @NonNullByDefault({}) String channelUUID;

    @InjectBinding
    protected @NonNullByDefault({}) String event;

    public String getChannelUUID() {
        return channelUUID;
    }

    public String getEvent() {
        return event;
    }

}
