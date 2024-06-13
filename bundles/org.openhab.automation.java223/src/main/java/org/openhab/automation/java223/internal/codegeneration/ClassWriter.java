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
package org.openhab.automation.java223.internal.codegeneration;

import static org.openhab.automation.java223.common.Java223Constants.LIB_DIR;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.automation.java223.common.Java223Constants;
import org.openhab.core.service.WatchService;
import org.openhab.core.service.WatchService.Kind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Write java file in lib directory.
 *
 * @author Gwendal Roulleau
 */
@NonNullByDefault
public class ClassWriter implements WatchService.WatchEventListener {

    public static final String HELPER_PACKAGE = "helper";

    private final Logger logger = LoggerFactory.getLogger(ClassGenerator.class);

    protected final Map<String, String> generatedClasses = new HashMap<>();

    private final Path folder;

    public ClassWriter(Path folder) throws IOException {
        this.folder = folder;

        String helperPackageFolder = HELPER_PACKAGE.replaceAll("\\.", "/");
        Files.createDirectories(folder.resolve(helperPackageFolder));
    }

    @Override
    public void processWatchEvent(Kind kind, Path path) {
        Path fullPath = LIB_DIR.resolve(path);
        if (fullPath.getFileName().toString().endsWith("." + Java223Constants.JAVA_FILE_TYPE)
                && (kind == Kind.DELETE || kind == Kind.MODIFY)) {
            // by intercepting delete or modify signal, we ensure that we remove java file from our internal database to
            // regenerate them thereafter
            String key = fullPath.toString().replace(File.separator, ".").substring(0, fullPath.toString().length());
            generatedClasses.remove(key);
        } else {
            logger.trace("Received '{}' for path '{}' - ignoring (wrong extension)", kind, fullPath);
        }
    }

    protected boolean replaceHelperFileIfNotEqual(String packageName, String className, String generatedClass)
            throws IOException {
        String key = packageName + "." + className;
        if (!generatedClass.equals(generatedClasses.put(key, generatedClass))) {
            String packageFolder = packageName.replaceAll("\\.", "/");
            Path javaFile = folder.resolve(packageFolder + "/" + className + "." + Java223Constants.JAVA_FILE_TYPE);

            Files.createDirectories(javaFile.getParent());
            try (FileOutputStream outFile = new FileOutputStream(javaFile.toFile())) {
                outFile.write(generatedClass.getBytes(StandardCharsets.UTF_8));
                logger.debug("Wrote generated class: {}", javaFile.toAbsolutePath());
            }
            return true;
        } else {
            logger.debug("{} has not changed.", key);
            return false;
        }
    }

    protected String getPackageName(String... packagePath) {
        return HELPER_PACKAGE + "." + String.join(".", packagePath);
    }
}
