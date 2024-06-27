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

package helper.rules;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.automation.java223.common.BindingInjector;
import org.openhab.automation.java223.common.Java223Exception;
import org.openhab.core.automation.Action;
import org.openhab.core.automation.module.script.rulesupport.shared.simple.SimpleRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extract code to execute, from diverse runnable field or method
 *
 * @author Gwendal Roulleau - Initial contribution
 *
 */
@NonNullByDefault
public class Java223Rule extends SimpleRule {

    private static final Set<Class<?>> ACCEPTABLE_FIELD_MEMBER_CLASSES = Set.of(SimpleRule.class, Function.class,
            BiFunction.class, Callable.class, Runnable.class, Consumer.class, BiConsumer.class);
    
    private static final Logger logger = LoggerFactory.getLogger(Java223Rule.class);

    private BiFunction<Action, Map<String, Object>, @Nullable Object> codeToExecute;

    @Nullable
    public Object execute(SimpleRule simpleRule, Action module, Map<String, Object> inputs) {
        return simpleRule.execute(module, inputs);
    }

    @Nullable
    public Object execute(Function<Map<String, Object>, Object> function, Map<String, Object> inputs) {
        return function.apply(inputs);
    }

    @Nullable
    public Object execute(BiFunction<Action, Map<String, Object>, Object> function, Action module,
            Map<String, Object> inputs) {
        return function.apply(module, inputs);
    }

    @Nullable
    public Object execute(Callable<Object> callable) {
        try {
            return callable.call();
        } catch (Exception e) {
            throw new Java223Exception("Cannot execute callable");
        }
    }

    @Nullable
    public Object execute(Runnable runnable) {
        runnable.run();
        return null;
    }

    @Nullable
    public Object execute(Consumer<Map<String, Object>> consumer, Map<String, Object> inputs) {
        consumer.accept(inputs);
        return null;
    }

    @Nullable
    public Object execute(BiConsumer<Action, Map<String, Object>> consumer, Action module, Map<String, Object> inputs) {
        consumer.accept(module, inputs);
        return null;
    }

    public Java223Rule(Object script, Method method) throws RuleParserException {
        Parameter[] parameters = method.getParameters();
        codeToExecute = (module, inputs) -> {
            try {
                if (method.getParameters().length == 0) {
                    return method.invoke(script);
                } else {
                    Object[] parameterValues = new Object[parameters.length];
                    for (int i = 0; i < parameters.length; i++) {
                        if (parameters[i].getType().equals(Action.class)) {
                            parameterValues[i] = module;
                        } else {
                            parameterValues[i] = BindingInjector.extractBindingValueForElement(inputs, parameters[i]);
                        }
                    }
                    return method.invoke(script, parameterValues);
                }
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
                    | SecurityException e) {
                logger.debug("Cannot execute method named {}", method.getName(), e);
                throw new Java223Exception("Cannot execute method named " + method.getName(), e);
            }
        };
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Java223Rule(Object script, Field fieldMember) throws RuleParserException {
        Class<?> fieldType = fieldMember.getType();

        if (ACCEPTABLE_FIELD_MEMBER_CLASSES.stream().noneMatch(clazz -> fieldType.isAssignableFrom(clazz))) {
            throw new RuleParserException("Field member " + fieldMember.getName() + " cannot be of class " + fieldType
                    + ". Must be " + ACCEPTABLE_FIELD_MEMBER_CLASSES.stream().map(Class::getSimpleName)
                            .collect(Collectors.joining(" or ")));
        }

        codeToExecute = (module, inputs) -> {
            Object objectToExecute;
            try {
                objectToExecute = fieldMember.get(script);
            } catch (IllegalArgumentException | IllegalAccessException e) {
                throw new Java223Exception("Cannot get field member " + fieldMember.getName() + " on object of class "
                        + script.getClass().getName(), e);
            }
            if (objectToExecute == null) {
                throw new Java223Exception("Field " + fieldMember.getName() + " is null. Cannot execute anything");
            }
            if (objectToExecute instanceof SimpleRule simpleRule) {
                return execute(simpleRule, module, inputs);
            } else if (objectToExecute instanceof Function function) {
                return execute(function, inputs);
            } else if (objectToExecute instanceof BiFunction bifunction) {
                return execute(bifunction, module, inputs);
            } else if (objectToExecute instanceof Callable callable) {
                return execute(callable);
            } else if (objectToExecute instanceof Runnable runable) {
                return execute(runable);
            } else if (objectToExecute instanceof Consumer consumer) {
                return execute(consumer, inputs);
            } else if (objectToExecute instanceof BiConsumer biconsumer) {
                return execute(biconsumer, module, inputs);
            } else {
                throw new Java223Exception("Wrong type of field " + fieldType + ". Should not happened");
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
