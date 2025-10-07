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
package org.openhab.automation.java223.internal.codegeneration;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.audio.AudioManager;
import org.openhab.core.automation.RuleRegistry;
import org.openhab.core.automation.module.script.ScriptExtensionAccessor;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.thing.ThingRegistry;
import org.openhab.core.thing.binding.ThingActions;
import org.openhab.core.voice.VoiceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generate code for imports and default injected field member.
 *
 * @author Gwendal Roulleau - Initial contribution
 */
@NonNullByDefault
public class InjectedCodeGenerator {

    private final Logger logger = LoggerFactory.getLogger(InjectedCodeGenerator.class);

    private final String imports;
    private final String injectedField;

    // We cannot easily find what types to write for those bindings, so we define some exceptions here:
    private static final Map<String, ImportAndDeclaration> OVERWRITE_BINDINGS_TYPE = Map.ofEntries(
            getEntry("audio", AudioManager.class), getEntry("voice", VoiceManager.class),
            getEntry("rules", RuleRegistry.class), getEntry("things", ThingRegistry.class),
            getEntry("itemRegistry", ItemRegistry.class), getEntry("ir", ItemRegistry.class),
            new AbstractMap.SimpleEntry<>("items", new ImportAndDeclaration("import java.util.Map;",
                    "protected @InjectBinding Map<String, State> items;")));

    public InjectedCodeGenerator(ScriptExtensionAccessor scriptExtensionAccessor) {
        Map<String, Object> defaultPresets = scriptExtensionAccessor.findDefaultPresets("");
        List<@Nullable ImportAndDeclaration> importAndDeclarations = parseBindings(defaultPresets);
        this.imports = importAndDeclarations.stream().filter(Objects::nonNull).map(ImportAndDeclaration::importLine)
                .distinct().sorted().collect(Collectors.joining("\n"));
        this.injectedField = importAndDeclarations.stream().filter(Objects::nonNull)
                .map(ImportAndDeclaration::declaration).filter(Objects::nonNull).filter(Predicate.not(String::isEmpty))
                .distinct().map("    "::concat).sorted().collect(Collectors.joining("\n"));
    }

    private static Map.Entry<String, ImportAndDeclaration> getEntry(String key, Class<?> clazz) {
        return new AbstractMap.SimpleEntry<>(key, getImportAndDeclaration(key, clazz));
    }

    private static ImportAndDeclaration getImportAndDeclaration(String key, Class<?> clazz) {
        return new ImportAndDeclaration("import " + clazz.getCanonicalName() + ";",
                "protected @InjectBinding " + clazz.getSimpleName() + " " + key + ";");
    }

    public String getDefaultPresetImportList() {
        return imports;
    }

    public String getInjectedFieldsDeclaration() {
        return injectedField;
    }

    private List<@Nullable ImportAndDeclaration> parseBindings(Map<String, Object> defaultPresets) {
        // create a list of imports for each binding found
        return defaultPresets.entrySet().stream().map(this::generateImportAndDeclaration).distinct().toList();
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
    private ImportAndDeclaration generateImportAndDeclaration(Map.Entry<String, Object> parameter) {
        String key = parameter.getKey();
        Object value = parameter.getValue();

        switch (value) {
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
                    return new ImportAndDeclaration("import " + canonicalName + ";", null);
                } else {
                    // Class is in the default package, no import statement needed.
                    return null;
                }
            }
            case Enum<?> enumMember -> {
                Class<?> enumClass = enumMember.getDeclaringClass();
                String enumCanonicalName = enumClass.getCanonicalName();
                String memberName = enumMember.name();

                return new ImportAndDeclaration("import static " + enumCanonicalName + "." + memberName + ";", null);
            }
            default -> {
                ImportAndDeclaration importAndDeclaration = OVERWRITE_BINDINGS_TYPE.get(key);
                if (importAndDeclaration != null) {
                    return importAndDeclaration;
                }
                Class<?>[] interfaces = value.getClass().getInterfaces();
                if (interfaces.length == 0) { // directly import class name
                    return getImportAndDeclaration(key, value.getClass());
                } else if (interfaces.length == 1) {
                    return getImportAndDeclaration(key, interfaces[0]);
                } else {
                    logger.warn(
                            "Cannot found an appropriate class for declaring the injected field {} : {}. We pick the first one",
                            key, value.getClass());
                    return getImportAndDeclaration(key, interfaces[0]);
                }
            }
        }
    }

    public record ImportAndDeclaration(String importLine, @Nullable String declaration) {
    }
}
