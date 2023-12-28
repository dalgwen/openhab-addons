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
package org.openhab.automation.javascripting.internal.codegeneration;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

import org.apache.velocity.exception.ResourceNotFoundException;
import org.apache.velocity.runtime.resource.Resource;
import org.apache.velocity.runtime.resource.loader.ResourceLoader;
import org.apache.velocity.util.ExtProperties;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Load code generation template from classpath
 *
 * @author Gwendal Roulleau - Initial contribution
 */
@NonNullByDefault
public class VelocityResourceLoader extends ResourceLoader {

    @Override
    public void init(@Nullable ExtProperties configuration) {
    }

    @Override
    public @Nullable Reader getResourceReader(@Nullable String name, @Nullable String encoding)
            throws ResourceNotFoundException {
        Reader result = null;
        InputStream rawStream = null;
        if (name != null) {
            rawStream = this.getClass().getResourceAsStream(name);
            if (rawStream != null) {
                try {
                    result = buildReader(rawStream, encoding);
                } catch (IOException e) {
                    String msg = "ClasspathResourceLoader Error: cannot find resource " + name;
                    throw new ResourceNotFoundException(msg, null, rsvc.getLogContext().getStackTrace());
                }
            }
        }
        return result;
    }

    @Override
    public boolean isSourceModified(@Nullable Resource resource) {
        return false;
    }

    @Override
    public long getLastModified(@Nullable Resource resource) {
        return 0;
    }
}
