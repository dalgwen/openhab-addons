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

package org.openhab.automation.java223.helper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;
import java.util.function.BiFunction;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.automation.java223.helper.eventinfo.EventInfo;
import org.openhab.automation.java223.internal.strategy.Java223Strategy;
import org.openhab.core.automation.Action;
import org.openhab.core.automation.module.script.rulesupport.shared.simple.SimpleRule;
import org.openhab.core.automation.module.script.rulesupport.shared.simple.SimpleRuleActionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Gwendal Roulleau - Initial contribution
 *         Extract code to execute from simplerule or method
 */
@NonNullByDefault
public class SimpleRuleActionHandlerJava223 implements SimpleRuleActionHandler {

    private static Logger logger = LoggerFactory.getLogger(SimpleRuleActionHandlerJava223.class);

    private BiFunction<Action, Map<String, Object>, @Nullable Object> codeToExecute;

    public SimpleRuleActionHandlerJava223(SimpleRule simpleRule) throws RuleParserException {
        codeToExecute = (module, inputs) -> {
            return simpleRule.execute(module, inputs);
        };
    }

    public SimpleRuleActionHandlerJava223(Object script, Method method) throws RuleParserException {

        codeToExecute = (module, inputs) -> {
            Parameter[] parameters = method.getParameters();
            try {
                if (method.getParameters().length == 0) {
                    return method.invoke(script);
                } else {
                    Object[] parameterValues = new Object[parameters.length];
                    for (int i = 0; i < parameters.length; i++) {
                        if (EventInfo.class.isAssignableFrom(parameters[i].getType())) { // special eventInfo parameter
                                                                                         // case
                            parameterValues[i] = parameters[i].getType().getDeclaredConstructor(Map.class)
                                    .newInstance(inputs);
                        } else {
                            parameterValues[i] = Java223Strategy.extractBindingValueForElement(inputs, parameters[i]);
                        }
                    }
                    return method.invoke(script, parameterValues);
                }
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
                    | InstantiationException | NoSuchMethodException | SecurityException e) {
                throw new Java223Exception("Cannot execute method named " + method.getName(), e);
            }
        };
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object execute(Action module, Map<String, ?> inputs) {
        // special self reference :
        ((Map<String, Object>) inputs).put("inputs", inputs);
        // actual call :
        Object value = codeToExecute.apply(module, (Map<String, Object>) inputs);
        return value != null ? value : "";
    }
}
