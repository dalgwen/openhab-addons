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
import java.util.List;

import javax.script.ScriptException;
import javax.tools.JavaFileObject;

import ch.obermuhlner.scriptengine.java.MemoryFileManager;
import ch.obermuhlner.scriptengine.java.compilation.CompilationStrategy;
import ch.obermuhlner.scriptengine.java.name.DefaultNameStrategy;
import ch.obermuhlner.scriptengine.java.name.NameStrategy;

/**
 * With this strategy, we compile the script alongside previously registered libraries
 *
 * @author Gwendal Roulleau - Initial contribution
 */
public class IncludeLibraryCompilationStrategy implements CompilationStrategy {
    Collection<JavaFileObject> keepCompilingMap;

    NameStrategy nameStrategy = new DefaultNameStrategy();

    public void setLibraries(Collection<Path> librariesFile) throws IOException, ScriptException {
        keepCompilingMap = new ArrayList<>();
        for (Path path : librariesFile) {
            keepCompilingMap.add(getJavaFileObject(path));
        }
    }

    private JavaFileObject getJavaFileObject(Path path) throws IOException, ScriptException {
        String readString = Files.readString(path);
        String fullName = nameStrategy.getFullName(readString);
        String simpleClassName = NameStrategy.extractSimpleName(fullName);
        return MemoryFileManager.createSourceFileObject(null, simpleClassName, readString);
    }

    @Override
    public List<JavaFileObject> getJavaFileObjectsToCompile(String simpleClassName, String currentSource) {
        JavaFileObject currentJavaFileObject = MemoryFileManager.createSourceFileObject(null, simpleClassName,
                currentSource);
        List<JavaFileObject> sumFileObjects = new ArrayList<>(keepCompilingMap);
        sumFileObjects.add(currentJavaFileObject);
        return sumFileObjects;
    }

    @Override
    public void compilationResult(Class<?> clazz) {
    }
}
