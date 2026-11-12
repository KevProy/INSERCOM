package co.insercom.inventory;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Versioned, salted password storage. Caller owns (and should erase) input arrays. */
public final class Passwords {
    private static final int ITERATIONS = 210_000;
    private static final SecureRandom RANDOM = new SecureRandom();
    private Passwords() {}

    public static String hash(char[] password) {
        validate(password);
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] key = derive(password, salt, ITERATIONS);
        return "pbkdf2-sha256$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(key);
    }

    public static boolean verify(char[] password, String encoded) {
        if (password == null || encoded == null) return false;
        try {
            String[] parts = encoded.split("\\$", -1);
            if (parts.length != 4 || !parts[0].equals("pbkdf2-sha256")) return false;
            int iterations = Integer.parseInt(parts[1]);
            if (iterations < 100_000 || iterations > 2_000_000) return false;
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            if (salt.length < 16 || expected.length != 32) return false;
            return MessageDigest.isEqual(expected, derive(password, salt, iterations));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static void validate(char[] password) {
        if (password == null || password.length < 8 || password.length > 256)
            throw new IllegalArgumentException("La contraseña debe tener entre 8 y 256 caracteres.");
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("El entorno Java no dispone de PBKDF2WithHmacSHA256.", e);
        } finally {
            spec.clearPassword();
        }
    }
}
