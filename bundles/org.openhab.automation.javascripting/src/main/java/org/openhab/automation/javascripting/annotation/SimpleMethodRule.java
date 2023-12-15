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

package org.openhab.automation.javascripting.annotation;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.automation.javascripting.eventinfo.EventInfo;
import org.openhab.automation.javascripting.scriptsupport.Script;
import org.openhab.core.automation.Action;
import org.openhab.core.automation.module.script.rulesupport.shared.simple.SimpleRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Gwendal Roulleau - Initial contribution
 */
@NonNullByDefault
public class SimpleMethodRule extends SimpleRule {

    private static Logger logger = LoggerFactory.getLogger(SimpleMethodRule.class);

    private Script script;
    private Method method;
    private Class<?> eventInfoType;

    public SimpleMethodRule(Script script, Method method) throws RuleParserException {
        this.script = script;
        this.method = method;

        Parameter[] parameters = method.getParameters();
        if (parameters.length != 1) {
            throw new RuleParserException("We should have only one argument in method " + method.getName());
        }

        Parameter parameter = parameters[0];
        this.eventInfoType = parameter.getType();
        if (!EventInfo.class.isAssignableFrom(eventInfoType) && !Map.class.isAssignableFrom(eventInfoType)) {
            throw new RuleParserException("Argument of method " + method.getName() + "must be a map or an EventInfo");
        }
    }

    @Override
    public Object execute(Action module, Map<String, ?> inputs) {
        Object returnObject = null;
        try {
            Object methodParameter = inputs;
            if (EventInfo.class.isAssignableFrom(eventInfoType)) {
                methodParameter = eventInfoType.getDeclaredConstructor().newInstance();
                ((EventInfo) methodParameter).fill(inputs);
            }
            returnObject = method.invoke(script, methodParameter);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | InstantiationException
                | NoSuchMethodException | SecurityException e) {
            logger.error("Cannot execute rule {}", method.getName(), e);
        }
        return returnObject == null ? "" : returnObject;
    }
}
