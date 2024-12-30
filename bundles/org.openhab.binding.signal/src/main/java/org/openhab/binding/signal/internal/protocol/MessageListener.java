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
package org.openhab.binding.signal.internal.protocol;

import org.asamk.signal.manager.api.MessageEnvelope.Data.Reaction;
import org.asamk.signal.manager.api.RecipientAddress;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 *
 * @author Gwendal ROULLEAU - Initial contribution
 *
 */
@NonNullByDefault
public interface MessageListener {

    public void messageReceived(@Nullable RecipientAddress sender, String messageData);

    public void reactionReceived(@Nullable RecipientAddress sender, Reaction reaction);

    public void deliveryStatusReceived(DeliveryReport deliveryStatus);
}
