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
import org.openhab.core.automation.Trigger;
import org.openhab.core.automation.module.script.rulesupport.shared.ScriptedAutomationManager;
import org.openhab.core.automation.module.script.rulesupport.shared.simple.SimpleRule;
import org.openhab.core.automation.util.TriggerBuilder;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.items.Item;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.thing.ThingRegistry;
import org.openhab.core.thing.binding.ThingActions;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
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

    protected ScriptThingActionsProxy actions;

    protected ScriptBusEventProxy events;

    protected ScriptExtensionManagerWrapperProxy self;

    protected Map<String, Object> ruleSupport;

    protected String ON = "ON";
    protected String OFF = "OFF";

    protected ItemRegistry itemRegistry;

    protected ThingRegistry thingRegistry;

    protected VoiceManager voice;

    protected AudioManager audio;

    protected Object input;

    // ScriptExtensionManagerWrapper is in the bundle private
    // org.openhab.core.automation.module.script.internal.ScriptExtensionManagerWrapper
    // which we can only access by reflection (Java is not Groovy)

    protected static class ScriptExtensionManagerWrapperProxy {

        Object scriptExtensionManagerRef;

        ScriptExtensionManagerWrapperProxy(Object scriptExtensionManagerRef) {
            this.scriptExtensionManagerRef = scriptExtensionManagerRef;
        }

        @SuppressWarnings("unchecked")
        public Map<String, Object> importPreset(String preset) {

            Object o;
            Map<String, Object> presets = null;

            try {
                Method m = scriptExtensionManagerRef.getClass().getMethod("importPreset", String.class);
                o = m.invoke(scriptExtensionManagerRef, preset);
                presets = (Map<String, Object>) o;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            return presets;
        }
    }

    protected static class ScriptBusEventProxy {

        Object scriptBusEvent;

        ScriptBusEventProxy(Object scriptBusEvent) {
            this.scriptBusEvent = scriptBusEvent;
        }

        public Object sendCommand(String itemName, String commandString) {
            try {
                Method m = scriptBusEvent.getClass().getMethod("sendCommand", String.class, String.class);
                return m.invoke(scriptBusEvent, itemName, commandString);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public Object sendCommand(Item item, Number number) {
            try {
                Method m = scriptBusEvent.getClass().getMethod("sendCommand", Item.class, Number.class);
                return m.invoke(scriptBusEvent, item, number);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public Object sendCommand(Item item, String commandString) {
            try {
                Method m = scriptBusEvent.getClass().getMethod("sendCommand", Item.class, String.class);
                return m.invoke(scriptBusEvent, item, commandString);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public Object sendCommand(Item item, Command command) {
            try {
                Method m = scriptBusEvent.getClass().getMethod("sendCommand", Item.class, Command.class);
                return m.invoke(scriptBusEvent, item, command);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public Object postUpdate(Item item, Number state) {
            try {
                Method m = scriptBusEvent.getClass().getMethod("postUpdate", Item.class, Number.class);
                return m.invoke(scriptBusEvent, item, state);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public Object postUpdate(Item item, String stateAsString) {
            try {
                Method m = scriptBusEvent.getClass().getMethod("postUpdate", Item.class, String.class);
                return m.invoke(scriptBusEvent, item, stateAsString);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public Object postUpdate(String itemName, String stateString) {
            try {
                Method m = scriptBusEvent.getClass().getMethod("postUpdate", String.class, String.class);
                return m.invoke(scriptBusEvent, itemName, stateString);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public Object postUpdate(Item item, State state) {
            try {
                Method m = scriptBusEvent.getClass().getMethod("postUpdate", Item.class, String.class);
                return m.invoke(scriptBusEvent, item, state);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public Map<Item, State> storeStates(Item... items) {
            try {
                Method m = scriptBusEvent.getClass().getMethod("storeStates", Item[].class);
                return (Map<Item, State>) m.invoke(scriptBusEvent, items);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    protected static class ScriptThingActionsProxy {

        Object scriptThingActionsRef;

        ScriptThingActionsProxy(Object scriptThingActionsRef) {
            this.scriptThingActionsRef = scriptThingActionsRef;
        }

        public ThingActions get(String scope, String thingUid) {

            Object o;
            ThingActions actions = null;

            try {
                Method m = scriptThingActionsRef.getClass().getMethod("get", String.class, String.class);
                o = m.invoke(scriptThingActionsRef, scope, thingUid);
                actions = (ThingActions) o;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            return actions;
        }

        public void invoke(ThingActions thingActions, String method, Object... params) {
            Class<?>[] paramClasses = new Class<?>[params.length];

            for (int i = 0; i < params.length; i++) {
                paramClasses[i] = params[i].getClass();
            }
            try {

                Method m = thingActions.getClass().getMethod(method, paramClasses);
                m.invoke(thingActions, params);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    /*
     * called by JavaRuleEngine on script load
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

        Object se = bindings.get("se");

        self = new ScriptExtensionManagerWrapperProxy(se);

        ruleSupport = self.importPreset("RuleSupport");

        Object actions = bindings.get("actions");

        this.actions = new ScriptThingActionsProxy(actions);

        Object events = bindings.get("events");

        this.events = new ScriptBusEventProxy(events);

        automationManager = (ScriptedAutomationManager) ruleSupport.get("automationManager");

        itemRegistry = (ItemRegistry) bindings.get("itemRegistry");

        thingRegistry = (ThingRegistry) bindings.get("things");

        voice = (VoiceManager) bindings.get("voice");

        audio = (AudioManager) bindings.get("audio");

        // input is set for transformations

        input = bindings.get("input");
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

    protected RuleBuilder ruleBuilder(SimpleRule sr) {
        return new RuleBuilder(automationManager, sr);
    }

    public void activateRule(String name, SimpleRule sr, List<Trigger> triggers) {
        ruleBuilder(sr).withName(name).withTriggers(triggers).activate();
    }
}
