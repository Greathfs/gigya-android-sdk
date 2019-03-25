package com.gigya.android.sdk.biometric;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.support.annotation.NonNull;
import android.util.Base64;

import com.gigya.android.sdk.GigyaLogger;
import com.gigya.android.sdk.model.account.SessionInfo;
import com.gigya.android.sdk.services.PersistenceService;
import com.gigya.android.sdk.services.SessionService;
import com.gigya.android.sdk.utils.CipherUtils;
import com.gigya.android.sdk.utils.ObjectUtils;

import org.json.JSONObject;

import java.security.KeyStore;
import java.util.Enumeration;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;


public abstract class GigyaBiometricImpl implements IGigyaBiometricActions {

    private static final String LOG_TAG = "GigyaBiometricImpl";

    private static final String FINGERPRINT_KEY_NAME = "fingerprint";

    final private SessionService _sessionService;

    public GigyaBiometricImpl(SessionService sessionService) {
        // Reference session service.
        _sessionService = sessionService;
    }

    boolean okayToOptInOut() {
        return (_sessionService != null && _sessionService.isValidSession());
    }

    boolean isOptIn() {
        final String encryptionType = _sessionService.getPersistenceService().getSessionEncryption();
        final boolean optIn = ObjectUtils.safeEquals(encryptionType, SessionService.FINGERPRINT);
        GigyaLogger.debug(LOG_TAG, "isOptIn : " + String.valueOf(optIn));
        return optIn;
    }

    boolean isLocked() {
        return !_sessionService.isValidSession() && isOptIn();
    }

    /**
     * Perform relevant success logic on successful biometric authentication.
     *
     * @param action            Desired action.
     * @param biometricCallback Status callback.
     */
    synchronized protected void onSuccessfulAuthentication(final GigyaBiometric.Action action, @NonNull final IGigyaBiometricCallback biometricCallback) {
        switch (action) {
            case OPT_IN:
                optIn(biometricCallback);
                break;
            case OPT_OUT:
                optOut(biometricCallback);
                break;
            case LOCK:
                lock(biometricCallback);
                break;
            case UNLOCK:
                unlock(biometricCallback);
                break;
            default:
                break;
        }
    }

    /**
     * Opt-In operation.
     * Encrypt the current persistent session with the fingerprint key.
     *
     * @param biometricCallback Status callback.
     */
    private void optIn(@NonNull final IGigyaBiometricCallback biometricCallback) {
        final PersistenceService persistenceService = _sessionService.getPersistenceService();
        final String encryptionType = persistenceService.getSessionEncryption();
        if (encryptionType.equals(SessionService.FINGERPRINT)) {
            GigyaLogger.error(LOG_TAG, "Fingerprint already opt-in");
            return;
        }
        final SessionInfo sessionInfo = _sessionService.getSession();
        if (sessionInfo == null) {
            GigyaLogger.error(LOG_TAG, "Session is null Opt-In failed");
            return;
        }
        final JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("sessionToken", sessionInfo.getSessionToken());
            jsonObject.put("sessionSecret", sessionInfo.getSessionSecret());
            jsonObject.put("expirationTime", sessionInfo.getExpirationTime());
            jsonObject.put("ucid", _sessionService.getConfig().getUcid());
            jsonObject.put("gmid", _sessionService.getConfig().getGmid());
            final String plain = jsonObject.toString();

            // Encrypt.
            final byte[] encryptedBytes = _cipher.doFinal(CipherUtils.toBytes(plain.toCharArray()));
            final byte[] ivBytes = _cipher.getParameters().getParameterSpec(IvParameterSpec.class).getIV();

            // Update preferences.
            persistenceService.setSession(Base64.encodeToString(encryptedBytes, Base64.DEFAULT));
            persistenceService.updateIVSpec(Base64.encodeToString(ivBytes, Base64.DEFAULT));
            persistenceService.updateSessionEncryption(SessionService.FINGERPRINT);

            // Callback.
            biometricCallback.onBiometricOperationSuccess(GigyaBiometric.Action.OPT_IN);
        } catch (Exception ex) {
            ex.printStackTrace();
            // Callback.
            biometricCallback.onBiometricOperationFailed("Fingerprint optIn: " + ex.getMessage());
        }
    }

    /**
     * Opt-Out operation.
     * Set current heap session. Save as default encryption.
     *
     * @param biometricCallback Status callback.
     */
    private void optOut(IGigyaBiometricCallback biometricCallback) {
        final PersistenceService persistenceService = _sessionService.getPersistenceService();
        final String encryptionType = persistenceService.getSessionEncryption();
        if (encryptionType.equals(SessionService.DEFAULT)) {
            GigyaLogger.error(LOG_TAG, "Fingerprint already opt-out");
            return;
        }
        final SessionInfo sessionInfo = _sessionService.getSession();
        // Set the session (heap/save).
        _sessionService.setSession(sessionInfo);
        // Reset session encryption to DEFAULT.
        _sessionService.getPersistenceService().updateSessionEncryption(SessionService.DEFAULT);
        // Delete KeyStore.
        deleteKey();
        // Callback.
        biometricCallback.onBiometricOperationSuccess(GigyaBiometric.Action.OPT_OUT);
    }

    /**
     * Lock operation.
     * Clear current heap session.
     *
     * @param biometricCallback Status callback.
     */
    private void lock(IGigyaBiometricCallback biometricCallback) {
        // Locking means clearing the SessionInfo from the heap but keeping the persistence.
        _sessionService.clear(false);
        // Callback.
        biometricCallback.onBiometricOperationSuccess(GigyaBiometric.Action.LOCK);
    }

    /**
     * Unlock operation.
     * Decrypt fingerprint session and save as default.
     *
     * @param biometricCallback Status callback.
     */
    private void unlock(IGigyaBiometricCallback biometricCallback) {
        final PersistenceService persistenceService = _sessionService.getPersistenceService();
        final String encryptedSession = persistenceService.getSession();
        // Decrypt the session.
        try {
            // Decrypt & set the session.
            byte[] encPLBytes = Base64.decode(encryptedSession, Base64.DEFAULT);
            byte[] bytePlainText = _cipher.doFinal(encPLBytes);
            final String plain = new String(CipherUtils.toChars(bytePlainText));

            // Set session.
            JSONObject jsonObject = new JSONObject(plain);
            final String sessionToken = jsonObject.has("sessionToken") ? jsonObject.getString("sessionToken") : null;
            final String sessionSecret = jsonObject.has("sessionSecret") ? jsonObject.getString("sessionSecret") : null;
            final long expirationTime = jsonObject.has("expirationTime") ? jsonObject.getLong("expirationTime") : -1;
            final SessionInfo sessionInfo = new SessionInfo(sessionSecret, sessionToken, expirationTime);
            final String ucid = jsonObject.getString("ucid");
            _sessionService.getConfig().setUcid(ucid);
            final String gmid = jsonObject.getString("gmid");
            _sessionService.getConfig().setGmid(gmid);

            // Set the session (DEFAULT encryption).
            _sessionService.setSession(sessionInfo);
            _sessionService.updateSessionExpirationIfExists();
            // Callback.
            biometricCallback.onBiometricOperationSuccess(GigyaBiometric.Action.UNLOCK);
        } catch (Exception ex) {
            ex.printStackTrace();
            biometricCallback.onBiometricOperationFailed("Fingerprint unlock: " + ex.getMessage());
        }
    }

    //region KEYSTORE

    private KeyStore _keyStore;
    protected Cipher _cipher;
    protected SecretKey _secretKey;

    private void deleteKey() {
        try {
            _keyStore.deleteEntry(FINGERPRINT_KEY_NAME);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Get or generate new SecretKey.
     */
    protected void getKey() {
        try {
            _keyStore = KeyStore.getInstance("AndroidKeyStore");
            _keyStore.load(null);

            final Enumeration<String> aliases = _keyStore.aliases();
            while (aliases.hasMoreElements()) {
                if (FINGERPRINT_KEY_NAME.equals(aliases.nextElement())) {
                    try {
                        _secretKey = (SecretKey) _keyStore.getKey(FINGERPRINT_KEY_NAME, null);
                        return;
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        deleteKey();
                    }
                }
            }
            final KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            keyGenerator.init(new
                    KeyGenParameterSpec.Builder(FINGERPRINT_KEY_NAME,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .setUserAuthenticationRequired(true)
                    .setKeySize(256)
                    .build());
            _secretKey = keyGenerator.generateKey();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Create cipher instance according to desired encryption mode.
     *
     * @param encryptionMode Cipher encryption mode (Cipher.ENCRYPT_MODE/Cipher.DECRYPT_MODE).
     */
    protected void createCipherFor(final int encryptionMode) {
        try {
            _cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
            final PersistenceService persistenceService = _sessionService.getPersistenceService();
            if (encryptionMode == Cipher.ENCRYPT_MODE) {
                _cipher.init(encryptionMode, _secretKey);
            } else if (encryptionMode == Cipher.DECRYPT_MODE) {
                final String ivSpec = persistenceService.getIVSpec();
                if (ivSpec != null) {
                    final IvParameterSpec spec = new IvParameterSpec(Base64.decode(ivSpec, Base64.DEFAULT));
                    _cipher.init(Cipher.DECRYPT_MODE, _secretKey, spec);
                } else {
                    GigyaLogger.error(LOG_TAG, "createCipherFor: getIVSpec null");
                    _cipher = null;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            _cipher = null;
        }
    }

    //endregion
}