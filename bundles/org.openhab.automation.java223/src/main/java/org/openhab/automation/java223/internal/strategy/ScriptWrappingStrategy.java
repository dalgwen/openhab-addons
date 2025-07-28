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

    private static final String BOILERPLATE_CODE_BEFORE = """
            import helper.generated.Java223Script;
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


            public class WrappedJavaScript extends Java223Script {
                public Object main() {
            """;

    private static final String BOILERPLATE_CODE_AFTER = """
                }
            }
            """;

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
