package ${packageName};

import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.openhab.automation.java223.common.BindingInjector;
import org.openhab.automation.java223.common.InjectBinding;
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
import org.openhab.core.thing.ThingManager;
import org.openhab.core.thing.ThingRegistry;
import org.openhab.core.types.State;
import org.openhab.core.voice.VoiceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import helper.rules.RuleAnnotationParser;
import helper.rules.RuleParserException;

/**
 * Base helper class for all Java223 Scripts.
 * This class needs the helper-lib.jar
 * Features :
 * - Standard JSR223 OpenHAB bindings already declared as fields for immediate access
 * - Auto execution of a parsing rule method (internalParseRules calling RuleAnnotationParser)
 * - Additional shortcut to useful services (automationManager, sharedCache, ruleManager, metadataRegistry)
 * - Include other generated helper classes (_items, _actions, _things)
 *
 * @author Gwendal Roulleau - Initial contribution
 */
public abstract class Java223Script {

    // warning : default openhab logger level is error
    protected Logger logger = LoggerFactory.getLogger(this.getClass());

    // all OpenHAB input as a convenience object :
    protected @InjectBinding Map<String, Object> bindings;

    // default preset
    protected @InjectBinding Map<String, State> items;
    protected @InjectBinding ItemRegistry ir;
    protected @InjectBinding ItemRegistry itemRegistry;
    protected @InjectBinding ThingRegistry things;
    protected @InjectBinding RuleRegistry rules;
    protected @InjectBinding ScriptBusEvent events;
    protected @InjectBinding ScriptThingActions actions;
    protected @InjectBinding ScriptExtensionManagerWrapper scriptExtension;
    protected @InjectBinding ScriptExtensionManagerWrapper se;
    protected @InjectBinding VoiceManager voice;
    protected @InjectBinding AudioManager audio;

    // from ruleSupport preset
    protected @InjectBinding(preset = "RuleSupport", named = "automationManager") ScriptedAutomationManager automationManager;
    protected @InjectBinding(preset = "cache", named = "sharedCache") ValueCache sharedCache;
    protected @InjectBinding(preset = "cache", named = "privateCache") ValueCache privateCache;

    // for transformation support
    protected @Nullable Object input;

    // additional useful classes :
    protected @InjectBinding RuleManager ruleManager;
    protected @InjectBinding ThingManager thingManager;
    protected @InjectBinding MetadataRegistry metadataRegistry;

    // generated classes
    protected @InjectBinding Items _items;
    protected @InjectBinding Actions _actions;
    protected @InjectBinding Things _things;

    /**
     * Parse all method/field rules in this script
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
     * Use this method to manually inject bindings value in an object of your choice.
     * You probably don't need this (you should use your object as a library and let this helper framework injects it)
     */
    public void injectBindings(Object objectToInjectInto) {
        BindingInjector.injectBindingsInto(this.getClass(), bindings, objectToInjectInto);
    }
}
