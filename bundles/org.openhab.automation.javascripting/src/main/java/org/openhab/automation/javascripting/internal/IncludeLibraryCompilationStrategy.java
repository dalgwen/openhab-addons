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
package org.openhab.automation.javascripting.internal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.script.ScriptException;
import javax.tools.JavaFileObject;

import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.obermuhlner.scriptengine.java.MemoryFileManager;
import ch.obermuhlner.scriptengine.java.compilation.CompilationStrategy;
import ch.obermuhlner.scriptengine.java.name.DefaultNameStrategy;
import ch.obermuhlner.scriptengine.java.name.NameStrategy;

/**
 * With this strategy, we compile the script alongside registered libraries
 *
 * @author Gwendal Roulleau - Initial contribution
 */
public class IncludeLibraryCompilationStrategy implements CompilationStrategy {

    private static Logger logger = LoggerFactory.getLogger(IncludeLibraryCompilationStrategy.class);

    Map<String, JavaFileObject> keepCompilingMap = new HashMap<>();

    NameStrategy nameStrategy = new DefaultNameStrategy();

    public void setLibraries(Collection<Path> librariesFile) throws IOException {
        keepCompilingMap = new HashMap<>();
        for (Path path : librariesFile) {
            try {
                JavaFileObject javaFileObject = getJavaFileObject(path);
                keepCompilingMap.put(javaFileObject.getName(), javaFileObject);
            } catch (ScriptException e) {
                logger.info("Cannot get the file {} as a valid java object. Cause: {}", path.toString(),
                        e.getMessage());
            }
        }
    }

    private JavaFileObject getJavaFileObject(Path path) throws IOException, ScriptException {
        String readString = Files.readString(path);
        String fullName = nameStrategy.getFullName(readString);
        String simpleClassName = NameStrategy.extractSimpleName(fullName);
        return MemoryFileManager.createSourceFileObject(null, simpleClassName, readString);
    }

    @Override
    public List<JavaFileObject> getJavaFileObjectsToCompile(@Nullable String simpleClassName,
            @Nullable String currentSource) {
        JavaFileObject currentJavaFileObject = MemoryFileManager.createSourceFileObject(null, simpleClassName,
                currentSource);
        List<JavaFileObject> sumFileObjects = new ArrayList<>(keepCompilingMap.values());
        sumFileObjects.add(currentJavaFileObject);
        return sumFileObjects;
    }

    @Override
    public void compilationResult(@Nullable Class<?> clazz) {
    }
}
