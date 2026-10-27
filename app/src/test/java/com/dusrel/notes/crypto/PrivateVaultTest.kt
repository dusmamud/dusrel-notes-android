package com.dusrel.notes.crypto

import com.dusrel.notes.domain.crypto.PrivateVault
import com.dusrel.notes.domain.crypto.VaultAuthenticationException
import com.dusrel.notes.domain.crypto.VaultException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PrivateVaultTest {

    private val pin = "4829"
    private val salt = PrivateVault.generateSalt()
    private val key = PrivateVault.deriveKey(pin, salt)

    @Test fun roundTripTitleAndContentSeparately() {
        val encrypted = PrivateVault.encryptNote("Secret title", "Secret body text", key)
        val plain = PrivateVault.decryptNote(encrypted, key)
        assertEquals("Secret title", plain.title)
        assertEquals("Secret body text", plain.content)
    }

    @Test fun roundTripSingleField() {
        val encoded = PrivateVault.encrypt("hello private world", key)
        assertEquals("hello private world", PrivateVault.decrypt(encoded, key))
    }

    @Test fun roundTripEmptyAndUnicode() {
        assertEquals("", PrivateVault.decrypt(PrivateVault.encrypt("", key), key))
        val unicode = "Assamese: বিজনী আমাৰ প্ৰাণ — emoji 🔒"
        assertEquals(unicode, PrivateVault.decrypt(PrivateVault.encrypt(unicode, key), key))
    }

    @Test fun wrongPinFailsViaGcmTag() {
        val encoded = PrivateVault.encrypt("do not read this", key)
        val wrongKey = PrivateVault.deriveKey("0000", salt)
        try {
            PrivateVault.decrypt(encoded, wrongKey)
            fail("Expected VaultAuthenticationException for wrong PIN")
        } catch (expected: VaultAuthenticationException) {
            // Wrong PIN -> GCM tag mismatch, not a hash comparison.
        }
        assertNull(PrivateVault.tryDecrypt(encoded, wrongKey))
        assertFalse(PrivateVault.verifyPin(wrongKey, PrivateVault.createVerifier(key)))
    }

    @Test fun wrongPinFailsEvenWithRightSaltAndWrongVerifier() {
        // The verifier is the PIN check: right PIN verifies, wrong PIN does not.
        val verifier = PrivateVault.createVerifier(key)
        assertTrue(PrivateVault.verifyPin(key, verifier))
        assertFalse(PrivateVault.verifyPin(PrivateVault.deriveKey("1234", salt), verifier))
    }

    @Test fun tamperedCiphertextFails() {
        val encoded = PrivateVault.encrypt("tamper target", key)
        val decoded = java.util.Base64.getDecoder().decode(encoded)
        // Flip a byte in the ciphertext region (past the 12-byte IV).
        decoded[decoded.size - 1] = (decoded[decoded.size - 1].toInt() xor 0x01).toByte()
        val tampered = java.util.Base64.getEncoder().encodeToString(decoded)
        try {
            PrivateVault.decrypt(tampered, key)
            fail("Expected VaultAuthenticationException for tampered ciphertext")
        } catch (expected: VaultAuthenticationException) {
            // Tampering breaks the GCM tag.
        }
    }

    @Test fun tamperedIvFails() {
        val encoded = PrivateVault.encrypt("iv target", key)
        val decoded = java.util.Base64.getDecoder().decode(encoded)
        decoded[0] = (decoded[0].toInt() xor 0x01).toByte()
        val tampered = java.util.Base64.getEncoder().encodeToString(decoded)
        try {
            PrivateVault.decrypt(tampered, key)
            fail("Expected failure for tampered IV")
        } catch (expected: VaultException) {
            // IV tamper also breaks GCM authentication.
        }
    }

    @Test fun malformedInputFailsCleanly() {
        try {
            PrivateVault.decrypt("not-base64!!!", key)
            fail("Expected VaultException for malformed Base64")
        } catch (expected: VaultException) {
        }
        try {
            PrivateVault.decrypt(java.util.Base64.getEncoder().encodeToString(ByteArray(5)), key)
            fail("Expected failure for input too short for an IV")
        } catch (expected: RuntimeException) {
        }
    }

    @Test fun samePlaintextEncryptsDifferently() {
        val first = PrivateVault.encrypt("same text", key)
        val second = PrivateVault.encrypt("same text", key)
        assertNotEquals("Random IV must make each encryption unique", first, second)
        // ...yet both decrypt to the same plaintext.
        assertEquals("same text", PrivateVault.decrypt(first, key))
        assertEquals("same text", PrivateVault.decrypt(second, key))
        // Same for the two fields of one note: distinct IVs per field.
        val note = PrivateVault.encryptNote("dup", "dup", key)
        assertNotEquals(note.encryptedTitle, note.encryptedContent)
    }

    @Test fun ciphertextDoesNotContainPlaintext() {
        val encoded = PrivateVault.encrypt("my secret diary entry", key)
        assertFalse(encoded.contains("secret"))
        assertFalse(encoded.contains("diary"))
    }

    @Test fun saltUniquenessAndKeyDerivation() {
        val saltA = PrivateVault.generateSalt()
        val saltB = PrivateVault.generateSalt()
        assertNotEquals("Two generated salts must differ", saltA, saltB)

        val keyA1 = PrivateVault.deriveKey(pin, saltA)
        val keyA2 = PrivateVault.deriveKey(pin, saltA)
        assertEquals(
            "Same PIN + same salt must derive the same key",
            java.util.Base64.getEncoder().encodeToString(keyA1.encoded),
            java.util.Base64.getEncoder().encodeToString(keyA2.encoded),
        )

        val keyB = PrivateVault.deriveKey(pin, saltB)
        assertNotEquals(
            "Different salt must derive a different key",
            java.util.Base64.getEncoder().encodeToString(keyA1.encoded),
            java.util.Base64.getEncoder().encodeToString(keyB.encoded),
        )
        // ...and the different-salt key cannot decrypt salt-A ciphertext.
        val encoded = PrivateVault.encrypt("salt-bound", keyA1)
        assertNull(PrivateVault.tryDecrypt(encoded, keyB))
    }

    @Test fun saltIsAtLeast16Bytes() {
        val saltBytes = java.util.Base64.getDecoder().decode(PrivateVault.generateSalt())
        assertTrue(saltBytes.size >= 16)
        assertEquals(PrivateVault.SALT_LENGTH_BYTES, saltBytes.size)
    }

    @Test fun constantsMeetSpec() {
        assertTrue(PrivateVault.PBKDF2_ITERATIONS >= 100_000)
        assertEquals(256, PrivateVault.KEY_LENGTH_BITS)
        assertEquals(12, PrivateVault.IV_LENGTH_BYTES)
    }

    @Test fun blankPinAndMalformedSaltRejected() {
        try {
            PrivateVault.deriveKey("", salt)
            fail("Expected rejection of blank PIN")
        } catch (expected: IllegalArgumentException) {
        }
        try {
            PrivateVault.deriveKey(pin, "not-base64!!!")
            fail("Expected rejection of malformed salt")
        } catch (expected: IllegalArgumentException) {
        }
    }
}
