/**
 * Copyright (c) 2021-2023 Contributors to the SmartHome/J project
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

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.script.Invocable;
import javax.script.ScriptException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.automation.java223.common.ScriptLoadedTrigger;
import org.openhab.automation.java223.common.ScriptUnloadedTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.obermuhlner.scriptengine.java.JavaCompiledScript;
import ch.obermuhlner.scriptengine.java.JavaScriptEngine;

/**
 * This class add the Invocable aspect to the JavaScriptEngine from obermuhlner's base class
 * The Invocable aspect adds the ability to be called when loaded and unloaded script event
 * are triggered.
 *
 * @author Gwendal Roulleau - Initial contribution
 */
@NonNullByDefault
public class Java223ScriptEngine extends JavaScriptEngine implements Invocable {
    private final Logger logger = LoggerFactory.getLogger(Java223ScriptEngine.class);

    private @Nullable JavaCompiledScript lastCompiledScript;

    @Override
    public JavaCompiledScript compile(@Nullable String script) throws ScriptException {
        JavaCompiledScript localLastCompiledScript = super.compile(script);
        lastCompiledScript = localLastCompiledScript;
        if (localLastCompiledScript != null) {
            return localLastCompiledScript;
        } else {
            throw new ScriptException("Compile result is null. Should not happened");
        }
    }

    @Override
    public @Nullable Object invokeMethod(@Nullable Object o, @Nullable String name, Object @Nullable... args)
            throws NoSuchMethodException {
        throw new NoSuchMethodException("not implemented");
    }

    @Override
    public @Nullable Object invokeFunction(@Nullable String name, Object @Nullable... args) throws ScriptException {

        // here we assume that the script engine served only once and that the wanted compiled script is the last one
        JavaCompiledScript compiledScript = this.lastCompiledScript;
        if (compiledScript == null || name == null) {
            return null;
        }

        Class<? extends Annotation> annotation;
        switch (name) {
            case "scriptLoaded":
                annotation = ScriptLoadedTrigger.class;
                break;
            case "scriptUnloaded":
                annotation = ScriptUnloadedTrigger.class;
                break;
            default:
                throw new ScriptException(name + " is not an allowed method in java223");
        }

        Object compiledInstance = compiledScript.getCompiledInstance();
        for (Method method : compiledInstance.getClass().getMethods()) {
            Annotation scriptLoadedOrUnloadedAnnotation = method.getAnnotation(annotation);
            if (scriptLoadedOrUnloadedAnnotation != null) {
                if (method.getParameters().length != 0) {
                    throw new ScriptException("Method " + method.getName()
                            + " called by ScriptLoaded/ScriptUnloaded trigger should not have any argument");
                } else {
                    try {
                        method.invoke(compiledInstance);
                    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                        logger.warn("Method " + method.getName()
                                + " cannot be called by ScriptLoaded/ScriptUnloaded trigger");
                        throw new ScriptException(e);
                    }
                }
            }
        }
        return null;
    }

    @Override
    @SuppressWarnings("null")
    public <T> T getInterface(@Nullable Class<T> clazz) {
        return null;
    }

    @Override
    @SuppressWarnings("null")
    public <T> T getInterface(@Nullable Object o, @Nullable Class<T> clazz) {
        return null;
    }
}
