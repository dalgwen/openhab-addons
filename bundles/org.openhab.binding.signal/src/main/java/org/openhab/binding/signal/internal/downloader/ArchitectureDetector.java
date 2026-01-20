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
package org.openhab.binding.signal.internal.downloader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detect architecture for the signal native client library download
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
public class ArchitectureDetector {
    private static final Logger logger = LoggerFactory.getLogger(ArchitectureDetector.class);

    public enum Architecture {
        LINUX_AARCH64("aarch64-unknown-linux-gnu"),
        LINUX_X86_64("x86_64-unknown-linux-gnu"),
        LINUX_MUSL_X86_64("x86_64-unknown-linux-musl"),
        MACOS_AARCH64("aarch64-apple-darwin"),
        MACOS_X86_64("x86_64-apple-darwin"),
        WINDOWS_X86_64("x86_64-pc-windows"),
        UNKNOWN("NONE");

        public final String downloadDiscriminant;

        Architecture(String downloadDiscriminant) {
            this.downloadDiscriminant = downloadDiscriminant;
        }
    }

    private static String getSystemPropertySafe(String property) {
        return System.getProperty(property, "unknown");
    }

    private static String getEnvironmentVariableSafe(@SuppressWarnings("SameParameterValue") String variable) {
        String value = System.getenv(variable);
        return value != null ? value : "unknown";
    }

    public static Architecture detect() {
        String os = getSystemPropertySafe("os.name").toLowerCase();
        String arch = getSystemPropertySafe("os.arch").toLowerCase();
        logger.debug("Detecting architecture for OS: {}, arch: {}", os, arch);

        Architecture detected;
        if (os.contains("win")) {
            if (arch.contains("amd64") || arch.contains("x86_64")) {
                detected = Architecture.WINDOWS_X86_64;
            } else {
                detected = Architecture.UNKNOWN;
            }
        } else if (os.contains("mac")) {
            if (arch.contains("aarch64")) {
                detected = Architecture.MACOS_AARCH64;
            } else if (arch.contains("amd64") || arch.contains("x86_64")) {
                detected = Architecture.MACOS_X86_64;
            } else {
                detected = Architecture.UNKNOWN;
            }
        } else if (os.contains("linux")) {
            if (arch.contains("aarch64")) {
                detected = Architecture.LINUX_AARCH64;
            } else if (arch.contains("amd64") || arch.contains("x86_64")) {
                String libc = getEnvironmentVariableSafe("org.osgi.framework.os.libc").toLowerCase();
                if (libc.contains("musl")) {
                    detected = Architecture.LINUX_MUSL_X86_64;
                } else {
                    detected = Architecture.LINUX_X86_64;
                }
            } else {
                detected = Architecture.UNKNOWN;
            }
        } else {
            detected = Architecture.UNKNOWN;
        }

        logger.debug("Detected architecture: {}", detected);
        return detected;
    }
}
