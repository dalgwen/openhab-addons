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

import static org.openhab.automation.java223.common.Java223Constants.ANNOTATION_DEFAULT;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.automation.java223.helper.annotations.ChannelEventTrigger;
import org.openhab.automation.java223.helper.annotations.CronTrigger;
import org.openhab.automation.java223.helper.annotations.DateTimeTrigger;
import org.openhab.automation.java223.helper.annotations.DayOfWeekCondition;
import org.openhab.automation.java223.helper.annotations.EphemerisDaysetCondition;
import org.openhab.automation.java223.helper.annotations.EphemerisHolidayCondition;
import org.openhab.automation.java223.helper.annotations.EphemerisNotHolidayCondition;
import org.openhab.automation.java223.helper.annotations.EphemerisWeekdayCondition;
import org.openhab.automation.java223.helper.annotations.EphemerisWeekendCondition;
import org.openhab.automation.java223.helper.annotations.GenericAutomationTrigger;
import org.openhab.automation.java223.helper.annotations.GenericCompareCondition;
import org.openhab.automation.java223.helper.annotations.GenericEventCondition;
import org.openhab.automation.java223.helper.annotations.GenericEventTrigger;
import org.openhab.automation.java223.helper.annotations.GroupStateChangeTrigger;
import org.openhab.automation.java223.helper.annotations.GroupStateUpdateTrigger;
import org.openhab.automation.java223.helper.annotations.ItemCommandTrigger;
import org.openhab.automation.java223.helper.annotations.ItemStateChangeTrigger;
import org.openhab.automation.java223.helper.annotations.ItemStateCondition;
import org.openhab.automation.java223.helper.annotations.ItemStateUpdateTrigger;
import org.openhab.automation.java223.helper.annotations.Rule;
import org.openhab.automation.java223.helper.annotations.ScriptLoadedTrigger;
import org.openhab.automation.java223.helper.annotations.SystemStartlevelTrigger;
import org.openhab.automation.java223.helper.annotations.ThingStatusChangeTrigger;
import org.openhab.automation.java223.helper.annotations.ThingStatusUpdateTrigger;
import org.openhab.automation.java223.helper.annotations.TimeOfDayCondition;
import org.openhab.automation.java223.helper.annotations.TimeOfDayTrigger;
import org.openhab.core.automation.Action;
import org.openhab.core.automation.Condition;
import org.openhab.core.automation.Module;
import org.openhab.core.automation.Trigger;
import org.openhab.core.automation.module.script.rulesupport.shared.ScriptedAutomationManager;
import org.openhab.core.automation.module.script.rulesupport.shared.simple.SimpleRule;
import org.openhab.core.automation.module.script.rulesupport.shared.simple.SimpleRuleActionHandler;
import org.openhab.core.automation.util.ModuleBuilder;
import org.openhab.core.automation.util.TriggerBuilder;
import org.openhab.core.config.core.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Gwendal Roulleau - Initial contribution, based on work from Jürgen Weber and Jan N. Klug
 */
@NonNullByDefault
public class RuleAnnotationParser {

    private static Logger logger = LoggerFactory.getLogger(RuleAnnotationParser.class);

    public static final Map<Class<? extends Annotation>, String> TRIGGER_FROM_ANNOTATION = Map.ofEntries(
            Map.entry(ItemCommandTrigger.class, "core.ItemCommandTrigger"),
            Map.entry(ItemStateChangeTrigger.class, "core.ItemStateChangeTrigger"),
            Map.entry(ItemStateUpdateTrigger.class, "core.ItemStateUpdateTrigger"),
            Map.entry(GroupStateChangeTrigger.class, "core.GroupStateChangeTrigger"),
            Map.entry(GroupStateUpdateTrigger.class, "core.GroupStateUpdateTrigger"),
            Map.entry(ChannelEventTrigger.class, "core.ChannelEventTrigger"),
            Map.entry(CronTrigger.class, "timer.GenericCronTrigger"),
            Map.entry(DateTimeTrigger.class, "timer.DateTimeTrigger"),
            Map.entry(TimeOfDayTrigger.class, "timer.TimeOfDayTrigger"),
            Map.entry(GenericEventTrigger.class, "core.GenericEventTrigger"),
            Map.entry(ThingStatusUpdateTrigger.class, "core.ThingStatusUpdateTrigger"),
            Map.entry(ThingStatusChangeTrigger.class, "core.ThingStatusChangeTrigger"),
            Map.entry(SystemStartlevelTrigger.class, "core.SystemStartlevelTrigger"));

    public static final Map<Class<? extends Annotation>, String> CONDITION_FROM_ANNOTATION = Map.ofEntries(
            Map.entry(ItemStateCondition.class, "core.ItemStateCondition"),
            Map.entry(GenericCompareCondition.class, "core.GenericCompareCondition"),
            Map.entry(DayOfWeekCondition.class, "timer.DayOfWeekCondition"),
            Map.entry(EphemerisWeekdayCondition.class, "ephemeris.WeekdayCondition"),
            Map.entry(EphemerisWeekendCondition.class, "ephemeris.WeekendCondition"),
            Map.entry(EphemerisHolidayCondition.class, "ephemeris.HolidayCondition"),
            Map.entry(EphemerisNotHolidayCondition.class, "ephemeris.NotHolidayCondition"),
            Map.entry(EphemerisDaysetCondition.class, "ephemeris.DaysetCondition"),
            Map.entry(GenericEventCondition.class, "core.GenericEventCondition"),
            Map.entry(TimeOfDayCondition.class, "core.TimeOfDayCondition"));

    public static void parse(Object script, ScriptedAutomationManager automationManager,
            @Nullable Consumer<SimpleRuleActionHandler> scriptLoadedActions)
            throws IllegalArgumentException, IllegalAccessException, RuleParserException {
        Class<?> c = script.getClass();

        logger.debug("Parsing: {}", c.getName());

        List<AccessibleObject> members = new ArrayList<>();
        Collections.addAll(members, c.getDeclaredFields());
        Collections.addAll(members, c.getDeclaredMethods());

        for (AccessibleObject member : members) {

            Rule ra = member.getAnnotation(Rule.class);
            if (ra == null) {
                continue;
            }
            if (ra.disabled()) {
                logger.debug("Ignoring disabled rule '{}'");
                continue;
            }

            // extract an action from the annotated member
            SimpleRuleActionHandler action;
            String memberName;
            if (member instanceof Field fieldMember) {
                Class<?> ftype = fieldMember.getType();
                memberName = fieldMember.getName();
                if (ftype.isAssignableFrom(SimpleRuleActionHandler.class)) {
                    action = (SimpleRuleActionHandler) fieldMember.get(script);
                } else {
                    try {
                        Constructor<SimpleRuleActionHandlerJava223> constructor = SimpleRuleActionHandlerJava223.class
                                .getDeclaredConstructor(ftype);
                        action = constructor.newInstance(fieldMember.get(script));
                    } catch (NoSuchMethodException | InstantiationException | InvocationTargetException nse) {
                        logger.info("Cannot apply rule annotation on a member of type {}", ftype.getSimpleName(), nse);
                        continue;
                    }
                }
            } else if (member instanceof Method methodMember) {
                action = new SimpleRuleActionHandlerJava223(script, methodMember);
                memberName = methodMember.getName();
            } else {
                continue;
            }
            SimpleRule simpleRule = new SimpleRule() {
                @Override
                public Object execute(Action module, Map<String, ?> inputs) {
                    return action.execute(module, inputs);
                }
            };

            // name and description
            String ruleName = chooseFirstOk(ra.name(), memberName);
            String ruleDescription = chooseFirstOk(ra.description(),
                    script.getClass().getSimpleName() + "/" + ruleName);
            simpleRule.setName(ruleName);
            simpleRule.setDescription(ruleDescription);

            // tags
            simpleRule.setTags(Set.of(ra.tags()));

            // triggers
            List<Trigger> triggers = new ArrayList<>();
            TRIGGER_FROM_ANNOTATION
                    .entrySet().stream().map(annotation -> getModuleForAnnotation(member, annotation.getKey(),
                            annotation.getValue(), ModuleBuilder::createTrigger, ruleName))
                    .flatMap(Collection::stream).forEach(triggers::add);
            Arrays.stream(member.getDeclaredAnnotationsByType(GenericAutomationTrigger.class))
                    .map(annotation -> getGenericAutomationTrigger(annotation, ruleName)).forEach(triggers::add);
            simpleRule.setTriggers(triggers);

            // condition
            List<Condition> conditions = CONDITION_FROM_ANNOTATION.entrySet().stream()
                    .map(annotationClazz -> getModuleForAnnotation(member, annotationClazz.getKey(),
                            annotationClazz.getValue(), ModuleBuilder::createCondition, ruleName))
                    .flatMap(Collection::stream).collect(Collectors.toList());
            simpleRule.setConditions(conditions);

            // log everything
            if (logger.isDebugEnabled()) {
                logger.debug("field {}", memberName);
                logger.debug("@Rule(name = {}", ruleName);
                for (Trigger trigger : simpleRule.getTriggers()) {
                    logger.debug("Trigger(id = {}, uid = {})", trigger.getId(), trigger.getTypeUID());
                    logger.debug("Configuration: {}", trigger.getConfiguration().toString());
                }
            }

            // create rule
            automationManager.addRule(simpleRule);

            // check if ScriptLoadedTrigger is present
            if (member.getDeclaredAnnotation(ScriptLoadedTrigger.class) != null && action != null
                    && scriptLoadedActions != null) {
                scriptLoadedActions.accept(action);
            }
        }
    }

    private static String chooseFirstOk(String... choices) {
        for (String choice : choices) {
            if (choice == null || choice.isBlank() || choice.equals(ANNOTATION_DEFAULT)) {
                continue;
            } else {
                return choice;
            }
        }
        return "";
    }

    private static Trigger getGenericAutomationTrigger(GenericAutomationTrigger annotation, String ruleName) {
        String typeUid = annotation.typeUid();
        Configuration configuration = new Configuration();
        for (String param : annotation.params()) {
            String[] parts = param.split("=");
            if (parts.length != 2) {
                logger.warn("Ignoring '{}' in trigger for '{}', can not determine key and value", param, ruleName);
                continue;
            }
            configuration.put(parts[0], parts[1]);
        }
        return TriggerBuilder.create().withTypeUID(typeUid).withId(sanitizeTriggerId(ruleName, annotation.hashCode()))
                .withConfiguration(configuration).build();
    }

    private static <T extends Annotation, R extends Module> List<R> getModuleForAnnotation(
            AccessibleObject accessibleObject, Class<T> clazz, String typeUid, Supplier<ModuleBuilder<?, R>> builder,
            String ruleName) {
        T[] annotations = accessibleObject.getDeclaredAnnotationsByType(clazz);
        return Arrays.stream(annotations)
                .map(annotation -> builder.get().withId(sanitizeTriggerId(ruleName, annotation.hashCode()))
                        .withTypeUID(typeUid).withConfiguration(getAnnotationConfiguration(annotation)).build())
                .collect(Collectors.toList());
    }

    private static Configuration getAnnotationConfiguration(Annotation annotation) {
        Map<String, Object> configuration = new HashMap<>();
        for (Method method : annotation.annotationType().getDeclaredMethods()) {
            try {
                if (method.getParameterCount() == 0) {
                    Object parameterValue = method.invoke(annotation);
                    if (parameterValue == null || ANNOTATION_DEFAULT.equals(parameterValue)) {
                        continue;
                    }
                    if (parameterValue instanceof String[]) {
                        configuration.put(method.getName(), Arrays.asList((String[]) parameterValue));
                    } else if (parameterValue instanceof Integer) {
                        configuration.put(method.getName(), BigDecimal.valueOf((Integer) parameterValue));
                    } else {
                        configuration.put(method.getName(), parameterValue);
                    }
                }
            } catch (IllegalAccessException | InvocationTargetException e) {
                // ignore private fields
            }
        }
        return new Configuration(configuration);
    }

    private static String sanitizeTriggerId(String ruleName, int index) {
        return (ruleName + "_" + index).replaceAll("\\.", "_");
    }
}
