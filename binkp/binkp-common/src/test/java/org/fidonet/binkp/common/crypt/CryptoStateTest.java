package org.fidonet.binkp.common.crypt;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.Arrays;
import java.util.Random;

public class CryptoStateTest {
    @Test
    public void encryptAndDecryptWorkIncrementallyInEitherDirectionAndResetReproducesCiphertext() {
        byte[] plaintext = new byte[10000];
        new Random(17).nextBytes(plaintext);
        for (boolean client : new boolean[]{false, true}) {
            StandardEncrypt encrypt = new StandardEncrypt("secret".toCharArray(), client);
            StandardDecrypt decrypt = new StandardDecrypt("secret".toCharArray(), !client);
            byte[] ciphertext = plaintext.clone();
            assertEquals(ciphertext.length, encrypt.encryptData(ciphertext));
            assertFalse(Arrays.equals(plaintext, ciphertext));
            byte[] first = Arrays.copyOfRange(ciphertext, 0, 5000);
            byte[] second = Arrays.copyOfRange(ciphertext, 5000, ciphertext.length);
            assertEquals(5000, decrypt.decryptData(first));
            decrypt.decryptData(second);
            assertArrayEquals(Arrays.copyOfRange(plaintext, 0, 5000), first);
            assertArrayEquals(Arrays.copyOfRange(plaintext, 5000, plaintext.length), second);
            encrypt.reset();
            byte[] repeated = plaintext.clone();
            encrypt.encryptData(repeated);
            assertArrayEquals(ciphertext, repeated);
            decrypt.reset();
            decrypt.save();
            decrypt.decryptData(ciphertext.clone(), 7);
            decrypt.restore();
            decrypt.decryptData(repeated);
            assertArrayEquals(plaintext, repeated);
        }
        try {
            new StandardEncrypt("secret".toCharArray(), true).encryptData(null);
            fail("Null buffer accepted");
        } catch (NullPointerException expected) {
        }
    }
}
