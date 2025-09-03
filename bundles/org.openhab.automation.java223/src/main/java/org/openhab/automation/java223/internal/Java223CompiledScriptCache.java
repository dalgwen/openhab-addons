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
package org.openhab.automation.java223.internal;

import java.nio.file.Path;
import java.util.Map;

import javax.script.ScriptException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.service.WatchService.Kind;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * This class caches compiled scripts
 *
 * @author Gwendal Roulleau - Initial contribution
 */
@NonNullByDefault
public class Java223CompiledScriptCache {

    private int cacheSize;

    private Cache<String, Java223CompiledScript> cache;

    public Java223CompiledScriptCache(int cacheSize) {
        super();
        this.cacheSize = cacheSize;
        cache = Caffeine.newBuilder().maximumSize(cacheSize).build();
    }

    /**
     * Recreate cache with a new size
     *
     * @param newCacheSize New cache size
     */
    public void setCacheSize(Integer newCacheSize) {
        if (!newCacheSize.equals(cacheSize)) {
            this.cacheSize = newCacheSize;
            cache = Caffeine.newBuilder().maximumSize(cacheSize).build();
        }
    }

    public Java223CompiledScript getOrCompile(String script, Compiler compiler) throws ScriptException {
        Java223CompiledScript wrapper = null;
        if (cacheSize > 0) {
            wrapper = cache.getIfPresent(script);
        }
        if (wrapper == null) {
            wrapper = compiler.compile(script);
            if (cacheSize > 0) {
                cache.put(script, wrapper);
            }
        }
        return wrapper;
    }

    public interface Compiler {
        Java223CompiledScript compile(String script) throws ScriptException;
    }

    /**
     * If a change is detected somewhere in the libraries,
     * then we invalidate all cache
     */
    public void processWatchEvent(Kind kind, Path path) {
        for (Map.Entry<String, Java223CompiledScript> entry : cache.asMap().entrySet()) {
            entry.getValue().invalidate(entry.getKey());
        }
    }
}
