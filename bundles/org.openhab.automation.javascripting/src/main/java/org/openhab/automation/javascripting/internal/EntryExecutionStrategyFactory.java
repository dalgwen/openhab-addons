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

import javax.script.ScriptException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.automation.javascripting.scriptsupport.Script;

import ch.obermuhlner.scriptengine.java.execution.ExecutionStrategy;
import ch.obermuhlner.scriptengine.java.execution.ExecutionStrategyFactory;

/**
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
            try {
                if (instance instanceof Script) {
                    Script script = (Script) instance;
                    return script.eval();
                } else {
                    String simpleName = instance == null ? "unknown" : instance.getClass().getSimpleName();
                    throw new ScriptException(
                            String.format("cannot execute: %s not instance of %s", simpleName, Script.class.getName()));
                }

            } catch (Exception e) {
                throw new ScriptException(e);
            }
        }
    }
}
