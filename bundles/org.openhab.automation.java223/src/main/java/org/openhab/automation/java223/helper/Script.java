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

import java.lang.reflect.Method;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.automation.java223.helper.annotations.InjectBinding;
import org.openhab.automation.java223.helper.annotations.RunScript;
import org.openhab.automation.java223.internal.strategy.Java223Strategy;
import org.openhab.core.audio.AudioManager;
import org.openhab.core.automation.RuleManager;
import org.openhab.core.automation.RuleRegistry;
import org.openhab.core.automation.module.script.ScriptExtensionManagerWrapper;
import org.openhab.core.automation.module.script.defaultscope.ScriptBusEvent;
import org.openhab.core.automation.module.script.defaultscope.ScriptThingActions;
import org.openhab.core.automation.module.script.rulesupport.shared.ScriptedAutomationManager;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.items.MetadataRegistry;
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
public abstract class Script {

    protected Logger logger = Logger.getLogger(this.getClass());

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

    // for transformation support
    protected @Nullable Object input;

    // additional useful class :
    protected @InjectBinding @NonNullByDefault({}) RuleManager ruleManager;
    protected @InjectBinding @NonNullByDefault({}) MetadataRegistry metadataRegistry;

    @RunScript
    public void internalParseRules() {
        try {
            RuleAnnotationParser.parse(this, automationManager, null);
        } catch (IllegalArgumentException | IllegalAccessException | RuleParserException e) {
            logger.error("Cannot parse rules", e);
        }
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
            throw new Java223Exception("Cannot invoke action for " + method);
        }
    }

    public void injectBindings(Object objectToInjectInto) {
        Java223Strategy.injectBindingsInto(bindings, objectToInjectInto);
    }
}
