/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
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
package org.openhab.automation.javascripting.eventinfo;

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.automation.javascripting.scriptsupport.JavaScriptingException;

/**
 * @author Gwendal Roulleau - Initial contribution
 */
@NonNullByDefault
public abstract class EventInfo {

    protected final Map<String, ?> inputs;

    public EventInfo(Map<String, ?> inputs) {
        this.inputs = inputs;
    }

    protected Object shouldNotBeNull(String inputParameterName) {
        return shouldNotBeNull(inputs.get(inputParameterName), inputParameterName);
    }

    protected <T> T shouldNotBeNull(@Nullable T inputParameter, String inputParameterName) {
        if (inputParameter == null) {
            throw new JavaScriptingException("Cannot create parameter object " + this.getClass().getName()
                    + " because mandatory parameter " + inputParameterName
                    + " is null. Do you use the right event info object as an argument of your rule ? inputs parameters of this event are : "
                    + inputs.toString());
        } else {
            return inputParameter;
        }
    }

    public Map<String, ?> getAllInputs() {
        return inputs;
    }
}
