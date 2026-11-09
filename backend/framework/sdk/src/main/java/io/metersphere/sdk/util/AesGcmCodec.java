package io.metersphere.sdk.util;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/** Shared algorithm only. Key derivation, AAD and secret references belong to each caller. */
public final class AesGcmCodec {
    private static final SecureRandom RANDOM = new SecureRandom();
    private AesGcmCodec() { }

    public static String seal(SecretKey key, byte[] aad, byte[] plaintext) throws GeneralSecurityException {
        byte[] iv = new byte[12];
        RANDOM.nextBytes(iv);
        Cipher cipher = cipher(Cipher.ENCRYPT_MODE, key, aad, iv);
        byte[] encrypted = cipher.doFinal(plaintext);
        byte[] result = Arrays.copyOf(iv, iv.length + encrypted.length);
        System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
        return Base64.getEncoder().encodeToString(result);
    }

    public static byte[] open(SecretKey key, byte[] aad, String ciphertext) throws GeneralSecurityException {
        byte[] source = Base64.getDecoder().decode(ciphertext);
        if (source.length < 28) throw new GeneralSecurityException("Invalid encrypted value");
        return cipher(Cipher.DECRYPT_MODE, key, aad, Arrays.copyOf(source, 12))
                .doFinal(Arrays.copyOfRange(source, 12, source.length));
    }

    private static Cipher cipher(int mode, SecretKey key, byte[] aad, byte[] iv) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(128, iv));
        if (aad != null) cipher.updateAAD(aad);
        return cipher;
    }
}
