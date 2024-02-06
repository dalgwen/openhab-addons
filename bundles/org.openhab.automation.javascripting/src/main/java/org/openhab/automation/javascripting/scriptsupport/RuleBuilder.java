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
package org.openhab.automation.javascripting.scriptsupport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.automation.Trigger;
import org.openhab.core.automation.module.script.rulesupport.shared.ScriptedAutomationManager;
import org.openhab.core.automation.module.script.rulesupport.shared.simple.SimpleRule;
import org.openhab.core.automation.util.TriggerBuilder;
import org.openhab.core.config.core.Configuration;

/**
 * Trigger builder class
 *
 * @author Gwendal Roulleau - Initial contribution, extracted from pravussum's groovy rules
 */
@NonNullByDefault
public class RuleBuilder {

    private ScriptedAutomationManager automationManager;

    @Nullable
    private SimpleRule sr = null;

    private List<Trigger> triggers = new ArrayList<Trigger>();

    @Nullable
    private String name;

    public RuleBuilder(ScriptedAutomationManager automationManager) {
        this.automationManager = automationManager;
    }

    public RuleBuilder withSimpleRule(SimpleRule sr) {
        this.sr = sr;
        return this;
    }

    public RuleBuilder withName(String name) {
        this.name = name;
        return this;
    }

    public List<Trigger> getTriggers() {
        return triggers;
    }

    public RuleBuilder withTrigger(Trigger trigger) {
        triggers.add(trigger);
        return this;
    }

    public void activate() {
        SimpleRule localSr = sr;
        if (localSr == null || name == null) {
            throw new JavaScriptingException("A rule must have a name and something to exec with a SimpleRule");
        }
        localSr.setTriggers(triggers);
        localSr.setName(name);
        automationManager.addRule(localSr);
    }

    // Utility methods
    // very inspired by pravussum's groovy rules
    // https://community.openhab.org/t/examples-for-groovy-scripts/131121/9
    public static Trigger createSystemStartlevelTrigger(String triggerId, String startlevel) {

        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("startlevel", startlevel);

        Trigger trigger = TriggerBuilder.create().withLabel(triggerId).withId(triggerId)
                .withTypeUID("core.SystemStartlevelTrigger").withConfiguration(new Configuration(configuration))
                .build();

        return trigger;
    }

    public static Trigger createGenericCronTrigger(String triggerId, String cronExpression) {

        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("cronExpression", cronExpression);

        Trigger trigger = TriggerBuilder.create().withLabel(triggerId).withId(triggerId)
                .withTypeUID("timer.GenericCronTrigger").withConfiguration(new Configuration(configuration)).build();

        return trigger;
    }

    public static Trigger createItemStateChangeTrigger(String triggerId, String itemName) {

        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("itemName", itemName);

        Trigger trigger = TriggerBuilder.create().withLabel(triggerId).withId(triggerId)
                .withTypeUID("core.ItemStateChangeTrigger").withConfiguration(new Configuration(configuration)).build();

        return trigger;
    }

    public static Trigger createItemStateChangeTrigger(String triggerId, String itemName, String newState) {

        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("itemName", itemName);
        configuration.put("state", newState);

        Trigger trigger = TriggerBuilder.create().withLabel(triggerId).withId(triggerId)
                .withTypeUID("core.ItemStateChangeTrigger").withConfiguration(new Configuration(configuration)).build();

        return trigger;
    }

    public static Trigger createItemStateChangeTrigger(String triggerId, String itemName, String previousState,
            String newState) {

        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("itemName", itemName);
        configuration.put("previousState", previousState);
        configuration.put("state", newState);

        Trigger trigger = TriggerBuilder.create().withLabel(triggerId).withId(triggerId)
                .withTypeUID("core.ItemStateChangeTrigger").withConfiguration(new Configuration(configuration)).build();

        return trigger;
    }

    public static Trigger createItemStateUpdateTrigger(String triggerId, String itemName) {

        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("itemName", itemName);

        Trigger trigger = TriggerBuilder.create().withLabel(triggerId).withId(triggerId)
                .withTypeUID("core.ItemStateUpdateTrigger").withConfiguration(new Configuration(configuration)).build();

        return trigger;
    }

    public static Trigger createItemStateUpdateTrigger(String triggerId, String itemName, String state) {

        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("itemName", itemName);
        configuration.put("command", state);

        Trigger trigger = TriggerBuilder.create().withLabel(triggerId).withId(triggerId)
                .withTypeUID("core.ItemStateUpdateTrigger").withConfiguration(new Configuration(configuration)).build();

        return trigger;
    }

    public static Trigger createItemStateUpdateTrigger(String triggerId, String itemName, String previousState,
            String newState) {

        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("itemName", itemName);
        configuration.put("previousState", previousState);
        configuration.put("state", newState);

        Trigger trigger = TriggerBuilder.create().withLabel(triggerId).withId(triggerId)
                .withTypeUID("core.ItemStateUpdateTrigger").withConfiguration(new Configuration(configuration)).build();

        return trigger;
    }

    public static Trigger createItemCommandTrigger(String triggerId, String itemName) {

        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("itemName", itemName);

        Trigger trigger = TriggerBuilder.create().withLabel(triggerId).withId(triggerId)
                .withTypeUID("core.ItemCommandTrigger").withConfiguration(new Configuration(configuration)).build();

        return trigger;
    }

    public static Trigger createItemCommandTrigger(String triggerId, String itemName, String command) {

        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("itemName", itemName);
        configuration.put("command", command);

        Trigger trigger = TriggerBuilder.create().withLabel(triggerId).withId(triggerId)
                .withTypeUID("core.ItemCommandTrigger").withConfiguration(new Configuration(configuration)).build();

        return trigger;
    }

    public static Trigger createChannelEventTrigger(String triggerId, String channelUID) {

        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("channelUID", channelUID);

        Trigger trigger = TriggerBuilder.create().withLabel(triggerId).withId(triggerId)
                .withTypeUID("core.ChannelEventTrigger").withConfiguration(new Configuration(configuration)).build();

        return trigger;
    }

    public static Trigger createThingChangeTrigger(String triggerId, String thingUID) {

        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("thingUID", thingUID);

        Trigger trigger = TriggerBuilder.create().withLabel(triggerId).withId(triggerId)
                .withTypeUID("core.ThingStatusChangeTrigger").withConfiguration(new Configuration(configuration))
                .build();

        return trigger;
    }

    public static Trigger createThingChangeTrigger(String triggerId, String thingUID, String status) {

        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("thingUID", thingUID);
        configuration.put("status", status);

        Trigger trigger = TriggerBuilder.create().withLabel(triggerId).withId(triggerId)
                .withTypeUID("core.ThingStatusChangeTrigger").withConfiguration(new Configuration(configuration))
                .build();

        return trigger;
    }

    public static Trigger createThingChangeTrigger(String triggerId, String thingUID, String status,
            String previousStatus) {

        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("thingUID", thingUID);
        configuration.put("status", status);
        configuration.put("previousStatus", previousStatus);

        Trigger trigger = TriggerBuilder.create().withLabel(triggerId).withId(triggerId)
                .withTypeUID("core.ThingStatusChangeTrigger").withConfiguration(new Configuration(configuration))
                .build();

        return trigger;
    }

    public static Trigger createThingUpdateTrigger(String triggerId, String thingUID) {

        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("thingUID", thingUID);

        Trigger trigger = TriggerBuilder.create().withLabel(triggerId).withId(triggerId)
                .withTypeUID("core.ThingStatusUpdateTrigger").withConfiguration(new Configuration(configuration))
                .build();

        return trigger;
    }

    public static Trigger createThingUpdateTrigger(String triggerId, String thingUID, String status) {

        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("thingUID", thingUID);
        configuration.put("status", status);

        Trigger trigger = TriggerBuilder.create().withLabel(triggerId).withId(triggerId)
                .withTypeUID("core.ThingStatusUpdateTrigger").withConfiguration(new Configuration(configuration))
                .build();

        return trigger;
    }

    public static Trigger createThingUpdateTrigger(String triggerId, String thingUID, String status,
            String previousStatus) {

        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("thingUID", thingUID);
        configuration.put("status", status);
        configuration.put("previousStatus", previousStatus);

        Trigger trigger = TriggerBuilder.create().withLabel(triggerId).withId(triggerId)
                .withTypeUID("core.ThingStatusUpdateTrigger").withConfiguration(new Configuration(configuration))
                .build();

        return trigger;
    }

    public static Trigger createChannelEventTrigger(String triggerId, String channelUID, String event) {

        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("channelUID", channelUID);
        configuration.put("event", event);

        Trigger trigger = TriggerBuilder.create().withLabel(triggerId).withId(triggerId)
                .withTypeUID("core.ChannelEventTrigger").withConfiguration(new Configuration(configuration)).build();

        return trigger;
    }

}
