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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openhab.automation.javascripting.annotation.RuleAnnotationParser;
import org.openhab.core.audio.AudioManager;
import org.openhab.core.automation.RuleManager;
import org.openhab.core.automation.Trigger;
import org.openhab.core.automation.module.script.ScriptExtensionManagerWrapper;
import org.openhab.core.automation.module.script.defaultscope.ScriptBusEvent;
import org.openhab.core.automation.module.script.defaultscope.ScriptThingActions;
import org.openhab.core.automation.module.script.rulesupport.shared.ScriptedAutomationManager;
import org.openhab.core.automation.module.script.rulesupport.shared.simple.SimpleRule;
import org.openhab.core.automation.util.TriggerBuilder;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.thing.ThingRegistry;
import org.openhab.core.thing.binding.ThingActions;
import org.openhab.core.voice.VoiceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base Class for all Java Scripts
 *
 * @author Jürgen Weber - Initial contribution
 */

public abstract class Script {

    protected static Logger logger = LoggerFactory.getLogger(Script.class);

    protected Map<String, Object> bindings;

    protected ScriptedAutomationManager automationManager;

    protected ScriptThingActions actions;

    protected ScriptBusEvent events;

    protected ScriptExtensionManagerWrapper se;

    protected Map<String, Object> ruleSupport;

    protected String ON = "ON";
    protected String OFF = "OFF";

    protected ItemRegistry itemRegistry;

    protected ThingRegistry things;

    protected VoiceManager voice;

    protected AudioManager audio;

    protected Object input;

    protected RuleManager ruleManager;

    /**
     * called on the script load
     */
    public Object eval() throws Exception {

        logger.trace("eval()");

        try {

            Object result;

            result = onLoad();
            parseAnnotations();

            return result;

        } catch (Exception e) {
            logger.error("Script eval", e);
            throw e;
        }
    }

    public void makeShortcuts() {

        this.se = (ScriptExtensionManagerWrapper) bindings.get("se");

        this.ruleSupport = se.importPreset("RuleSupport");

        this.actions = (ScriptThingActions) bindings.get("actions");

        this.events = (ScriptBusEvent) bindings.get("events");

        this.automationManager = (ScriptedAutomationManager) ruleSupport.get("automationManager");

        this.ruleManager = (RuleManager) bindings.get("ruleManager");

        this.itemRegistry = (ItemRegistry) bindings.get("itemRegistry");

        this.things = (ThingRegistry) bindings.get("things");

        this.voice = (VoiceManager) bindings.get("voice");

        this.audio = (AudioManager) bindings.get("audio");

        // input is set for transformations

        this.input = bindings.get("input");
    }

    /*
     * called by JavaRuleEngine before eval()
     */
    public void setBindings(Map<String, Object> bindings) {
        this.bindings = bindings;
    }

    /*
     * called by JavaRuleEngine after eval()
     */
    public Map<String, Object> getBindings() {
        return bindings;
    }

    // to be implemented by the concrete script class
    protected Object onLoad() {
        return null;
    }

    private void parseAnnotations() throws Exception {
        new RuleAnnotationParser(this).parse();
    }

    // Utility methods
    // very inspired by pravussum's groovy rules
    // https://community.openhab.org/t/examples-for-groovy-scripts/131121/9
    public Trigger createSystemStartlevelTrigger(String triggerId, String startlevel) {

        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("startlevel", startlevel);

        Trigger trigger = TriggerBuilder.create().withLabel(triggerId).withId(triggerId)
                .withTypeUID("core.SystemStartlevelTrigger").withConfiguration(new Configuration(configuration))
                .build();

        return trigger;
    }

    public Trigger createGenericCronTrigger(String triggerId, String cronExpression) {

        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("cronExpression", cronExpression);

        Trigger trigger = TriggerBuilder.create().withLabel(triggerId).withId(triggerId)
                .withTypeUID("timer.GenericCronTrigger").withConfiguration(new Configuration(configuration)).build();

        return trigger;
    }

    public Trigger createItemStateChangeTrigger(String triggerId, String itemName) {

        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("itemName", itemName);

        Trigger trigger = TriggerBuilder.create().withLabel(triggerId).withId(triggerId)
                .withTypeUID("core.ItemStateChangeTrigger").withConfiguration(new Configuration(configuration)).build();

        return trigger;
    }

    public Trigger createItemStateChangeTrigger(String triggerId, String itemName, String newState) {

        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("itemName", itemName);
        configuration.put("state", newState);

        Trigger trigger = TriggerBuilder.create().withLabel(triggerId).withId(triggerId)
                .withTypeUID("core.ItemStateChangeTrigger").withConfiguration(new Configuration(configuration)).build();

        return trigger;
    }

    public Trigger createItemStateChangeTrigger(String triggerId, String itemName, String previousState,
            String newState) {

        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("itemName", itemName);
        configuration.put("previousState", previousState);
        configuration.put("state", newState);

        Trigger trigger = TriggerBuilder.create().withLabel(triggerId).withId(triggerId)
                .withTypeUID("core.ItemStateChangeTrigger").withConfiguration(new Configuration(configuration)).build();

        return trigger;
    }

    public Trigger createItemStateUpdateTrigger(String triggerId, String itemName) {

        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("itemName", itemName);

        Trigger trigger = TriggerBuilder.create().withLabel(triggerId).withId(triggerId)
                .withTypeUID("core.ItemStateUpdateTrigger").withConfiguration(new Configuration(configuration)).build();

        return trigger;
    }

    public Trigger createItemStateUpdateTrigger(String triggerId, String itemName, String state) {

        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("itemName", itemName);
        configuration.put("command", state);

        Trigger trigger = TriggerBuilder.create().withLabel(triggerId).withId(triggerId)
                .withTypeUID("core.ItemStateUpdateTrigger").withConfiguration(new Configuration(configuration)).build();

        return trigger;
    }

    public Trigger createItemStateUpdateTrigger(String triggerId, String itemName, String previousState,
            String newState) {

        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("itemName", itemName);
        configuration.put("previousState", previousState);
        configuration.put("state", newState);

        Trigger trigger = TriggerBuilder.create().withLabel(triggerId).withId(triggerId)
                .withTypeUID("core.ItemStateUpdateTrigger").withConfiguration(new Configuration(configuration)).build();

        return trigger;
    }

    public Trigger createItemCommandTrigger(String triggerId, String itemName, String command) {

        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("itemName", itemName);
        configuration.put("command", command);

        Trigger trigger = TriggerBuilder.create().withLabel(triggerId).withId(triggerId)
                .withTypeUID("core.ItemCommandTrigger").withConfiguration(new Configuration(configuration)).build();

        return trigger;
    }

    public Trigger createChannelEventTrigger(String triggerId, String channelUID) {

        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("channelUID", channelUID);

        Trigger trigger = TriggerBuilder.create().withLabel(triggerId).withId(triggerId)
                .withTypeUID("core.ChannelEventTrigger").withConfiguration(new Configuration(configuration)).build();

        return trigger;
    }

    public Trigger createThingChangeTrigger(String triggerId, String thingUID) {

        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("thingUID", thingUID);

        Trigger trigger = TriggerBuilder.create().withLabel(triggerId).withId(triggerId)
                .withTypeUID("core.ThingStatusChangeTrigger").withConfiguration(new Configuration(configuration))
                .build();

        return trigger;
    }

    public Trigger createThingChangeTrigger(String triggerId, String thingUID, String status) {

        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("thingUID", thingUID);
        configuration.put("status", status);

        Trigger trigger = TriggerBuilder.create().withLabel(triggerId).withId(triggerId)
                .withTypeUID("core.ThingStatusChangeTrigger").withConfiguration(new Configuration(configuration))
                .build();

        return trigger;
    }

    public Trigger createThingChangeTrigger(String triggerId, String thingUID, String status, String previousStatus) {

        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("thingUID", thingUID);
        configuration.put("status", status);
        configuration.put("previousStatus", previousStatus);

        Trigger trigger = TriggerBuilder.create().withLabel(triggerId).withId(triggerId)
                .withTypeUID("core.ThingStatusChangeTrigger").withConfiguration(new Configuration(configuration))
                .build();

        return trigger;
    }

    public Trigger createThingUpdateTrigger(String triggerId, String thingUID) {

        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("thingUID", thingUID);

        Trigger trigger = TriggerBuilder.create().withLabel(triggerId).withId(triggerId)
                .withTypeUID("core.ThingStatusUpdateTrigger").withConfiguration(new Configuration(configuration))
                .build();

        return trigger;
    }

    public Trigger createThingUpdateTrigger(String triggerId, String thingUID, String status) {

        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("thingUID", thingUID);
        configuration.put("status", status);

        Trigger trigger = TriggerBuilder.create().withLabel(triggerId).withId(triggerId)
                .withTypeUID("core.ThingStatusUpdateTrigger").withConfiguration(new Configuration(configuration))
                .build();

        return trigger;
    }

    public Trigger createThingUpdateTrigger(String triggerId, String thingUID, String status, String previousStatus) {

        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("thingUID", thingUID);
        configuration.put("status", status);
        configuration.put("previousStatus", previousStatus);

        Trigger trigger = TriggerBuilder.create().withLabel(triggerId).withId(triggerId)
                .withTypeUID("core.ThingStatusUpdateTrigger").withConfiguration(new Configuration(configuration))
                .build();

        return trigger;
    }

    public Trigger createChannelEventTrigger(String triggerId, String channelUID, String event) {

        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("channelUID", channelUID);
        configuration.put("event", event);

        Trigger trigger = TriggerBuilder.create().withLabel(triggerId).withId(triggerId)
                .withTypeUID("core.ChannelEventTrigger").withConfiguration(new Configuration(configuration)).build();

        return trigger;
    }

    protected static class RuleBuilder {

        private ScriptedAutomationManager automationManager;
        private SimpleRule sr = null;
        private List<Trigger> triggers = new ArrayList<Trigger>();
        private String name;

        private RuleBuilder(ScriptedAutomationManager automationManager, SimpleRule sr) {
            this.automationManager = automationManager;
            this.sr = sr;
        }

        public RuleBuilder withName(String name) {
            this.name = name;
            return this;
        }

        public RuleBuilder withTrigger(Trigger trigger) {
            triggers.add(trigger);
            return this;
        }

        public RuleBuilder withTriggers(List<Trigger> triggers) {
            this.triggers.addAll(triggers);
            return this;
        }

        public void activate() {
            sr.setName(name);
            sr.setTriggers(triggers);
            automationManager.addRule(sr);
        }
    }

    public void invokeAction(ThingActions thingActions, String method, Object... params) {
        Class<?>[] paramClasses = new Class<?>[params.length];

        for (int i = 0; i < params.length; i++) {
            paramClasses[i] = params[i].getClass();
        }
        try {

            Method m = thingActions.getClass().getMethod(method, paramClasses);
            m.invoke(thingActions, params);
        } catch (Exception e) {
            throw new JavaScriptingException("Cannot invoke action for " + method);
        }
    }

    protected RuleBuilder ruleBuilder(SimpleRule sr) {
        return new RuleBuilder(automationManager, sr);
    }

    public void activateRule(String name, SimpleRule sr, List<Trigger> triggers) {
        ruleBuilder(sr).withName(name).withTriggers(triggers).activate();
    }
}
