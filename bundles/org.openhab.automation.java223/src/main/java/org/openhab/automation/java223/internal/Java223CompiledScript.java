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

import ch.obermuhlner.scriptengine.java.JavaCompiledScript;
import ch.obermuhlner.scriptengine.java.JavaScriptEngine;

/**
 * Custom java compiled script instance wrapping additional information
 *
 * @author Gwendal Roulleau - Initial contribution
 */
@NonNullByDefault
public class Java223CompiledScript extends JavaCompiledScript {

    // overwrite compiledInstance from super class
    /**
     * Write access mandatory for setting instance after creation.
     */
    @Nullable
    private Object java223CompiledInstance;

    private Class<?> java223CompiledClass;

    /**
     * Hold the script source if, and only if, the script should be recompiled the next time it is necessary
     */
    @Nullable
    private String recompileScriptSource = null;

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
        this.java223CompiledClass = compiledClass;
        this.java223Strategy = java223Strategy;
    }

    @Override
    public synchronized Class<?> getCompiledClass() {
        try {
            return getCompiledClassSafe();
        } catch (ScriptException e) {
            throw new Java223Exception("Cannot recompile class", e);
        }
    }

    /**
     * Get the class, possibly recompiling it if necessary
     * 
     * @return The compiled class
     * @throws ScriptException Only when the script should be recompiled and there is an error during it.
     */
    public synchronized Class<?> getCompiledClassSafe() throws ScriptException {
        Class<?> localCompiledClass = java223CompiledClass;
        String localRecompileScriptSource = recompileScriptSource;
        if (localRecompileScriptSource != null) { // a recompilation has been asked
            this.java223CompiledInstance = null;
            localCompiledClass = ((Java223ScriptEngine) getEngine()).internalCompilation(localRecompileScriptSource);
            this.java223CompiledClass = localCompiledClass;
            this.recompileScriptSource = null;
        }
        return localCompiledClass;
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

        // instantiate the script
        Object compiledInstance = java223Strategy.construct(this, mergedBindings);

        // execute
        return java223Strategy.execute(compiledInstance, mergedBindings);
    }

    @Override
    public @Nullable Object getCompiledInstance() {
        return java223CompiledInstance;
    }

    public void invalidate(String scriptSource) {
        this.recompileScriptSource = scriptSource;
    }

    public void setCompiledInStance(Object compiledInstance) {
        this.java223CompiledInstance = compiledInstance;
    }
}
