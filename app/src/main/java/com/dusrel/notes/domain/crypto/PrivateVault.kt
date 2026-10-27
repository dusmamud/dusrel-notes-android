package com.dusrel.notes.domain.crypto

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Real encryption for private notes. Pure JVM: only standard JCE
 * (javax.crypto / java.security / java.util) — no Android classes,
 * so this file compiles and runs in plain unit tests.
 *
 * This REPLACES the old model (unsalted SHA-256 PIN hash in DataStore
 * plus private notes merely hidden). There is deliberately no hash
 * comparison anywhere in this module: a wrong PIN fails because the
 * AES-GCM authentication tag does not verify on decrypt. The old
 * `pin_hash` key must be deleted by the wiring step, not kept
 * alongside this module (see WIRING below).
 *
 * Design decisions (locked, so the coordinator can wire against them):
 * 1. Title and content are encrypted SEPARATELY, each with its own
 *    fresh random IV, via [encryptNote]/[decryptNote]. Separate fields
 *    (rather than one combined payload) keep the existing Room columns
 *    `title` / `content` usable as ciphertext columns, and identical
 *    titles do not leak equality with identical contents.
 * 2. One encoded string per field. Format: Base64( IV || ciphertext ||
 *    GCM tag ), where IV is 12 bytes and the 128-bit GCM tag is
 *    appended to the ciphertext by JCE. No separator is needed because
 *    the IV length is fixed: decrypt takes the first 12 decoded bytes
 *    as the IV and the rest as ciphertext+tag.
 * 3. Key derivation: PBKDF2-HmacSHA256, 100,000 iterations, 256-bit
 *    key, per-install random salt (32 bytes, Base64-encoded for
 *    storage). The salt is NOT secret — it is stored separately from
 *    ciphertext in DataStore — but it must never be reused across
 *    installs/PIN changes: generate a fresh salt whenever the PIN is
 *    set or changed, and re-encrypt all private notes under the new
 *    key at that moment (see WIRING).
 * 4. The derived key is NEVER persisted. It lives only in memory for
 *    the unlocked session and must be dropped (reference cleared) when
 *    the vault locks. Persisting the key would defeat the encryption.
 * 5. PIN correctness without any private note to trial-decrypt: at PIN
 *    setup the coordinator stores a verifier — [createVerifier] output
 *    — in DataStore. Unlock = derive key from the entered PIN + stored
 *    salt, then [verifyPin]; success means the PIN is correct, failure
 *    (tag mismatch) means it is wrong. The verifier plaintext is a
 *    fixed public constant; it reveals nothing about the PIN.
 *
 * WIRING (for the coordinator — this file does not touch DataStore,
 * Room, ViewModel, or UI):
 * - DataStore keys (new, string keys): `private_vault_salt`
 *   (Base64 from [generateSalt]), `private_vault_verifier`
 *   (output of [createVerifier]), `private_lockout_state`
 *   (output of [LockoutState.toStorageString]). DELETE the old
 *   `pin_hash` key on migration; do not keep both systems.
 * - Room (new fields on NoteEntity, coordinator's edit): add
 *   `encryptedTitle: String = ""` and `encryptedContent: String = ""`
 *   plus `isEncrypted: Boolean = false`. When a note is private AND
 *   locked, `title`/`content` must hold NOTHING readable: either
 *   empty strings or the ciphertext moved into the new columns — pick
 *   one in wiring and keep plaintext columns empty for encrypted
 *   notes. Never write decrypted private text back to Room.
 * - Unlock flow: (a) if [Lockout.isLocked] reject before any crypto;
 *   (b) derive key from entered PIN + stored salt; (c) [verifyPin]
 *   against the stored verifier; (d) on success call
 *   [Lockout.recordSuccess] and hold the key in memory only;
 *   (e) on failure call [Lockout.recordFailure] and persist state.
 *   While locked, attempts must be rejected WITHOUT deriving a key
 *   or attempting a decrypt (see [Lockout]).
 * - Privacy rules the coordinator MUST enforce (not enforced here,
 *   because this module has no access to lists/search/widgets):
 *   private notes stay hidden from the main list, search, widgets,
 *   and export until unlocked; search must run only over decrypted
 *   in-memory notes during an unlocked session, never over stored
 *   ciphertext or cached plaintext; widgets must never display a
 *   private note's title/content, locked or unlocked.
 * - Export default (locked decision): EXCLUDE private notes from
 *   export entirely unless the user explicitly exports AFTER
 *   unlocking. Encrypted private notes must NOT appear in a backup
 *   as opaque ciphertext either: ciphertext in a backup still leaks
 *   note existence, sizes, and update patterns, and invites offline
 *   PIN guessing against the backup file. So the default backup
 *   contains no private-note rows at all; an unlocked, explicitly
 *   requested export may include their decrypted content only with
 *   a clear user confirmation, and must warn that the backup file
 *   itself is then unencrypted.
 *
 * Failure semantics: [decrypt] throws on any failure — wrong key
 * (wrong PIN), tampered ciphertext, truncated input, or malformed
 * Base64 — wrapped as [VaultException] except that a GCM tag failure
 * is surfaced as [VaultAuthenticationException] (a subclass) so the
 * coordinator can count it as a failed PIN attempt without treating
 * corrupt storage the same way. [tryDecrypt] is the non-throwing
 * variant; [verifyPin] never throws.
 */
object PrivateVault {

    /** PBKDF2 iterations. At least 100,000 by spec; do not lower. */
    const val PBKDF2_ITERATIONS = 100_000

    /** Derived key length in bits. */
    const val KEY_LENGTH_BITS = 256

    /** Salt length in bytes. At least 16 by spec; 32 is used. */
    const val SALT_LENGTH_BYTES = 32

    /** GCM IV length in bytes. 12 is the recommended GCM nonce size. */
    const val IV_LENGTH_BYTES = 12

    /** GCM authentication tag length in bits. */
    const val GCM_TAG_LENGTH_BITS = 128

    /** Fixed plaintext encrypted at PIN setup as the unlock verifier. */
    const val VERIFIER_PLAINTEXT = "dusrel-notes-private-vault-v1"

    private val secureRandom = SecureRandom()

    /** A note's two fields, each encrypted separately (see header). */
    data class EncryptedNote(
        val encryptedTitle: String,
        val encryptedContent: String,
    )

    /** A note's two fields in plaintext (in-memory only, never stored). */
    data class PlainNote(
        val title: String,
        val content: String,
    )

    /**
     * Generates a fresh random salt, Base64-encoded for DataStore
     * storage. Call once per PIN setup/change, never reuse across
     * installs or PIN changes.
     */
    fun generateSalt(): String {
        val salt = ByteArray(SALT_LENGTH_BYTES)
        secureRandom.nextBytes(salt)
        return Base64.getEncoder().encodeToString(salt)
    }

    /**
     * Derives the vault key from [pin] and the Base64 [saltBase64]
     * produced by [generateSalt]. The same PIN + same salt always
     * derives the same key; a different salt derives a different key.
     * @throws IllegalArgumentException for a blank PIN or malformed salt.
     */
    fun deriveKey(pin: String, saltBase64: String): SecretKey {
        require(pin.isNotEmpty()) { "PIN must not be empty" }
        val salt = try {
            Base64.getDecoder().decode(saltBase64)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Malformed vault salt", e)
        }
        require(salt.size >= 16) { "Vault salt must be at least 16 bytes" }
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val derived = factory.generateSecret(spec)
            SecretKeySpec(derived.encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    /**
     * Encrypts [plaintext] under [key]. Returns a single Base64
     * string: Base64( 12-byte IV || ciphertext || 128-bit GCM tag ).
     * Every call uses a fresh random IV, so the same plaintext
     * encrypts differently each time.
     */
    fun encrypt(plaintext: String, key: SecretKey): String {
        val iv = ByteArray(IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = iv + ciphertext
        return Base64.getEncoder().encodeToString(combined)
    }

    /**
     * Decrypts a string produced by [encrypt].
     * @return the original plaintext.
     * @throws VaultAuthenticationException if the GCM tag does not
     *   verify — i.e. wrong PIN/key or tampered ciphertext.
     * @throws VaultException for malformed input (bad Base64, too
     *   short to contain an IV, undecryptable for a non-auth reason).
     */
    fun decrypt(encoded: String, key: SecretKey): String {
        val combined = try {
            Base64.getDecoder().decode(encoded)
        } catch (e: IllegalArgumentException) {
            throw VaultException("Malformed encrypted value", e)
        }
        require(combined.size > IV_LENGTH_BYTES) {
            "Encrypted value too short to contain an IV and ciphertext"
        }
        val iv = combined.copyOfRange(0, IV_LENGTH_BYTES)
        val ciphertext = combined.copyOfRange(IV_LENGTH_BYTES, combined.size)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
        } catch (e: AEADBadTagException) {
            throw VaultAuthenticationException("Authentication failed: wrong key or tampered data", e)
        } catch (e: Exception) {
            throw VaultException("Decryption failed", e)
        }
    }

    /**
     * Non-throwing variant of [decrypt]: returns null on any failure
     * instead of throwing. Useful where a failed decrypt is an
     * expected outcome (e.g. a wrong-PIN probe), not an error.
     */
    fun tryDecrypt(encoded: String, key: SecretKey): String? =
        runCatching { decrypt(encoded, key) }.getOrNull()

    /** Encrypts a note's title and content, each with its own IV. */
    fun encryptNote(title: String, content: String, key: SecretKey): EncryptedNote =
        EncryptedNote(
            encryptedTitle = encrypt(title, key),
            encryptedContent = encrypt(content, key),
        )

    /** Decrypts both fields of an [EncryptedNote]. Throws as [decrypt]. */
    fun decryptNote(note: EncryptedNote, key: SecretKey): PlainNote =
        PlainNote(
            title = decrypt(note.encryptedTitle, key),
            content = decrypt(note.encryptedContent, key),
        )

    /**
     * Creates the unlock verifier to store in DataStore at PIN setup:
     * the fixed [VERIFIER_PLAINTEXT] encrypted under [key]. Re-create
     * it whenever the PIN (and therefore the salt/key) changes.
     */
    fun createVerifier(key: SecretKey): String = encrypt(VERIFIER_PLAINTEXT, key)

    /**
     * Returns true only if [key] decrypts the stored [verifier] back
     * to [VERIFIER_PLAINTEXT]. Never throws: any failure means
     * "wrong PIN". This is the PIN check — there is no hash to compare.
     */
    fun verifyPin(key: SecretKey, verifier: String): Boolean =
        tryDecrypt(verifier, key) == VERIFIER_PLAINTEXT
}

/** Base failure for vault operations (malformed input, bad salt, etc.). */
open class VaultException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

/**
 * GCM authentication failure: the key is wrong (wrong PIN) or the
 * ciphertext was tampered with. The coordinator counts this as a
 * failed PIN attempt via [Lockout.recordFailure].
 */
class VaultAuthenticationException(message: String, cause: Throwable? = null) :
    VaultException(message, cause)
