/**
 * Copyright (c) 2010-2022 Contributors to the openHAB project
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
package org.openhab.binding.signal.internal.protocol.store;

import java.io.IOException;
import java.util.Locale;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.signal.internal.protocol.IncompleteRegistrationException;
import org.openhab.binding.signal.internal.protocol.RegistrationState;
import org.openhab.binding.signal.internal.protocol.RegistrationType;
import org.openhab.binding.signal.internal.protocol.StateListener;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.profiles.ProfileKey;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.push.exceptions.CaptchaRequiredException;
import org.whispersystems.signalservice.api.push.exceptions.NonNormalizedPhoneNumberException;
import org.whispersystems.signalservice.internal.ServiceResponse;
import org.whispersystems.signalservice.internal.push.RequestVerificationCodeResponse;
import org.whispersystems.signalservice.internal.push.VerifyAccountResponse;
import org.whispersystems.signalservice.internal.util.Util;

/**
 * A context storing data and parameter, for a dedicated account
 * Also manage registration with a verification code
 *
 * @author Gwendal ROULLEAU - Initial contribution
 *
 */
@NonNullByDefault
public class ContextDedicatedAccount extends Context {

    private final String phoneNumber;
    private final String captcha;
    private final String verificationCode;
    private final RegistrationType registrationType;

    public ContextDedicatedAccount(PersistentStorage persistentStorage, String phoneNumber, String captcha,
            String verificationCode, RegistrationType verificationCodeMethod) throws PersistenceException {
        super(persistentStorage);
        this.phoneNumber = phoneNumber;
        this.captcha = captcha;
        this.verificationCode = verificationCode;
        this.registrationType = verificationCodeMethod;
        loadStore();
    }

    public @Nullable String getCaptcha() {
        return captcha;
    }

    public String getVerificationCode() {
        return verificationCode;
    }

    public RegistrationType getRegistrationType() {
        return registrationType;
    }

    @Override
    public RegistrationState getRegistrationState() {
        if (!isPresent(phoneNumber)) {
            return RegistrationState.NO_USER;
        } else if (!isPresent(getVerificationCode())) {
            return RegistrationState.VERIFICATION_CODE_NEEDED;
        } else if (getAci() == null || !isPresent(getPassword())) {
            return RegistrationState.REGISTER_NEEDED;
        } else {
            return RegistrationState.REGISTERED;
        }
    }

    @Override
    public @Nullable String getE164() {
        return this.phoneNumber;
    }

    @Override
    public void verification() throws IOException, IncompleteRegistrationException {
        logger.info("Ask sending verification code to {} ...", getE164());

        String captcha = getCaptcha();
        captcha = (captcha == null || captcha.isBlank()) ? null : captcha; // nullify the captcha if empty
        if (captcha != null && captcha.length() > 0) {
            if (captcha.startsWith(SIGNAL_CAPTCHA_SCHEME)) {
                captcha = captcha.substring(SIGNAL_CAPTCHA_SCHEME.length());
            }
            logger.info("Using captcha token {}", captcha);
        }
        ServiceResponse<RequestVerificationCodeResponse> serviceResponse;
        if (getRegistrationType() == RegistrationType.PhoneCall) {
            serviceResponse = getOrCreateAccountManager().requestVoiceVerificationCode(Locale.getDefault(),
                    Optional.fromNullable(captcha), Optional.absent(), Optional.absent());
        } else {
            serviceResponse = getOrCreateAccountManager().requestSmsVerificationCode(false,
                    Optional.fromNullable(captcha), Optional.absent(), Optional.absent());
        }
        final Optional<Throwable> throwableOptional = serviceResponse.getExecutionError()
                .or(serviceResponse.getApplicationError());
        if (throwableOptional.isPresent()) {
            logger.error("Ask for verification code failed !");
            if (throwableOptional.get() instanceof CaptchaRequiredException
                    || throwableOptional.get() instanceof NonNormalizedPhoneNumberException) {
                throw new IncompleteRegistrationException(RegistrationState.CAPTCHA_NEEDED);
            } else if (throwableOptional.get() instanceof IOException) {
                throw ((IOException) throwableOptional.get());
            } else {
                throw new IOException(throwableOptional.get());
            }
        }
        logger.info("Verification code asked. Check your phone.");
    }

    @Override
    public void register(@Nullable StateListener connectionStateListener)
            throws InvalidInputException, IncompleteRegistrationException {
        logger.info("Verifying user {} with code {} ...", getE164(), getVerificationCode());

        String code = getVerificationCode().replace("-", "");

        byte[] profileKey = Util.getSecretBytes(32);
        byte[] unidentifiedAccessKey = UnidentifiedAccess.deriveAccessKeyFrom(new ProfileKey(profileKey));
        ServiceResponse<VerifyAccountResponse> response = getOrCreateAccountManager().verifyAccount(code,
                Integer.valueOf(getOrCreateRegistrationId()), true, unidentifiedAccessKey, false, null, false);
        if (response.getResult().isPresent()) {
            setACI(response.getResult().get().getUuid());
        } else {
            logger.error("Cannot verify user {} with code {}. Is code expired or incorrect ?", getE164(),
                    getVerificationCode());
            throw new IncompleteRegistrationException(RegistrationState.VERIFICATION_CODE_NEEDED,
                    "Cannot verify code ! Retry ?");
        }
    }

    @Override
    public String getId() {
        return this.phoneNumber;
    }
}
