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

package org.openhab.automation.javascripting.internal;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

import javax.script.ScriptException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.automation.javascripting.scriptsupport.Scriptable;

import ch.obermuhlner.scriptengine.java.execution.ExecutionStrategy;
import ch.obermuhlner.scriptengine.java.execution.ExecutionStrategyFactory;

/**
 * Run a java file by searching a suitable entry point
 * Suitable entry point are the following method : eval, main, run,
 *
 * @author Jürgen Weber - Initial contribution
 */
@NonNullByDefault
public class EntryExecutionStrategyFactory implements ExecutionStrategyFactory {

    private final static ExecutionStrategy scriptExecutionStrategy = new ScriptExecutionStrategy();

    @Override
    public ExecutionStrategy create(@Nullable Class<?> clazz) throws ScriptException {
        return scriptExecutionStrategy;
    }

    private static class ScriptExecutionStrategy implements ExecutionStrategy {

        @Override
        public @Nullable Object execute(@Nullable Object instance) throws ScriptException {
            if (instance == null) {
                throw new ScriptException("Cannot run null class/instance");
            }
            try {
                if (instance instanceof Scriptable script) {
                    return script.eval();
                } else {
                    return searchAndRunEntryPointByReflection(instance);
                }
            } catch (Exception e) {
                throw new ScriptException(e);
            }
        }

        @Nullable
        private static Object searchAndRunEntryPointByReflection(Object instance) throws ScriptException {
            for (String methodName : Arrays.asList("eval", "main", "run")) {
                try {
                    Method evalMethod = instance.getClass().getMethod(methodName);
                    return evalMethod.invoke(instance);
                } catch (NoSuchMethodException nse) {
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                    String simpleName = instance.getClass().getSimpleName();
                    throw new ScriptException(
                            String.format("Error executing entry point %s in %s", methodName, simpleName, e));
                }
            }
            String simpleName = instance.getClass().getSimpleName();
            throw new ScriptException(
                    String.format("cannot execute: %s not instance of %s or didn't have an eval method", simpleName,
                            Scriptable.class.getName()));
        }
    }
}
