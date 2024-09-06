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

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.automation.java223.common.InjectBinding;

/**
 * Base class : DTO object to facilitate input injection when used as an argument in a rule annotated method
 *
 * @author Gwendal Roulleau - Initial contribution
 */
@NonNullByDefault
public abstract class EventInfo {

    @InjectBinding()
    protected @NonNullByDefault({}) Map<String, ?> inputs;

    public Map<String, ?> getAllInputs() {
        return inputs;
    }
}
