/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
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

import java.util.HashMap;
import java.util.Map;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.automation.java223.common.Java223Exception;
import org.openhab.automation.java223.internal.strategy.Java223Strategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.obermuhlner.scriptengine.java.JavaCompiledScript;
import ch.obermuhlner.scriptengine.java.JavaScriptEngine;

/**
 * Custom java compiled script instance wrapping additional information
 *
 * @author Gwendal Roulleau - Initial contribution
 */
@NonNullByDefault
public class Java223CompiledScript extends JavaCompiledScript {

    private final Logger logger = LoggerFactory.getLogger(Java223CompiledScript.class);

    // overwrite compiledInstance from super class
    /**
     * Write access mandatory for setting instance after creation.
     */
    @Nullable
    private Object java223CompiledInstance;

    private final Java223Strategy java223Strategy;

    /**
     * Construct a {@link JavaCompiledScript}.
     *
     * @param engine the {@link JavaScriptEngine} that compiled this script
     * @param compiledClass the compiled {@link Class}
     * @param java223Strategy the {@link Java223Strategy}
     */
    public Java223CompiledScript(JavaScriptEngine engine, Class<?> compiledClass, Java223Strategy java223Strategy) {
        super(engine, compiledClass, null, java223Strategy, java223Strategy);
        this.java223Strategy = java223Strategy;
    }

    @Override
    public @Nullable Object eval(@Nullable ScriptContext context) throws ScriptException {

        // prepare bindings data
        if (context == null) {
            throw new IllegalArgumentException("ScriptContext must not be null");
        }
        Bindings globalBindings = context.getBindings(ScriptContext.GLOBAL_SCOPE);
        Bindings engineBindings = context.getBindings(ScriptContext.ENGINE_SCOPE);
        Map<String, Object> mergedBindings = new HashMap<>();
        if (globalBindings != null) {
            mergedBindings.putAll(globalBindings);
        }
        if (engineBindings != null) {
            mergedBindings.putAll(engineBindings);
        }
        java223Strategy.associateBindings(null, null, mergedBindings);

        try {
            // instantiate the script
            Object compiledInstance = java223Strategy.construct(this, mergedBindings);

            // execute
            return java223Strategy.execute(compiledInstance, mergedBindings);
        } catch (Java223Exception e) {
            // keep responsibility of logging full stack trace, as ScriptException cannot contain cause
            // and caller sometimes does not do it well
            logger.error("Exception during evaluation of a java223 script: {}", e.getMessage(), e);
            // and sending only the message upstream
            throw new ScriptException(e.getMessage());
        }
    }

    @Override
    public @Nullable Object getCompiledInstance() {
        return java223CompiledInstance;
    }

    public void setCompiledInStance(Object compiledInstance) {
        this.java223CompiledInstance = compiledInstance;
    }
}
