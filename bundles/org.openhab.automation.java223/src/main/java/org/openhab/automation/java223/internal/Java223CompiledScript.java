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
import org.openhab.automation.java223.internal.strategy.Java223Strategy;

import ch.obermuhlner.scriptengine.java.JavaCompiledScript;
import ch.obermuhlner.scriptengine.java.JavaScriptEngine;
import ch.obermuhlner.scriptengine.java.execution.ExecutionStrategy;

/**
 * Custom java compiled script instance wrapping additional information
 *
 * @author Gwendal Roulleau - Initial contribution
 */
@NonNullByDefault
public class Java223CompiledScript extends JavaCompiledScript {

    /**
     * Store binding data is mandatory to defer instantiation
     */
    @Nullable
    private Map<String, Object> bindings;

    // overwrite compiledInstance from super class
    /**
     * Write access mandatory for setting instance after creation.
     */
    @Nullable
    private Object java223CompiledInstance;

    /**
     * Construct a {@link JavaCompiledScript}.
     *
     * @param engine the {@link JavaScriptEngine} that compiled this script
     * @param compiledClass the compiled {@link Class}
     * @param java223Strategy the {@link ExecutionStrategy}
     */
    public Java223CompiledScript(JavaScriptEngine engine, Class<?> compiledClass, Java223Strategy java223Strategy) {
        super(engine, compiledClass, null, java223Strategy, java223Strategy);
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

    @Override
    public @Nullable Object getCompiledInstance() {
        return java223CompiledInstance;
    }

    public void setCompiledInStance(Object compiledInstance) {
        this.java223CompiledInstance = compiledInstance;
    }
}
