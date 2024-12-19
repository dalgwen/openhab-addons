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
package org.openhab.automation.java223.internal;

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.automation.java223.common.Java223Exception;

/**
 * Custom java compiled script instance wrapping additional information
 *
 * @author Gwendal Roulleau - Initial contribution
 */
@NonNullByDefault
public class Java223CompiledScriptInstanceWrapper {

    @Nullable
    private Map<String, Object> bindings;

    @Nullable
    private Object wrappedScriptInstance;

    private final Class<?> wrappedScriptClass;

    public Java223CompiledScriptInstanceWrapper(Class<?> wrappedScriptClass) {
        this.wrappedScriptClass = wrappedScriptClass;
    }

    public Map<String, Object> getBindings() {
        var localBindings = bindings;
        if (localBindings != null) {
            return localBindings;
        } else {
            throw new Java223Exception("Getting bindings before being set ! Should not happened");
        }
    }

    public void setBindings(@Nullable Map<String, Object> bindings) {
        this.bindings = bindings;
    }

    @Nullable
    public Object getWrappedScriptInstance() {
        return wrappedScriptInstance;
    }

    public void setWrappedScriptInstance(Object wrappedScriptInstance) {
        this.wrappedScriptInstance = wrappedScriptInstance;
    }

    public Class<?> getCompiledClass() {
        return wrappedScriptClass;
    }
}
