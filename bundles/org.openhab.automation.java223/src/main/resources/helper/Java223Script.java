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
package helper;

import java.lang.reflect.Method;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.automation.java223.common.BindingInjector;
import org.openhab.automation.java223.common.InjectBinding;
import org.openhab.automation.java223.common.Java223Exception;
import org.openhab.automation.java223.common.RunScript;
import org.openhab.core.audio.AudioManager;
import org.openhab.core.automation.RuleManager;
import org.openhab.core.automation.RuleRegistry;
import org.openhab.core.automation.module.script.ScriptExtensionManagerWrapper;
import org.openhab.core.automation.module.script.defaultscope.ScriptBusEvent;
import org.openhab.core.automation.module.script.defaultscope.ScriptThingActions;
import org.openhab.core.automation.module.script.rulesupport.shared.ScriptedAutomationManager;
import org.openhab.core.automation.module.script.rulesupport.shared.ValueCache;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.items.MetadataRegistry;
import org.openhab.core.thing.ThingRegistry;
import org.openhab.core.thing.binding.ThingActions;
import org.openhab.core.types.State;
import org.openhab.core.voice.VoiceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import helper.generated.Actions;
import helper.generated.Items;
import helper.generated.Things;
import helper.rules.RuleAnnotationParser;
import helper.rules.RuleParserException;

/**
 * Base helper class for all Java223 Scripts.
 * Standard JSR223 openhab bindings already declared as mandatory
 * Helper generated class included
 * Auto execution of a parsing rule method
 * Additionnal shortcut to usefull services  
 *
 * @author Gwendal Roulleau - Initial contribution
 */
@NonNullByDefault
public abstract class Java223Script {

    protected Logger logger = LoggerFactory.getLogger(this.getClass());

    protected @InjectBinding @NonNullByDefault({}) Map<String, Object> bindings;

    // default preset
    protected @InjectBinding @NonNullByDefault({}) Map<String, State> items;
    protected @InjectBinding @NonNullByDefault({}) ItemRegistry ir;
    protected @InjectBinding @NonNullByDefault({}) ItemRegistry itemRegistry;
    protected @InjectBinding @NonNullByDefault({}) ThingRegistry things;
    protected @InjectBinding @NonNullByDefault({}) RuleRegistry rules;
    protected @InjectBinding @NonNullByDefault({}) ScriptBusEvent events;
    protected @InjectBinding @NonNullByDefault({}) ScriptThingActions actions;
    protected @InjectBinding @NonNullByDefault({}) ScriptExtensionManagerWrapper scriptExtension;
    protected @InjectBinding @NonNullByDefault({}) ScriptExtensionManagerWrapper se;
    protected @InjectBinding @NonNullByDefault({}) VoiceManager voice;
    protected @InjectBinding @NonNullByDefault({}) AudioManager audio;

    // from ruleSupport preset
    protected @InjectBinding(preset = "RuleSupport", named = "automationManager") @NonNullByDefault({}) ScriptedAutomationManager automationManager;
    protected @InjectBinding(preset = "cache", named = "sharedCache") @NonNullByDefault({}) ValueCache sharedCache;

    // for transformation support
    protected @Nullable Object input;

    // additional useful class :
    protected @InjectBinding @NonNullByDefault({}) RuleManager ruleManager;
    protected @InjectBinding @NonNullByDefault({}) MetadataRegistry metadataRegistry;
    
    // inject generated libraries 
    protected @InjectBinding @NonNullByDefault({}) Things _things;
    protected @InjectBinding @NonNullByDefault({}) Actions _actions;
    protected @InjectBinding @NonNullByDefault({}) Items _items;

    /**
     * Parse all method rules presents in this script
     */
    @RunScript
    public void internalParseRules() {
        try {
            RuleAnnotationParser.parse(this, automationManager);
        } catch (IllegalArgumentException | IllegalAccessException | RuleParserException e) {
            logger.error("Cannot parse rules", e);
        }
    }

    /**
     * Helper method to call arbitrary action. You should use the dedicated generated class _actions if possible
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
            throw new Java223Exception("Cannot invoke action for " + method);
        }
    }

    public void injectBindings(Object objectToInjectInto) {
        BindingInjector.injectBindingsInto(bindings, objectToInjectInto);
    }
}
