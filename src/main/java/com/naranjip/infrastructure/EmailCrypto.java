package com.naranjip.infrastructure;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class EmailCrypto {
    private final SecretKeySpec encryptionKey, lookupKey;
    private final SecureRandom random = new SecureRandom();
    public EmailCrypto(@Value("${app.email-encryption-key}") String encryption,
                       @Value("${app.email-lookup-key}") String lookup) {
        encryptionKey = new SecretKeySpec(key(encryption), "AES");
        lookupKey = new SecretKeySpec(key(lookup), "HmacSHA256");
        if (encryption.equals(lookup)) throw new IllegalStateException("Email encryption and lookup keys must be different");
    }
    private static byte[] key(String value) {
        try {
            byte[] bytes = Base64.getDecoder().decode(value);
            if (bytes.length != 32) throw new IllegalArgumentException();
            return bytes;
        } catch (IllegalArgumentException e) { throw new IllegalStateException("Email keys must be Base64-encoded 32-byte keys"); }
    }
    public String encrypt(String normalizedEmail) {
        try {
            byte[] nonce = new byte[12]; random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(128, nonce));
            cipher.updateAAD("naranjip:email:v1".getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(normalizedEmail.getBytes(StandardCharsets.UTF_8));
            return "v1:" + Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array());
        } catch (GeneralSecurityException e) { throw new IllegalStateException("Email encryption failed", e); }
    }
    public String lookup(String normalizedEmail) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256"); mac.init(lookupKey);
            return HexFormat.of().formatHex(mac.doFinal(normalizedEmail.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) { throw new IllegalStateException("Email lookup failed", e); }
    }
}
