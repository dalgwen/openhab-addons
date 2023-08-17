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

import org.openhab.automation.javascripting.scriptsupport.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.obermuhlner.scriptengine.java.execution.ExecutionStrategy;
import ch.obermuhlner.scriptengine.java.execution.ExecutionStrategyFactory;

/**
 * @author Jürgen Weber - Initial contribution
 */

public class EntryExecutionStrategyFactory implements ExecutionStrategyFactory {

    private static Logger logger = LoggerFactory.getLogger(EntryExecutionStrategyFactory.class);

    @Override
    public ExecutionStrategy create(Class<?> clazz) throws ScriptException {

        return new ExecutionStrategy() {

            @Override
            public Object execute(Object instance) throws ScriptException {

                Object retval = null;

                try {
                    if (instance instanceof Script) {
                        Script script = (Script) instance;

                        retval = script.eval();
                    } else {
                        throw new ScriptException(String.format("cannot execute: %s not instance of %s",
                                instance.getClass().getSimpleName(), Script.class.getName()));
                    }
                    return retval;

                } catch (Exception e) {
                    throw new ScriptException(e);
                }
            }
        };
    }
}
