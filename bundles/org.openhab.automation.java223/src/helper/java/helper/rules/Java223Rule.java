/**
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

/**
 * Extract code to execute, from diverse runnable field or from a method
 *
 * @author Gwendal Roulleau - Initial contribution
 *
 */
@NonNullByDefault
public class Java223Rule extends SimpleRule {

    private static final Set<Class<?>> ACCEPTABLE_FIELD_MEMBER_CLASSES = Set.of(SimpleRule.class, Function.class,
            BiFunction.class, Callable.class, Runnable.class, Consumer.class, BiConsumer.class);

    private final BiFunction<Action, Map<String, Object>, @Nullable Object> codeToExecute;

    public void setUid(String uid) {
        if (!uid.isBlank()) {
            this.uid = uid;
        }
    }

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
            throw new Java223Exception("Cannot execute callable", e);
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

    /**
     * Prepare some executable code from a method
     *
     * @param script The instance to execute the method on
     * @param method The method to execute
     */
    public Java223Rule(Object script, Method method) {
        Parameter[] parameters = method.getParameters();
        codeToExecute = (module, inputs) -> {
            try {
                if (method.getParameters().length == 0) {
                    return method.invoke(script);
                } else {
                    Object @Nullable [] parameterValues = new Object @Nullable [parameters.length];
                    for (int i = 0; i < parameters.length; i++) {
                        if (parameters[i].getType().equals(Action.class)) {
                            parameterValues[i] = module;
                        } else {
                            ClassLoader classLoader = script.getClass().getClassLoader();
                            if (classLoader == null) { // should not happen
                                throw new Java223Exception("Cannot get class loader for " + script.getClass());
                            }
                            parameterValues[i] = BindingInjector.extractBindingValueForElement(classLoader,
                                    inputs, parameters[i]);
                        }
                    }
                    return method.invoke(script, parameterValues);
                }
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
                    | SecurityException e) {
                throw new Java223Exception("Cannot execute method named " + method.getName(), e);
            }
        };
    }

    /**
     * Prepare some executable code from a field
     *
     * @param script The instance to execute the method on
     * @param fieldMember The field member containing some code to execute
     */
    @SuppressWarnings({ "unchecked" })
    public Java223Rule(Object script, Field fieldMember) throws RuleParserException {
        Class<?> fieldType = fieldMember.getType();

        if (ACCEPTABLE_FIELD_MEMBER_CLASSES.stream().noneMatch(fieldType::isAssignableFrom)) {
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
            return switch (objectToExecute) {
                case null ->
                        throw new Java223Exception("Field " + fieldMember.getName() + " is null. Cannot execute anything");
                case SimpleRule simpleRule -> execute(simpleRule, module, inputs);
                case Function<?, ?> function -> execute((Function<Map<String, Object>, Object>) function, inputs);
                case BiFunction<?, ?, ?> bifunction -> execute((BiFunction<Action, Map<String, Object>, Object>) bifunction, module, inputs);
                case Callable<?> callable -> execute((Callable<Object>) callable);
                case Runnable runnable -> execute(runnable);
                case Consumer<?> consumer -> execute((Consumer<Map<String, Object>>) consumer, inputs);
                case BiConsumer<?, ?> biconsumer -> execute((BiConsumer<Action, Map<String, Object>>) biconsumer, module, inputs);
                default -> throw new Java223Exception("Wrong type of field " + fieldType + ". Should not happen");
            };
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
