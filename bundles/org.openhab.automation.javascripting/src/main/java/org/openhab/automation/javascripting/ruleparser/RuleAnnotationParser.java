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

package org.openhab.automation.javascripting.ruleparser;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.automation.javascripting.annotations.ChannelEventTrigger;
import org.openhab.automation.javascripting.annotations.ChannelEventTriggers;
import org.openhab.automation.javascripting.annotations.CronTrigger;
import org.openhab.automation.javascripting.annotations.CronTriggers;
import org.openhab.automation.javascripting.annotations.ItemCommandTrigger;
import org.openhab.automation.javascripting.annotations.ItemCommandTriggers;
import org.openhab.automation.javascripting.annotations.ItemStateChangeTrigger;
import org.openhab.automation.javascripting.annotations.ItemStateChangeTriggers;
import org.openhab.automation.javascripting.annotations.ItemStateUpdateTrigger;
import org.openhab.automation.javascripting.annotations.ItemStateUpdateTriggers;
import org.openhab.automation.javascripting.annotations.Rule;
import org.openhab.automation.javascripting.annotations.SystemTrigger;
import org.openhab.automation.javascripting.annotations.SystemTriggers;
import org.openhab.automation.javascripting.annotations.ThingStatusChangeTrigger;
import org.openhab.automation.javascripting.annotations.ThingStatusChangeTriggers;
import org.openhab.automation.javascripting.annotations.ThingStatusUpdateTrigger;
import org.openhab.automation.javascripting.annotations.ThingStatusUpdateTriggers;
import org.openhab.automation.javascripting.scriptsupport.RuleBuilder;
import org.openhab.automation.javascripting.scriptsupport.Script;
import org.openhab.core.automation.Trigger;
import org.openhab.core.automation.module.script.rulesupport.shared.ScriptedAutomationManager;
import org.openhab.core.automation.module.script.rulesupport.shared.simple.SimpleRule;
import org.openhab.core.automation.module.script.rulesupport.shared.simple.SimpleRuleActionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jürgen Weber - Initial contribution
 */
@NonNullByDefault
public class RuleAnnotationParser {

    private static Logger logger = LoggerFactory.getLogger(RuleAnnotationParser.class);

    public static void parse(Script script, ScriptedAutomationManager automationManager)
            throws IllegalArgumentException, IllegalAccessException, RuleParserException {
        Class<? extends Script> c = script.getClass();

        logger.debug("parse: {}", c.getName());

        AccessibleObject[] fields = c.getDeclaredFields();
        AccessibleObject[] methods = c.getDeclaredMethods();

        List<AccessibleObject> members = new ArrayList<>();
        Collections.addAll(members, fields);
        Collections.addAll(members, methods);

        for (AccessibleObject m : members) {

            if (!m.isAnnotationPresent(Rule.class)) {
                continue;
            }

            SimpleRule simpleRule;
            String memberName;

            if (m instanceof Field fieldMember) {
                Class<?> ftype = fieldMember.getType();
                if (!org.openhab.core.automation.Rule.class.isAssignableFrom(ftype)
                        || !SimpleRuleActionHandler.class.isAssignableFrom(ftype)) {
                    continue;
                }
                simpleRule = (SimpleRule) (fieldMember.get(script));
                memberName = fieldMember.getName();
            } else if (m instanceof Method methodMember) {
                simpleRule = new SimpleMethodRule(script, methodMember);
                memberName = methodMember.getName();
            } else {
                continue;
            }

            Rule ra = m.getAnnotation(Rule.class);
            String ruleName = ra.name() != null && !ra.name().isEmpty() ? ra.name() : memberName;

            if (simpleRule == null) {
                throw new RuleParserException("Cannot build a rule with no code to execute");
            }

            RuleBuilder ruleBuilder = new RuleBuilder(automationManager).withSimpleRule(simpleRule).withName(ruleName);

            Annotation[] as = m.getDeclaredAnnotations();
            int currentIndex = 0;
            for (Annotation a : as) {

                if (a instanceof CronTrigger cronTrigger) {
                    String id = sanitizeTriggerId(cronTrigger.id(), ruleName, currentIndex);
                    ruleBuilder.withTrigger(createCronTrigger(cronTrigger, id));
                    currentIndex++;
                }

                if (a instanceof CronTriggers cronTriggers) {
                    for (CronTrigger trigger : cronTriggers.value()) {
                        String id = sanitizeTriggerId(trigger.id(), ruleName, currentIndex);
                        ruleBuilder.withTrigger(createCronTrigger(trigger, id));
                        currentIndex++;
                    }
                }

                if (a instanceof SystemTrigger trigger) {
                    String id = sanitizeTriggerId(trigger.id(), ruleName, currentIndex);
                    ruleBuilder.withTrigger(createSystemTrigger(trigger, id));
                    currentIndex++;
                }

                if (a instanceof SystemTriggers triggers) {
                    for (SystemTrigger trigger : triggers.value()) {
                        String id = sanitizeTriggerId(trigger.id(), ruleName, currentIndex);
                        ruleBuilder.withTrigger(createSystemTrigger(trigger, id));
                        currentIndex++;
                    }
                }

                if (a instanceof ItemStateChangeTrigger trigger) {
                    String id = sanitizeTriggerId(trigger.id(), ruleName, currentIndex);
                    ruleBuilder.withTrigger(createItemStateChangeTrigger(trigger, id));
                    currentIndex++;
                }

                if (a instanceof ItemStateChangeTriggers triggers) {
                    for (ItemStateChangeTrigger trigger : triggers.value()) {
                        String id = sanitizeTriggerId(trigger.id(), ruleName, currentIndex);
                        currentIndex++;
                        ruleBuilder.withTrigger(createItemStateChangeTrigger(trigger, id));
                        currentIndex++;
                    }
                }

                if (a instanceof ItemStateUpdateTrigger trigger) {
                    String id = sanitizeTriggerId(trigger.id(), ruleName, currentIndex);
                    ruleBuilder.withTrigger(createItemStateUpdateTrigger(trigger, id));
                    currentIndex++;
                }

                if (a instanceof ItemStateUpdateTriggers triggers) {
                    for (ItemStateUpdateTrigger trigger : triggers.value()) {
                        String id = sanitizeTriggerId(trigger.id(), ruleName, currentIndex);
                        ruleBuilder.withTrigger(createItemStateUpdateTrigger(trigger, id));
                        currentIndex++;
                    }
                }

                if (a instanceof ThingStatusUpdateTrigger trigger) {
                    String id = sanitizeTriggerId(trigger.id(), ruleName, currentIndex);
                    ruleBuilder.withTrigger(createThingStatusUpdateTrigger(trigger, id));
                    currentIndex++;
                }

                if (a instanceof ThingStatusUpdateTriggers triggers) {
                    for (ThingStatusUpdateTrigger trigger : triggers.value()) {
                        String id = sanitizeTriggerId(trigger.id(), ruleName, currentIndex);
                        ruleBuilder.withTrigger(createThingStatusUpdateTrigger(trigger, id));
                        currentIndex++;
                    }
                }

                if (a instanceof ThingStatusChangeTrigger trigger) {
                    String id = sanitizeTriggerId(trigger.id(), ruleName, currentIndex);
                    ruleBuilder.withTrigger(createThingStatusChangeTrigger(trigger, id));
                    currentIndex++;
                }

                if (a instanceof ThingStatusChangeTriggers triggers) {
                    for (ThingStatusChangeTrigger trigger : triggers.value()) {
                        String id = sanitizeTriggerId(trigger.id(), ruleName, currentIndex);
                        ruleBuilder.withTrigger(createThingStatusChangeTrigger(trigger, id));
                        currentIndex++;
                    }
                }

                if (a instanceof ItemCommandTrigger trigger) {
                    String id = sanitizeTriggerId(trigger.id(), ruleName, currentIndex);
                    ruleBuilder.withTrigger(createItemCommandTrigger(trigger, id));
                    currentIndex++;
                }

                if (a instanceof ItemCommandTriggers triggers) {
                    for (ItemCommandTrigger trigger : triggers.value()) {
                        String id = sanitizeTriggerId(trigger.id(), ruleName, currentIndex);
                        ruleBuilder.withTrigger(createItemCommandTrigger(trigger, id));
                        currentIndex++;
                    }
                }

                if (a instanceof ChannelEventTrigger trigger) {
                    String id = sanitizeTriggerId(trigger.id(), ruleName, currentIndex);
                    ruleBuilder.withTrigger(createChannelEventTrigger(trigger, id));
                    currentIndex++;
                }

                if (a instanceof ChannelEventTriggers triggers) {
                    for (ChannelEventTrigger trigger : triggers.value()) {
                        String id = sanitizeTriggerId(trigger.id(), ruleName, currentIndex);
                        ruleBuilder.withTrigger(createChannelEventTrigger(trigger, id));
                        currentIndex++;
                    }
                }

            }

            if (logger.isDebugEnabled()) {
                logger.debug("field {}", memberName);
                logger.debug("@Rule(name = {}", ruleName);
                for (Trigger trigger : ruleBuilder.getTriggers()) {
                    logger.debug("Trigger(id = {}, uid = {})", trigger.getId(), trigger.getTypeUID());
                    logger.debug("Configuration: {}", trigger.getConfiguration().toString());
                }
            }

            ruleBuilder.activate();
        }
    }

    private static Trigger createCronTrigger(CronTrigger ct, String id) {
        String cronExpression = ct.cronExpression();

        logger.debug("CronTrigger: {}", cronExpression);

        return RuleBuilder.createGenericCronTrigger(id, cronExpression);
    }

    private static Trigger createSystemTrigger(SystemTrigger triggerAnnot, String id) {
        String startlevel = triggerAnnot.startlevel();

        logger.debug("SystemTrigger, startlevel: {}", startlevel);

        return RuleBuilder.createSystemStartlevelTrigger(id, startlevel);
    }

    private static Trigger createItemStateChangeTrigger(ItemStateChangeTrigger triggerAnnot, String id) {
        String item = triggerAnnot.item();

        logger.debug("ItemStateChangeTrigger: {}", item);

        Trigger trigger;

        if (triggerAnnot.newState().isEmpty() && triggerAnnot.previousState().isEmpty()) {
            trigger = RuleBuilder.createItemStateChangeTrigger(id, item);
        } else if (triggerAnnot.previousState().isEmpty()) {
            trigger = RuleBuilder.createItemStateChangeTrigger(id, item, triggerAnnot.newState());
        } else {
            trigger = RuleBuilder.createItemStateChangeTrigger(id, item, triggerAnnot.newState(),
                    triggerAnnot.previousState());
        }
        return trigger;
    }

    private static Trigger createItemStateUpdateTrigger(ItemStateUpdateTrigger triggerAnnot, String id) {
        String item = triggerAnnot.item();

        logger.debug("ItemStateUpdateTrigger: {}", item);

        Trigger trigger;

        if (triggerAnnot.newState().isEmpty() && triggerAnnot.previousState().isEmpty()) {
            trigger = RuleBuilder.createItemStateUpdateTrigger(id, item);
        } else if (triggerAnnot.previousState().isEmpty()) {
            trigger = RuleBuilder.createItemStateUpdateTrigger(id, item, triggerAnnot.newState());
        } else {
            trigger = RuleBuilder.createItemStateUpdateTrigger(id, item, triggerAnnot.newState(),
                    triggerAnnot.previousState());
        }
        return trigger;
    }

    private static Trigger createThingStatusChangeTrigger(ThingStatusChangeTrigger triggerAnnot, String id) {
        String thingUID = triggerAnnot.thingUID();

        logger.debug("ThingStatuseChangeTrigger: {}", thingUID);

        Trigger trigger;

        if (triggerAnnot.newState().isEmpty() && triggerAnnot.previousState().isEmpty()) {
            trigger = RuleBuilder.createThingChangeTrigger(id, thingUID);
        } else if (triggerAnnot.previousState().isEmpty()) {
            trigger = RuleBuilder.createThingChangeTrigger(id, thingUID, triggerAnnot.newState());
        } else {
            trigger = RuleBuilder.createThingChangeTrigger(id, thingUID, triggerAnnot.newState(),
                    triggerAnnot.previousState());
        }
        return trigger;
    }

    private static Trigger createThingStatusUpdateTrigger(ThingStatusUpdateTrigger triggerAnnot, String id) {
        String thingUID = triggerAnnot.thingUID();

        logger.debug("ThingStatusUpdateTrigger: {}", thingUID);

        Trigger trigger;

        if (triggerAnnot.newState().isEmpty() && triggerAnnot.previousState().isEmpty()) {
            trigger = RuleBuilder.createThingUpdateTrigger(id, thingUID);
        } else if (triggerAnnot.previousState().isEmpty()) {
            trigger = RuleBuilder.createThingUpdateTrigger(id, thingUID, triggerAnnot.newState());
        } else {
            trigger = RuleBuilder.createThingUpdateTrigger(id, thingUID, triggerAnnot.newState(),
                    triggerAnnot.previousState());
        }
        return trigger;
    }

    private static Trigger createItemCommandTrigger(ItemCommandTrigger triggerAnnot, String id) {
        String item = triggerAnnot.item();
        String command = triggerAnnot.command();

        logger.debug("ItemCommandTrigger: {}", command);

        Trigger trigger;
        if (command.isEmpty()) {
            trigger = RuleBuilder.createItemCommandTrigger(id, item);
        } else {
            trigger = RuleBuilder.createItemCommandTrigger(id, item, command);
        }
        return trigger;
    }

    private static Trigger createChannelEventTrigger(ChannelEventTrigger triggerAnnot, String id) {
        String channelUID = triggerAnnot.channelUID();
        String event = triggerAnnot.event();

        logger.debug("ChannelEventTrigger: {}", channelUID);

        Trigger trigger;

        if (event.isEmpty()) {
            trigger = RuleBuilder.createChannelEventTrigger(id, channelUID);
        } else {
            trigger = RuleBuilder.createChannelEventTrigger(id, channelUID, event);
        }
        return trigger;
    }

    private static String sanitizeTriggerId(@Nullable String id, String ruleName, int index) {
        String returnId = id;
        if (returnId == null || returnId.isEmpty()) {
            returnId = ruleName + "_" + index;
        }
        return returnId.replaceAll("\\.", "_");
    }
}
