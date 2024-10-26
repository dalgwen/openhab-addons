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
package org.openhab.automation.java223.internal;

import java.nio.file.Path;

import javax.script.ScriptException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.service.WatchService;
import org.openhab.core.service.WatchService.Kind;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import ch.obermuhlner.scriptengine.java.JavaCompiledScript;

/**
 * This class caches compiled scripts
 *
 * @author Gwendal Roulleau - Initial contribution
 */
@NonNullByDefault
public class Java223CompiledScriptCache implements WatchService.WatchEventListener {

    private int cacheSize;

    private Cache<String, Java223CompiledScriptInstanceWrapper> cache;

    public Java223CompiledScriptCache(int cacheSize) {
        super();
        this.cacheSize = cacheSize;
        cache = Caffeine.newBuilder().maximumSize(cacheSize).build();
    }

    /**
     * Recreate cache with a new size
     *
     * @param scriptCacheSize
     */
    public void setCacheSize(Integer newCacheSize) {
        if (!newCacheSize.equals(cacheSize)) {
            this.cacheSize = newCacheSize;
            cache = Caffeine.newBuilder().maximumSize(cacheSize).build();
        }
    }

    @SuppressWarnings({ "null", "unused" }) // yes it can be null, no there is no dead code
    public Java223CompiledScriptInstanceWrapper getOrCompile(String script, Compiler compiler) throws ScriptException {
        if (cacheSize <= 0) { // no cache
            // Create our wrapper directly
            return new Java223CompiledScriptInstanceWrapper(compiler.compile(script).getCompiledClass());
        }
        Java223CompiledScriptInstanceWrapper wrapper = cache.getIfPresent(script);
        if (wrapper == null) {
            wrapper = new Java223CompiledScriptInstanceWrapper(compiler.compile(script).getCompiledClass());
            cache.put(script, wrapper);
        }
        return wrapper;
    }

    public static interface Compiler {
        public JavaCompiledScript compile(String script) throws ScriptException;
    }

    /**
     * If a change is detected somewhere in the libraries,
     * then we invalidate all cache
     */
    @Override
    public void processWatchEvent(Kind kind, Path path) {
        cache.invalidateAll();
    }
}
