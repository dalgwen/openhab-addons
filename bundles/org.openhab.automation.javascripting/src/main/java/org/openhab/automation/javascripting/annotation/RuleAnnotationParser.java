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

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
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
import org.openhab.automation.javascripting.annotations.ThingStateChangeTrigger;
import org.openhab.automation.javascripting.annotations.ThingStateChangeTriggers;
import org.openhab.automation.javascripting.annotations.ThingStateUpdateTrigger;
import org.openhab.automation.javascripting.annotations.ThingStateUpdateTriggers;
import org.openhab.automation.javascripting.scriptsupport.Script;
import org.openhab.core.automation.Trigger;
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

    private Script script;

    public RuleAnnotationParser(Script script) {
        this.script = script;
    }

    public void parse() throws IllegalArgumentException, IllegalAccessException {
        Class<? extends Object> c = script.getClass();

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
                try {
                    simpleRule = new SimpleMethodRule(script, methodMember);
                } catch (RuleParserException e) {
                    logger.error("Cannot parse rule", e);
                    continue;
                }
                memberName = methodMember.getName();
            } else {
                continue;
            }

            Annotation ra = m.getAnnotation(Rule.class);

            Rule r = (Rule) ra;
            String ruleName = r.name();

            List<Trigger> triggerList = new ArrayList<Trigger>();

            Annotation[] as = m.getDeclaredAnnotations();
            for (Annotation a : as) {

                if (a instanceof CronTrigger cronTrigger) {
                    createCronTrigger(triggerList, cronTrigger);
                }

                if (a instanceof CronTriggers cronTriggers) {
                    for (CronTrigger trigger : cronTriggers.value()) {
                        createCronTrigger(triggerList, trigger);
                    }
                }

                if (a instanceof SystemTrigger trigger) {
                    createSystemTrigger(triggerList, trigger);
                }

                if (a instanceof SystemTriggers triggers) {
                    for (SystemTrigger trigger : triggers.value()) {
                        createSystemTrigger(triggerList, trigger);
                    }
                }

                if (a instanceof ItemStateChangeTrigger trigger) {
                    createItemStateChangeTrigger(triggerList, trigger);
                }

                if (a instanceof ItemStateChangeTriggers triggers) {
                    for (ItemStateChangeTrigger trigger : triggers.value()) {
                        createItemStateChangeTrigger(triggerList, trigger);
                    }
                }

                if (a instanceof ItemStateUpdateTrigger trigger) {
                    createItemStateUpdateTrigger(triggerList, trigger);
                }

                if (a instanceof ItemStateUpdateTriggers triggers) {
                    for (ItemStateUpdateTrigger trigger : triggers.value()) {
                        createItemStateUpdateTrigger(triggerList, trigger);
                    }
                }

                if (a instanceof ThingStateUpdateTrigger trigger) {
                    createThingStateUpdateTrigger(triggerList, trigger);
                }

                if (a instanceof ThingStateUpdateTriggers triggers) {
                    for (ThingStateUpdateTrigger trigger : triggers.value()) {
                        createThingStateUpdateTrigger(triggerList, trigger);
                    }
                }

                if (a instanceof ThingStateChangeTrigger trigger) {
                    createThingStateChangeTrigger(triggerList, trigger);
                }

                if (a instanceof ThingStateChangeTriggers triggers) {
                    for (ThingStateChangeTrigger trigger : triggers.value()) {
                        createThingStateChangeTrigger(triggerList, trigger);
                    }
                }

                if (a instanceof ItemCommandTrigger trigger) {
                    createItemCommandTrigger(triggerList, trigger);
                }

                if (a instanceof ItemCommandTriggers triggers) {
                    for (ItemCommandTrigger trigger : triggers.value()) {
                        createItemCommandTrigger(triggerList, trigger);
                    }
                }

                if (a instanceof ChannelEventTrigger trigger) {
                    createChannelEventTrigger(triggerList, trigger);
                }

                if (a instanceof ChannelEventTriggers triggers) {
                    for (ChannelEventTrigger trigger : triggers.value()) {
                        createChannelEventTrigger(triggerList, trigger);
                    }
                }

            }

            if (logger.isDebugEnabled()) {
                logger.debug("field {}", memberName);
                logger.debug("@Rule(name = {}", ruleName);
                for (Trigger trigger : triggerList) {
                    logger.debug("Trigger(id = {}, uid = {})", trigger.getId(), trigger.getTypeUID());
                    logger.debug("Configuration: {}", trigger.getConfiguration().toString());
                }
            }

            script.activateRule(ruleName, simpleRule, triggerList);
        }
    }

    private void createCronTrigger(List<Trigger> triggers, CronTrigger ct) {
        String id = ct.id();
        String cronExpression = ct.cronExpression();

        logger.debug("CronTrigger: {}", cronExpression);

        Trigger trigger = script.createGenericCronTrigger(id, cronExpression);
        triggers.add(trigger);
    }

    private void createSystemTrigger(List<Trigger> triggers, SystemTrigger triggerAnnot) {
        String id = triggerAnnot.id();
        String startlevel = triggerAnnot.startlevel();

        logger.debug("SystemTrigger, startlevel: {}", startlevel);

        Trigger trigger = script.createSystemStartlevelTrigger(id, startlevel);
        triggers.add(trigger);
    }

    private void createItemStateChangeTrigger(List<Trigger> triggers, ItemStateChangeTrigger triggerAnnot) {
        String id = triggerAnnot.id();
        String item = triggerAnnot.item();

        logger.debug("ItemStateChangeTrigger: {}", item);

        Trigger trigger;

        if (triggerAnnot.newState().isEmpty() && triggerAnnot.previousState().isEmpty()) {
            trigger = script.createItemStateChangeTrigger(id, item);
        } else if (triggerAnnot.previousState().isEmpty()) {
            trigger = script.createItemStateChangeTrigger(id, item, triggerAnnot.newState());
        } else {
            trigger = script.createItemStateChangeTrigger(id, item, triggerAnnot.newState(),
                    triggerAnnot.previousState());
        }
        triggers.add(trigger);
    }

    private void createItemStateUpdateTrigger(List<Trigger> triggerList, ItemStateUpdateTrigger triggerAnnot) {
        String id = triggerAnnot.id();
        String item = triggerAnnot.item();

        logger.debug("ItemStateUpdateTrigger: {}", item);

        Trigger trigger;

        if (triggerAnnot.newState().isEmpty() && triggerAnnot.previousState().isEmpty()) {
            trigger = script.createItemStateChangeTrigger(id, item);
        } else if (triggerAnnot.previousState().isEmpty()) {
            trigger = script.createItemStateUpdateTrigger(id, item, triggerAnnot.newState());
        } else {
            trigger = script.createItemStateUpdateTrigger(id, item, triggerAnnot.newState(),
                    triggerAnnot.previousState());
        }
        triggerList.add(trigger);
    }

    private void createThingStateChangeTrigger(List<Trigger> triggers, ThingStateChangeTrigger triggerAnnot) {
        String id = triggerAnnot.id();
        String thingUID = triggerAnnot.thingUID();

        logger.debug("ThingStateChangeTrigger: {}", thingUID);

        Trigger trigger;

        if (triggerAnnot.newState().isEmpty() && triggerAnnot.previousState().isEmpty()) {
            trigger = script.createThingChangeTrigger(id, thingUID);
        } else if (triggerAnnot.previousState().isEmpty()) {
            trigger = script.createThingChangeTrigger(id, thingUID, triggerAnnot.newState());
        } else {
            trigger = script.createThingChangeTrigger(id, thingUID, triggerAnnot.newState(),
                    triggerAnnot.previousState());
        }
        triggers.add(trigger);
    }

    private void createThingStateUpdateTrigger(List<Trigger> triggers, ThingStateUpdateTrigger triggerAnnot) {
        String id = triggerAnnot.id();
        String thingUID = triggerAnnot.thingUID();

        logger.debug("ThingStateUpdateTrigger: {}", thingUID);

        Trigger trigger;

        if (triggerAnnot.newState().isEmpty() && triggerAnnot.previousState().isEmpty()) {
            trigger = script.createThingUpdateTrigger(id, thingUID);
        } else if (triggerAnnot.previousState().isEmpty()) {
            trigger = script.createThingUpdateTrigger(id, thingUID, triggerAnnot.newState());
        } else {
            trigger = script.createThingUpdateTrigger(id, thingUID, triggerAnnot.newState(),
                    triggerAnnot.previousState());
        }
        triggers.add(trigger);
    }

    private void createItemCommandTrigger(List<Trigger> triggers, ItemCommandTrigger triggerAnnot) {
        String id = triggerAnnot.id();
        String item = triggerAnnot.item();
        String command = triggerAnnot.command();

        logger.debug("ItemCommandTrigger: {}", command);

        Trigger trigger = script.createItemCommandTrigger(id, item, command);
        triggers.add(trigger);
    }

    private void createChannelEventTrigger(List<Trigger> triggers, ChannelEventTrigger triggerAnnot) {
        String id = triggerAnnot.id();
        String channelUID = triggerAnnot.channelUID();
        String event = triggerAnnot.event();

        logger.debug("ChannelEventTrigger: {}", channelUID);

        Trigger trigger;

        if (event.isEmpty()) {
            trigger = script.createChannelEventTrigger(id, channelUID);
        } else {
            trigger = script.createChannelEventTrigger(id, channelUID, event);
        }
        triggers.add(trigger);
    }
}
