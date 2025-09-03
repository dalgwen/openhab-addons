/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
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
package org.openhab.automation.java223.internal.strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

import ch.obermuhlner.scriptengine.java.compilation.ScriptInterceptorStrategy;

/**
 * Wraps a script in boilerplate code if not present.
 * Must respect some conditions to be wrapped correctly:
 * - must not contain "public class"
 * - line containing import must start with "import "
 * - you can globally return a value, but take care to put the "return " keyword at the beginning of its own line
 * - you cannot declare method (in fact, your script is already wrapped inside a method)
 *
 * @author Gwendal Roulleau - Initial contribution
 */
@NonNullByDefault
public class ScriptWrappingStrategy implements ScriptInterceptorStrategy {

    private static final Pattern NAME_PATTERN = Pattern.compile("public\\s+class\\s+.*");
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("package\\s+[A-Za-z][A-Za-z0-9_$.]*;\\s*");
    private static final Pattern IMPORT_PATTERN = Pattern.compile("import\\s+[A-Za-z][A-Za-z0-9_$.]*;\\s*");

    private static final String BOILERPLATE_CODE_COMMON_IMPORT = """
            import org.openhab.core.library.items.*;
            import org.openhab.core.library.types.*;
            import org.openhab.core.library.types.HSBType.*;
            import org.openhab.core.library.types.IncreaseDecreaseType.*;
            import org.openhab.core.library.types.NextPreviousType.*;
            import org.openhab.core.library.types.OnOffType.*;
            import org.openhab.core.library.types.OpenClosedType.*;
            import org.openhab.core.library.types.PercentType.*;
            import org.openhab.core.library.types.PlayPauseType.*;
            import org.openhab.core.library.types.PointType.*;
            import org.openhab.core.library.types.QuantityType.*;
            import org.openhab.core.library.types.RewindFastforwardType.*;
            import org.openhab.core.library.types.StopMoveType.*;
            import org.openhab.core.library.types.UpDownType.*;
            """;

    private static final String BOILERPLATE_CODE_IMPORT_WITHOUT_GENERATION = """
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
            """;

    private static final String BOILERPLATE_CODE_IMPORT_DECLARATION_WITH_GENERATION = """

            import helper.generated.Java223Script;
            """;

    private static final String BOILERPLATE_CODE_BEFORE_WITH_GENERATION =

            BOILERPLATE_CODE_IMPORT_DECLARATION_WITH_GENERATION + BOILERPLATE_CODE_COMMON_IMPORT + """

                    public class WrappedJavaScript extends Java223Script {
                        public Object main() {
                    """;

    private static final String BOILERPLATE_CODE_INJECTED_MEMBERS_DECLARATION = """
            protected Logger logger = LoggerFactory.getLogger(this.getClass());

            protected @InjectBinding Map<String, Object> bindings;

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

            protected @InjectBinding(preset = "RuleSupport", named = "automationManager") ScriptedAutomationManager automationManager;
            protected @InjectBinding(preset = "cache", named = "sharedCache") ValueCache sharedCache;
            protected @InjectBinding(preset = "cache", named = "privateCache") ValueCache privateCache;

            protected Object input;

            protected @InjectBinding RuleManager ruleManager;
            protected @InjectBinding ThingManager thingManager;
            protected @InjectBinding MetadataRegistry metadataRegistry;
            """;

    private static final String BOILERPLATE_CODE_BEFORE_WITHOUT_GENERATION =

            BOILERPLATE_CODE_IMPORT_WITHOUT_GENERATION + BOILERPLATE_CODE_COMMON_IMPORT
                    + " \npublic class WrappedJavaScript {" + BOILERPLATE_CODE_INJECTED_MEMBERS_DECLARATION
                    + "\n\tpublic Object main() {\n";

    private static final String BOILERPLATE_CODE_AFTER = """
                }
            }
            """;

    private Boolean enableHelper;

    public ScriptWrappingStrategy(Boolean enableHelper) {
        this.enableHelper = enableHelper;
    }

    @Override
    public @Nullable String intercept(@Nullable String script) {

        if (script == null) {
            return "";
        }
        List<String> lines = script.lines().toList();

        String packageDeclarationLine = "";
        List<String> importLines = new ArrayList<>(lines.size());
        List<String> scriptLines = new ArrayList<>(lines.size());
        boolean returnIsPresent = false;

        // parse the file and sort lines in different categories
        for (String line : lines) {
            line = line.trim();
            if (NAME_PATTERN.matcher(line).matches()) { // a class declaration is found. No need to wrap
                return script;
            }
            if (PACKAGE_PATTERN.matcher(line).matches()) {
                packageDeclarationLine = line;
            } else if (IMPORT_PATTERN.matcher(line).matches()) {
                importLines.add(line);
            } else {
                if (line.startsWith("return")) {
                    returnIsPresent = true;
                }
                scriptLines.add(line);
            }
        }

        // recompose a complete script with the different parts
        StringBuilder modifiedScript = new StringBuilder();
        modifiedScript.append(packageDeclarationLine).append("\n");
        modifiedScript.append(String.join("\n", importLines));
        modifiedScript.append("\n");
        if (enableHelper) {
            modifiedScript.append(BOILERPLATE_CODE_BEFORE_WITH_GENERATION);
        } else {
            modifiedScript.append(BOILERPLATE_CODE_BEFORE_WITHOUT_GENERATION);
        }
        modifiedScript.append(String.join("\n", scriptLines));
        modifiedScript.append("\n");
        if (!returnIsPresent) {
            modifiedScript.append("return null;");
        }
        modifiedScript.append(BOILERPLATE_CODE_AFTER);

        return modifiedScript.toString();
    }

    public void setEnableHelper(Boolean enableHelper) {
        this.enableHelper = enableHelper;
    }
}
