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
package org.openhab.automation.javascripting.scriptsupport;

import org.slf4j.LoggerFactory;

/**
 * Delegate wrapper for log around slf4j.
 * Exist solely for script class and related project to not have to import slf4j.
 *
 * @author Gwendal Roulleau - Initial contribution
 */
public class Logger {

    public org.slf4j.Logger delegate;

    public Logger(org.slf4j.Logger delegate) {
        this.delegate = delegate;
    }

    public static Logger getLogger(Class<?> clazz) {
        return new Logger(LoggerFactory.getLogger(clazz));
    }

    public static Logger getLogger(String name) {
        return new Logger(LoggerFactory.getLogger(name));
    }

    public String getName() {
        return delegate.getName();
    }

    public boolean isTraceEnabled() {
        return delegate.isTraceEnabled();
    }

    public void trace(String msg) {
        delegate.trace(msg);
    }

    public void trace(String format, Object arg) {
        delegate.trace(format, arg);
    }

    public void trace(String format, Object arg1, Object arg2) {
        delegate.trace(format, arg1, arg2);
    }

    public void trace(String format, Object... arguments) {
        delegate.trace(format, arguments);
    }

    public void trace(String msg, Throwable t) {
        delegate.trace(msg, t);
    }

    public boolean isDebugEnabled() {
        return delegate.isDebugEnabled();
    }

    public void debug(String msg) {
        delegate.debug(msg);

    }

    public void debug(String format, Object arg) {
        delegate.debug(format, arg);
    }

    public void debug(String format, Object arg1, Object arg2) {
        delegate.debug(format, arg1, arg2);

    }

    public void debug(String format, Object... arguments) {
        delegate.debug(format, arguments);
    }

    public void debug(String msg, Throwable t) {
        delegate.debug(msg, t);
    }

    public boolean isInfoEnabled() {
        return delegate.isInfoEnabled();
    }

    public void info(String msg) {
        delegate.info(msg);
    }

    public void info(String format, Object arg) {
        delegate.info(format, arg);
    }

    public void info(String format, Object arg1, Object arg2) {
        delegate.info(format, arg1, arg2);
    }

    public void info(String format, Object... arguments) {
        delegate.info(format, arguments);

    }

    public void info(String msg, Throwable t) {
        delegate.info(msg, t);
    }

    public boolean isWarnEnabled() {
        return delegate.isWarnEnabled();
    }

    public void warn(String msg) {
        delegate.warn(msg);
    }

    public void warn(String format, Object arg) {
        delegate.warn(format, arg);

    }

    public void warn(String format, Object... arguments) {
        delegate.warn(format, arguments);
    }

    public void warn(String format, Object arg1, Object arg2) {
        delegate.warn(format, arg1, arg2);
    }

    public void warn(String msg, Throwable t) {
        delegate.warn(msg, t);
    }

    public boolean isErrorEnabled() {
        return delegate.isErrorEnabled();
    }

    public void error(String msg) {
        delegate.error(msg);
    }

    public void error(String format, Object arg) {
        delegate.error(format, arg);
    }

    public void error(String format, Object arg1, Object arg2) {
        delegate.error(format, arg1, arg2);
    }

    public void error(String format, Object... arguments) {
        delegate.error(format, arguments);
    }

    public void error(String msg, Throwable t) {
        delegate.error(msg, t);
    }
}
