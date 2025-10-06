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
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.automation.module.script.ScriptEngineContainer;
import org.openhab.core.automation.module.script.ScriptEngineManager;
import org.openhab.core.thing.binding.ThingActions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.obermuhlner.scriptengine.java.compilation.ScriptInterceptorStrategy;

/**
 * Wraps a script in boilerplate code if not present.
 * Must respect some conditions to be wrapped correctly:
 * - must not contain "public class"
 * - line containing import must start with "import "
 * - you can globally return a value, but take care to put the "return " keyword at the beginning of its own line
 * - you cannot declare a method (in fact, your script is already wrapped inside a method)
 *
 * @author Gwendal Roulleau - Initial contribution
 */
@NonNullByDefault
public class ScriptWrappingStrategy implements ScriptInterceptorStrategy {

    private static final Logger logger = LoggerFactory.getLogger(ScriptWrappingStrategy.class);

    private static final Pattern NAME_PATTERN = Pattern.compile("public\\s+class\\s+.*");
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("package\\s+[A-Za-z][A-Za-z0-9_$.]*;\\s*");
    private static final Pattern IMPORT_PATTERN = Pattern.compile("import\\s+[A-Za-z][A-Za-z0-9_$.]*;\\s*");

    private static final String BOILERPLATE_CODE_COMMON_IMPORT = """
            import org.openhab.core.library.items.*;
            import org.openhab.core.library.types.*;
            """;

    private static final String BOILERPLATE_CODE_IMPORT_WITHOUT_GENERATION = """
            import org.openhab.automation.java223.common.BindingInjector;
            import org.openhab.automation.java223.common.InjectBinding;
            import org.openhab.automation.java223.common.RunScript;
            import org.openhab.core.audio.AudioManager;
            import org.openhab.core.automation.RuleManager;
            import org.openhab.core.automation.RuleRegistry;
            import org.openhab.core.automation.module.script.LifecycleScriptExtensionProvider.LifecycleTracker;
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
            import java.util.Map;
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

            protected @InjectBinding LifecycleTracker lifecycleTracker;

            protected @InjectBinding(preset = "RuleSupport") ScriptedAutomationManager automationManager;
            protected @InjectBinding(preset = "cache") ValueCache sharedCache;
            protected @InjectBinding(preset = "cache") ValueCache privateCache;

            protected String input;

            protected @InjectBinding RuleManager ruleManager;
            protected @InjectBinding ThingManager thingManager;
            protected @InjectBinding MetadataRegistry metadataRegistry;
            """;

    private static final String BOILERPLATE_CODE_BEFORE_WITHOUT_GENERATION = BOILERPLATE_CODE_IMPORT_WITHOUT_GENERATION
            + BOILERPLATE_CODE_COMMON_IMPORT + " \npublic class WrappedJavaScript {"
            + BOILERPLATE_CODE_INJECTED_MEMBERS_DECLARATION + "\n\tpublic Object main() {\n";

    private static final String BOILERPLATE_CODE_AFTER = """
                }
            }
            """;

    @Nullable
    private String defaultPresetImportList = null;
    private final Lock defaultPresetImportListLock = new ReentrantLock();
    private final ScriptEngineManager scriptEngineManager;

    private Boolean enableHelper;

    public ScriptWrappingStrategy(Boolean enableHelper, ScriptEngineManager scriptEngineManager) {
        this.enableHelper = enableHelper;
        this.scriptEngineManager = scriptEngineManager;
    }

    private String getDefaultPresetImportList() {
        String localDefaultImportList = defaultPresetImportList;
        if (localDefaultImportList == null) {
            defaultPresetImportListLock.lock();
            try {
                // traditional lock pattern: check again if already computed after gaining the lock
                localDefaultImportList = defaultPresetImportList;
                if (localDefaultImportList != null) {
                    return localDefaultImportList;
                }

                // make a fake script engine to get the default imports
                ScriptEngineContainer scriptEngineContainer = scriptEngineManager.createScriptEngine("java",
                        "java223-fakeScriptGetterDefaultScope");
                if (scriptEngineContainer == null) {
                    throw new IllegalStateException(
                            "Could not create ScriptEngineContainer for java223-fakeScriptGetterDefaultScope");
                }
                ScriptEngine scriptEngine = scriptEngineContainer.getScriptEngine();
                Bindings bindings = scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE);

                // create a list of imports for each binding found
                List<String> importStatements = bindings.values().stream().map(this::generateImportStatement)
                        .filter(Objects::nonNull).sorted().toList();
                localDefaultImportList = String.join("\n", importStatements) + "\n\n";

                this.defaultPresetImportList = localDefaultImportList;
            } finally {
                defaultPresetImportListLock.unlock();
            }
        }
        return localDefaultImportList;
    }

    /**
     * Generates a Java import statement for a given Class or enum member.
     * Reject action
     *
     * @param parameter The Class object or an enum member.
     * @return A String representing the import statement, or an empty string if no import is applicable
     *         (e.g., for primitive types, arrays, or classes in the default package).
     * @throws IllegalArgumentException if the parameter is null or not a Class or an enum member.
     */
    @Nullable
    private String generateImportStatement(Object parameter) {
        switch (parameter) {
            case Class<?> clazz -> {
                String canonicalName = clazz.getCanonicalName();

                // not directly accessible from scripts (internal classes)
                if (ThingActions.class.isAssignableFrom(clazz)) {
                    return null;
                }

                // Primitive types (e.g., int.class), array types (e.g., String[].class),
                // and classes without a canonical name (e.g., anonymous classes) do not
                // have standard import statements.
                if (clazz.isPrimitive() || clazz.isArray()) {
                    return null;
                }

                Package aPackage = clazz.getPackage();
                String packageName = aPackage != null ? aPackage.getName() : null;

                // TODO fix why can't we import org.openhab.core.library.unit ??
                if ("org.openhab.core.library.unit".equals(packageName)) {
                    return null;
                }

                if (packageName != null && !packageName.isEmpty()) {
                    return "import " + canonicalName + ";";
                } else {
                    // Class is in the default package, no import statement needed.
                    return null;
                }
            }
            case Enum<?> enumMember -> {
                Class<?> enumClass = enumMember.getDeclaringClass();
                String enumCanonicalName = enumClass.getCanonicalName();
                String memberName = enumMember.name();

                return "import static " + enumCanonicalName + "." + memberName + ";";
            }
            default -> {
                logger.trace("Parameter {} is not a Class or an enum member. ignoring", parameter);
                return null;
            }
        }
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
        modifiedScript.append("\n\n");
        modifiedScript.append(getDefaultPresetImportList());
        modifiedScript.append("\n\n");

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
        String returnedScript = modifiedScript.toString();
        logger.trace("Full script wrapped {}", returnedScript);
        return returnedScript;
    }

    public void setEnableHelper(Boolean enableHelper) {
        this.enableHelper = enableHelper;
    }
}
