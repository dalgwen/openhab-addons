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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;

import javax.tools.JavaFileObject;

import org.openhab.automation.javascripting.annotations.Library;

import ch.obermuhlner.scriptengine.java.MemoryFileManager;
import ch.obermuhlner.scriptengine.java.compilation.CompilationStrategy;

/**
 * @author Gwendal Roulleau - Initial contribution
 */
public class IncludeLibraryCompilationStrategy implements CompilationStrategy {
    Map<String, JavaFileObject> previousFileObject = new HashMap<>();

    JavaFileObject currentJavaFileObject;

    @Override
    public List<JavaFileObject> getJavaFileObjectsToCompile(String simpleClassName, String currentSource) {
        currentJavaFileObject = MemoryFileManager.createSourceFileObject(null, simpleClassName, currentSource);
        Stream<JavaFileObject> previousFileObjects = previousFileObject.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(simpleClassName)) // do no keep the old file
                .map(Entry::getValue);
        return Stream.concat(previousFileObjects, Stream.of(currentJavaFileObject)).toList();
    }

    @Override
    public void compilationResult(Class<?> clazz) {
        JavaFileObject currentJavaFileObjectLocal = currentJavaFileObject;
        if (clazz.isAnnotationPresent(Library.class) && currentJavaFileObjectLocal != null) {
            previousFileObject.put(clazz.getSimpleName(), currentJavaFileObjectLocal);
        }
    }
}
