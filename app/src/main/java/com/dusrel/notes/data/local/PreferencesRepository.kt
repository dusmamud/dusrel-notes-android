package com.dusrel.notes.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dusrel.notes.domain.crypto.Lockout
import com.dusrel.notes.domain.crypto.LockoutState
import com.dusrel.notes.domain.crypto.PrivateVault
import javax.crypto.SecretKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.notesDataStore: DataStore<Preferences> by preferencesDataStore(name = "notes_prefs")

enum class ThemeSetting { LIGHT, DARK, SYSTEM }

class PreferencesRepository(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    // Old unsalted SHA-256 PIN hash key — deliberately NOT used anymore.
    // It is removed on first vault setup so the old and new systems never run together.
    private val legacyPinHashKey = stringPreferencesKey("pin_hash")
    private val vaultSaltKey = stringPreferencesKey("private_vault_salt")
    private val vaultVerifierKey = stringPreferencesKey("private_vault_verifier")
    private val lockoutStateKey = stringPreferencesKey("private_lockout_state")
    private val guideDoneKey = booleanPreferencesKey("guide_done")
    private val isGridViewKey = booleanPreferencesKey("is_grid_view")

    val theme: Flow<ThemeSetting> = context.notesDataStore.data.map { prefs ->
        runCatching { ThemeSetting.valueOf(prefs[themeKey] ?: "SYSTEM") }.getOrDefault(ThemeSetting.SYSTEM)
    }
    val guideDone: Flow<Boolean> = context.notesDataStore.data.map { it[guideDoneKey] == true }
    val isGridView: Flow<Boolean> = context.notesDataStore.data.map { it[isGridViewKey] ?: true }

    suspend fun setTheme(theme: ThemeSetting) {
        context.notesDataStore.edit { it[themeKey] = theme.name }
    }
    suspend fun setGuideDone() {
        context.notesDataStore.edit { it[guideDoneKey] = true }
    }
    suspend fun setGridView(isGrid: Boolean) {
        context.notesDataStore.edit { it[isGridViewKey] = isGrid }
    }

    /** True when a vault (salt + verifier) exists. The PIN itself is never stored. */
    suspend fun hasPin(): Boolean {
        val prefs = context.notesDataStore.data.first()
        return prefs[vaultSaltKey] != null && prefs[vaultVerifierKey] != null
    }

    /**
     * First-time PIN setup: generates a fresh salt + verifier, deletes any
     * legacy PIN hash, resets lockout, and returns the derived key (memory only).
     * Callers must re-encrypt existing private notes with the returned key.
     */
    suspend fun setPin(pin: String): SecretKey {
        val salt = PrivateVault.generateSalt()
        val key = PrivateVault.deriveKey(pin, salt)
        val verifier = PrivateVault.createVerifier(key)
        context.notesDataStore.edit {
            it.remove(legacyPinHashKey)
            it[vaultSaltKey] = salt
            it[vaultVerifierKey] = verifier
            it[lockoutStateKey] = LockoutState().toStorageString()
        }
        return key
    }

    /**
     * Unlock attempt. Returns the derived key on success, null on wrong PIN or
     * while locked out. Wrong PINs fail via the GCM verifier check; after 5
     * consecutive failures the vault locks for 30 seconds (persisted).
     */
    suspend fun unlockWithPin(pin: String): SecretKey? {
        val prefs = context.notesDataStore.data.first()
        val salt = prefs[vaultSaltKey] ?: return null
        val verifier = prefs[vaultVerifierKey] ?: return null
        val lockout = Lockout(LockoutState.fromStorageString(prefs[lockoutStateKey]))
        if (lockout.isLocked()) return null
        val key = runCatching { PrivateVault.deriveKey(pin, salt) }.getOrNull() ?: run {
            persistLockout(lockout.recordFailure())
            return null
        }
        return if (PrivateVault.verifyPin(key, verifier)) {
            persistLockout(lockout.recordSuccess())
            key
        } else {
            persistLockout(lockout.recordFailure())
            null
        }
    }

    suspend fun lockoutRemainingSeconds(): Long {
        val prefs = context.notesDataStore.data.first()
        return Lockout(LockoutState.fromStorageString(prefs[lockoutStateKey])).remainingSeconds()
    }

    private suspend fun persistLockout(state: LockoutState) {
        context.notesDataStore.edit { it[lockoutStateKey] = state.toStorageString() }
    }
}
