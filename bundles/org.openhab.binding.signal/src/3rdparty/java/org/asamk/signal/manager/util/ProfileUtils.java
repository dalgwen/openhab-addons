package org.asamk.signal.manager.util;

import org.asamk.signal.manager.api.Pair;
import org.asamk.signal.manager.api.PhoneNumberSharingMode;
import org.asamk.signal.manager.api.Profile;
import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.ecc.ECPublicKey;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.crypto.InvalidCiphertextException;
import org.whispersystems.signalservice.api.crypto.ProfileCipher;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.internal.push.PaymentAddress;

import java.io.IOException;
import java.util.Base64;
import java.util.HashSet;
import java.util.Optional;

public class ProfileUtils {

    private static final Logger logger = LoggerFactory.getLogger(ProfileUtils.class);

    public static Profile decryptProfile(final ProfileKey profileKey, final SignalServiceProfile encryptedProfile) {
        var profileCipher = new ProfileCipher(profileKey);
        IdentityKey identityKey = null;
        try {
            identityKey = new IdentityKey(Base64.getDecoder().decode(encryptedProfile.getIdentityKey()), 0);
        } catch (InvalidKeyException e) {
            logger.debug("Failed to decode identity key in profile, can't verify payment address", e);
        }

        try {
            var name = decryptString(encryptedProfile.getName(), profileCipher);
            var about = decryptString(encryptedProfile.getAbout(), profileCipher);
            var aboutEmoji = decryptString(encryptedProfile.getAboutEmoji(), profileCipher);

            final var nameParts = splitName(name);
            final var remotePhoneNumberSharing = decryptBoolean(encryptedProfile.getPhoneNumberSharing(),
                    profileCipher).map(v -> v ? PhoneNumberSharingMode.EVERYBODY : PhoneNumberSharingMode.NOBODY)
                    .orElse(null);
            return new Profile(System.currentTimeMillis(),
                    nameParts.first(),
                    nameParts.second(),
                    about,
                    aboutEmoji,
                    encryptedProfile.getAvatar(),
                    identityKey == null || encryptedProfile.getPaymentAddress() == null
                            ? null
                            : decryptAndVerifyMobileCoinAddress(encryptedProfile.getPaymentAddress(),
                                    profileCipher,
                                    identityKey.getPublicKey()),
                    getUnidentifiedAccessMode(encryptedProfile, profileCipher),
                    getCapabilities(encryptedProfile),
                    remotePhoneNumberSharing);
        } catch (InvalidCiphertextException e) {
            logger.debug("Failed to decrypt profile for {}", encryptedProfile.getServiceId(), e);
            return null;
        }
    }

    public static Profile.UnidentifiedAccessMode getUnidentifiedAccessMode(
            final SignalServiceProfile encryptedProfile,
            final ProfileCipher profileCipher
    ) {
        if (encryptedProfile.isUnrestrictedUnidentifiedAccess()) {
            return Profile.UnidentifiedAccessMode.UNRESTRICTED;
        }

        if (encryptedProfile.getUnidentifiedAccess() != null && profileCipher != null) {
            final var unidentifiedAccessVerifier = Base64.getDecoder().decode(encryptedProfile.getUnidentifiedAccess());
            if (profileCipher.verifyUnidentifiedAccess(unidentifiedAccessVerifier)) {
                return Profile.UnidentifiedAccessMode.ENABLED;
            }
        }

        return Profile.UnidentifiedAccessMode.DISABLED;
    }

    public static HashSet<Profile.Capability> getCapabilities(final SignalServiceProfile encryptedProfile) {
        final var capabilities = new HashSet<Profile.Capability>();
        if (encryptedProfile.getCapabilities().isStorage()) {
            capabilities.add(Profile.Capability.storage);
        }
        if (encryptedProfile.getCapabilities().isStorageServiceEncryptionV2()) {
            capabilities.add(Profile.Capability.storageServiceEncryptionV2Capability);
        }

        return capabilities;
    }

    private static String decryptString(
            final String encrypted,
            final ProfileCipher profileCipher
    ) throws InvalidCiphertextException {
        try {
            return encrypted == null ? null : profileCipher.decryptString(Base64.getDecoder().decode(encrypted));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Optional<Boolean> decryptBoolean(
            final String encrypted,
            final ProfileCipher profileCipher
    ) throws InvalidCiphertextException {
        try {
            return encrypted == null
                    ? Optional.empty()
                    : profileCipher.decryptBoolean(Base64.getDecoder().decode(encrypted));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static byte[] decryptAndVerifyMobileCoinAddress(
            final byte[] encryptedPaymentAddress,
            final ProfileCipher profileCipher,
            final ECPublicKey publicKey
    ) throws InvalidCiphertextException {
        byte[] decrypted;
        try {
            decrypted = profileCipher.decryptWithLength(encryptedPaymentAddress);
        } catch (IOException e) {
            logger.debug("Failed to decrypt payment address", e);
            return null;
        }

        PaymentAddress paymentAddress;
        try {
            paymentAddress = PaymentAddress.ADAPTER.decode(decrypted);
        } catch (IOException e) {
            logger.debug("Failed to parse payment address", e);
            return null;
        }

        return PaymentUtils.verifyPaymentsAddress(paymentAddress, publicKey);
    }

    private static Pair<String, String> splitName(String name) {
        if (name == null) {
            return new Pair<>(null, null);
        }
        String[] parts = name.split("\0");

        return switch (parts.length) {
            case 0 -> new Pair<>(null, null);
            case 1 -> new Pair<>(parts[0], null);
            default -> new Pair<>(parts[0], parts[1]);
        };
    }
}
