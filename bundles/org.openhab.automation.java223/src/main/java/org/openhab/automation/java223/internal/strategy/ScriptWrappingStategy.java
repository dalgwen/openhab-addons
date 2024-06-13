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
package org.openhab.automation.java223.internal.strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

import ch.obermuhlner.scriptengine.java.compilation.ScriptInterceptorStrategy;

/**
 * Wrapps a script in boilerplate code if not present.
 * Must respect some conditions to be wrapped correctly:
 * - must not contains "public class"
 * - line containing import must start with "import " (on their own line)
 * - you can globally return a value, but take care to put the "return " keyword at the beginning of the line (on its
 * own line)
 * - you cannot declare method (in fact, your script is already wrapped inside a method)
 *
 * @author Gwendal Roulleau - Initial contribution
 */
@NonNullByDefault
public class ScriptWrappingStategy implements ScriptInterceptorStrategy {

    private static final Pattern NAME_PATTERN = Pattern.compile("public\\s+class\\s+.*");
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("package\\s+[A-Za-z][A-Za-z0-9_$.]*;\\s*");
    private static final Pattern IMPORT_PATTERN = Pattern.compile("import\\s+[A-Za-z][A-Za-z0-9_$.]*;\\s*");

    private static String BOILERPLATE_CODE_BEFORE = """
            import helper.Java223Script;
            public class WrappedJavaScript extends Java223Script {
                public Object main() {
            """;

    private static String BOILERPLATE_CODE_AFTER = """
                }
            }
            """;

    @Override
    public @Nullable String intercept(@Nullable String script) {
        if (script == null) {
            return "";
        }
        List<@NonNull String> lines = script.lines().toList();

        String packageDeclarationLine = "";
        List<String> importLines = new ArrayList<>(lines.size());
        List<String> scriptLines = new ArrayList<>(lines.size());
        boolean returnIsPresent = false;

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

        StringBuilder modifiedScript = new StringBuilder();
        modifiedScript.append(packageDeclarationLine + "\n");
        modifiedScript.append(String.join("\n", importLines));
        modifiedScript.append("\n");
        modifiedScript.append(BOILERPLATE_CODE_BEFORE);
        modifiedScript.append(String.join("\n", scriptLines));
        modifiedScript.append("\n");
        if (!returnIsPresent) {
            modifiedScript.append("return null;");
        }
        modifiedScript.append(BOILERPLATE_CODE_AFTER);

        return modifiedScript.toString();
    }
}
