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
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.automation.javascripting.common.JavaScriptingConstants;
import org.openhab.automation.javascripting.ruleparser.RuleAnnotationParser;
import org.openhab.core.audio.AudioManager;
import org.openhab.core.automation.RuleManager;
import org.openhab.core.automation.RuleRegistry;
import org.openhab.core.automation.module.script.ScriptExtensionManagerWrapper;
import org.openhab.core.automation.module.script.defaultscope.ScriptBusEvent;
import org.openhab.core.automation.module.script.defaultscope.ScriptThingActions;
import org.openhab.core.automation.module.script.rulesupport.shared.ScriptedAutomationManager;
import org.openhab.core.automation.module.script.rulesupport.shared.simple.SimpleRule;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.items.MetadataRegistry;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.thing.ThingRegistry;
import org.openhab.core.thing.binding.ThingActions;
import org.openhab.core.types.State;
import org.openhab.core.voice.VoiceManager;

/**
 * Base Class for all Java Scripts
 *
 * @author Jürgen Weber - Initial contribution
 */
@NonNullByDefault
public abstract class Script implements Scriptable {

    protected Logger logger = Logger.getLogger(this.getClass());

    protected @NonNullByDefault({}) Map<String, Object> bindings;

    // default preset
    protected @NonNullByDefault({}) Map<String, State> items;
    protected @NonNullByDefault({}) ItemRegistry ir;
    protected @NonNullByDefault({}) ItemRegistry itemRegistry;
    protected @NonNullByDefault({}) ThingRegistry things;
    protected @NonNullByDefault({}) RuleRegistry rules;
    protected @NonNullByDefault({}) ScriptBusEvent events;
    protected @NonNullByDefault({}) ScriptThingActions actions;
    protected @NonNullByDefault({}) ScriptExtensionManagerWrapper scriptExtension;
    protected @NonNullByDefault({}) ScriptExtensionManagerWrapper se;
    protected @NonNullByDefault({}) VoiceManager voice;
    protected @NonNullByDefault({}) AudioManager audio;

    // RuleSupport preset
    protected @NonNullByDefault({}) Map<String, Object> ruleSupport;
    protected @NonNullByDefault({}) ScriptedAutomationManager automationManager;

    // some static shortcut
    protected OnOffType ON = OnOffType.ON;
    protected OnOffType OFF = OnOffType.OFF;

    // for transformation support
    protected @Nullable Object input;

    // additional useful class :
    protected @NonNullByDefault({}) RuleManager ruleManager;
    protected @NonNullByDefault({}) MetadataRegistry metadataRegistry;

    /**
     * called on the script load
     */
    @Override
    public @Nullable Object eval() throws Exception {

        logger.trace("eval()");

        try {

            Object result;

            result = onLoad();
            RuleAnnotationParser.parse(this, automationManager);

            return result;

        } catch (Exception e) {
            logger.error("Script eval error", e);
            throw e;
        }
    }

    /*
     * called by JavaRuleEngine before eval()
     */
    @Override
    public void setBindings(Map<String, Object> bindings) {
        this.bindings = bindings;

        // default presets
        this.items = (Map<String, State>) bindings.get("items");
        this.itemRegistry = (ItemRegistry) bindings.get("itemRegistry");
        this.ir = itemRegistry;
        this.things = (ThingRegistry) bindings.get("things");
        this.rules = (RuleRegistry) bindings.get("rules");
        this.events = (ScriptBusEvent) bindings.get("events");
        this.actions = (ScriptThingActions) bindings.get("actions");
        this.scriptExtension = (ScriptExtensionManagerWrapper) bindings.get("scriptExtension");
        this.se = scriptExtension;
        this.voice = (VoiceManager) bindings.get("voice");
        this.audio = (AudioManager) bindings.get("audio");

        // automatically import the additional rulesupport preset to get the automation manager and make simple rules
        this.ruleSupport = scriptExtension.importPreset("RuleSupport");
        this.automationManager = (ScriptedAutomationManager) ruleSupport.get("automationManager");

        // input is set for transformations
        this.input = bindings.get("input");

        // retrieve other custom bindings injected by the JavaScriptEngineFactory
        this.ruleManager = (RuleManager) bindings.get(JavaScriptingConstants.RULE_MANAGER);
        this.metadataRegistry = (MetadataRegistry) bindings.get(JavaScriptingConstants.METADATA_REGISTRY);
    }

    /*
     * called by JavaRuleEngine after eval()
     */
    public Map<String, Object> getBindings() {
        return bindings;
    }

    // hook for the concrete script class to be called after initialization
    protected @Nullable Object onLoad() {
        return null;
    }

    /**
     * Helper method to call action. You should use the dedicated generated class if possible
     *
     * @param thingActions
     * @param method
     * @param params
     */
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

    protected RuleBuilder getNewRuleBuilder(SimpleRule sr, String name) {
        return new RuleBuilder(automationManager);
    }
}
