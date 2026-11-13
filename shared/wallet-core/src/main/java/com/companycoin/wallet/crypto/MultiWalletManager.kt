package com.companycoin.wallet.crypto

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.companycoin.wallet.crypto.WalletProfile
import java.security.KeyStore
import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Multi-wallet manager supporting multiple independent wallets.
 * Each wallet has its own mnemonic seed and derived addresses for all coin types.
 * 
 * Storage structure (SharedPreferences):
 * - wallet_list: JSON array of wallet IDs
 * - active_wallet_id: Currently selected wallet ID
 * - wallet_<id>_profile: JSON wallet profile (name, createdAt, isLocked)
 * - wallet_<id>_mnemonic: Encrypted mnemonic phrase
 * - wallet_<id>_seed: Encrypted master seed
 * - wallet_<id>_addr_<COIN>: Derived address for coin type
 * - wallet_<id>_pub_<COIN>: Public key for coin type
 * - wallet_<id>_priv_<COIN>: Encrypted private key for coin type
 */
class MultiWalletManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("coco_multi_wallet", Context.MODE_PRIVATE)

    companion object {
        private const val KEYSTORE_ALIAS = "coco_wallet_key"
        private const val PREF_WALLET_LIST = "wallet_list"
        private const val PREF_ACTIVE_WALLET = "active_wallet_id"
        private const val GCM_TAG_LENGTH = 128
        
        // Legacy keys for migration from single-wallet version
        private const val PREF_MASTER_MNEMONIC = "master_mnemonic"
        private const val PREF_ENCRYPTED_SEED = "encrypted_seed"
        private const val PREF_IV = "encryption_iv"
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        keyStore.getEntry(KEYSTORE_ALIAS, null)?.let {
            return (it as KeyStore.SecretKeyEntry).secretKey
        }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGenerator.generateKey()
    }

    private fun encrypt(plaintext: String, ivKey: String = PREF_IV): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        prefs.edit().putString(ivKey, Base64.encodeToString(cipher.iv, Base64.NO_WRAP)).apply()
        return Base64.encodeToString(cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun decrypt(ciphertext: String, ivKey: String = PREF_IV): String {
        val iv = Base64.decode(prefs.getString(ivKey, "") ?: "", Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return String(cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)), Charsets.UTF_8)
    }
    
    // ─── WALLET LIST MANAGEMENT ─────────────────────────────────────
    
    /** Get list of all wallet IDs */
    fun getWalletIds(): List<String> {
        val json = prefs.getString(PREF_WALLET_LIST, "[]") ?: "[]"
        return try {
            val array = org.json.JSONArray(json)
            val result = mutableListOf<String>()
            for (i in 0 until array.length()) {
                result.add(array.getString(i))
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /** Get all wallet profiles */
    fun getAllWallets(): List<WalletProfile> {
        return getWalletIds().mapNotNull { id -> getWalletProfile(id) }
    }
    
    /** Get currently active wallet ID */
    fun getActiveWalletId(): String? {
        return prefs.getString(PREF_ACTIVE_WALLET, null)
            ?: getWalletIds().firstOrNull()
    }
    
    /** Set active wallet */
    fun setActiveWallet(walletId: String) {
        if (walletId !in getWalletIds()) {
            throw IllegalArgumentException("Wallet ID not found: $walletId")
        }
        prefs.edit().putString(PREF_ACTIVE_WALLET, walletId).apply()
    }
    
    // ─── WALLET PROFILE MANAGEMENT ──────────────────────────────────
    
    /** Get wallet profile by ID */
    fun getWalletProfile(walletId: String): WalletProfile? {
        val json = prefs.getString("wallet_${walletId}_profile", null) ?: return null
        return try {
            val obj = org.json.JSONObject(json)
            WalletProfile(
                id = obj.getString("id"),
                name = obj.getString("name"),
                createdAt = obj.getLong("createdAt"),
                isLocked = obj.optBoolean("isLocked", false)
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /** Save wallet profile */
    private fun saveWalletProfile(profile: WalletProfile) {
        val json = org.json.JSONObject().apply {
            put("id", profile.id)
            put("name", profile.name)
            put("createdAt", profile.createdAt)
            put("isLocked", profile.isLocked)
        }.toString()
        prefs.edit().putString("wallet_${profile.id}_profile", json).apply()
    }
    
    // ─── WALLET CREATION & IMPORT ───────────────────────────────────
    
    /** Create a new wallet with generated mnemonic */
    fun createWallet(name: String = "Wallet ${System.currentTimeMillis().toString().takeLast(4)}"): WalletProfile {
        val profile = WalletProfile.create(name)
        val mnemonic = LocalWallet.generateMnemonic()
        val seed = LocalWallet.mnemonicToSeed(mnemonic)
        saveWalletData(profile.id, mnemonic, seed)
        saveWalletProfile(profile)
        addWalletToList(profile.id)
        setActiveWallet(profile.id)
        return profile
    }
    
    /** Import wallet from mnemonic phrase */
    fun importWallet(mnemonic: String, name: String = "Imported ${System.currentTimeMillis().toString().takeLast(4)}"): WalletProfile? {
        return try {
            if (!LocalWallet.isValidMnemonic(mnemonic)) return null
            val seed = LocalWallet.mnemonicToSeed(mnemonic)
            val profile = WalletProfile.create(name)
            saveWalletData(profile.id, mnemonic, seed)
            saveWalletProfile(profile)
            addWalletToList(profile.id)
            setActiveWallet(profile.id)
            profile
        } catch (e: Exception) {
            null
        }
    }
    
    /** Switch to existing wallet by ID */
    fun switchWallet(walletId: String): Boolean {
        if (walletId !in getWalletIds()) return false
        setActiveWallet(walletId)
        return true
    }
    
    /** Delete a wallet permanently */
    fun deleteWallet(walletId: String): Boolean {
        if (walletId !in getWalletIds()) return false
        
        // Remove from list
        val currentList = getWalletIds().toMutableList()
        currentList.remove(walletId)
        updateWalletList(currentList)
        
        // Delete all wallet data
        val editor = prefs.edit()
        editor.remove("wallet_${walletId}_profile")
        editor.remove("wallet_${walletId}_mnemonic")
        editor.remove("wallet_${walletId}_seed")
        for (coin in CoinType.entries) {
            editor.remove("wallet_${walletId}_addr_${coin.name}")
            editor.remove("wallet_${walletId}_pub_${coin.name}")
            editor.remove("wallet_${walletId}_priv_${coin.name}")
            editor.remove("wallet_${walletId}_priv_iv_${coin.name}")
        }
        editor.apply()
        
        // Switch to another wallet if deleted was active
        if (getActiveWalletId() == walletId) {
            getWalletIds().firstOrNull()?.let { setActiveWallet(it) }
        }
        
        return true
    }
    
    /** Rename a wallet */
    fun renameWallet(walletId: String, newName: String): Boolean {
        val profile = getWalletProfile(walletId) ?: return false
        val updated = profile.copy(name = newName)
        saveWalletProfile(updated)
        return true
    }
    
    // ─── WALLET DATA ACCESS (Active Wallet) ─────────────────────────
    
    /** Check if any wallet exists */
    fun hasWallet(): Boolean = getWalletIds().isNotEmpty()
    
    /** Get active wallet's address for a coin type */
    fun getAddress(coinType: CoinType): String? {
        val walletId = getActiveWalletId() ?: return null
        return prefs.getString("wallet_${walletId}_addr_${coinType.name}", null)
    }

    /** Get a specific wallet's address for a coin type. */
    fun getAddressForWallet(walletId: String, coinType: CoinType): String? {
        return prefs.getString("wallet_${walletId}_addr_${coinType.name}", null)
    }

    /** Import a raw 32-byte Ed25519 seed (64 hex chars). No mnemonic. */
    fun importFromSeed(seedHex: String, name: String = "Imported ${System.currentTimeMillis().toString().takeLast(4)}"): WalletProfile? {
        val clean = seedHex.trim().removePrefix("0x")
        if (!clean.matches(Regex("^[0-9a-fA-F]{64}$"))) return null
        return try {
            val seed = ByteArray(32) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
            val profile = WalletProfile.create(name)
            saveWalletDataSeedOnly(profile.id, seed)
            saveWalletProfile(profile)
            addWalletToList(profile.id)
            setActiveWallet(profile.id)
            profile
        } catch (e: Exception) { null }
    }

    private fun saveWalletDataSeedOnly(walletId: String, seed: ByteArray) {
        val encryptedSeed = encrypt(Base64.encodeToString(seed, Base64.NO_WRAP), "wallet_${walletId}_seed_iv")
        val editor = prefs.edit()
        editor.putString("wallet_${walletId}_seed", encryptedSeed)
        val allKeys = HDWallet.deriveAllKeys(seed)
        for ((coin, key) in allKeys) {
            editor.putString("wallet_${walletId}_addr_${coin.name}", key.address)
            editor.putString("wallet_${walletId}_pub_${coin.name}", Base64.encodeToString(key.publicKey, Base64.NO_WRAP))
            editor.putString(
                "wallet_${walletId}_priv_${coin.name}",
                encrypt(Base64.encodeToString(key.privateKey, Base64.NO_WRAP), "wallet_${walletId}_priv_iv_${coin.name}")
            )
        }
        editor.apply()
    }

    /**
     * Repair addresses created by app versions <= 1.8: their SS58 checksum
     * omitted the "SS58PRE" prefix, so the address bytes were right but the
     * checksum (and therefore the last chars) were wrong. Re-derive COCO keys
     * from the stored seed; funds live on the public key, so nothing moves.
     */
    fun repairLegacyAddresses() {
        for (walletId in getWalletIds()) {
            val seedEnc = prefs.getString("wallet_${walletId}_seed", null) ?: continue
            val seedB64 = try { decrypt(seedEnc, "wallet_${walletId}_seed_iv") } catch (e: Exception) { continue }
            val seed = try { Base64.decode(seedB64, Base64.NO_WRAP) } catch (e: Exception) { continue }
            if (seed.size < 32) continue
            val kp = LocalWallet.deriveKeyPair(seed)
            val old = prefs.getString("wallet_${walletId}_addr_COCO", null)
            if (old != kp.address) {
                val encPriv = encrypt(
                    Base64.encodeToString(kp.privateKey, Base64.NO_WRAP),
                    "wallet_${walletId}_priv_iv_COCO"
                )
                prefs.edit()
                    .putString("wallet_${walletId}_addr_COCO", kp.address)
                    .putString("wallet_${walletId}_pub_COCO", Base64.encodeToString(kp.publicKey, Base64.NO_WRAP))
                    .putString("wallet_${walletId}_priv_COCO", encPriv)
                    .apply()
            }
        }
    }

    /**
     * Backfill missing profile records. Versions <= 1.9 wrote wallet keys and
     * the wallet list but never saved the JSON profile, so the wallet switcher
     * and labels were empty after a restart.
     */
    fun repairWalletProfiles() {
        for (walletId in getWalletIds()) {
            if (getWalletProfile(walletId) != null) continue
            val name = "Wallet ${walletId.takeLast(4)}"
            saveWalletProfile(
                WalletProfile(
                    id = walletId,
                    name = name,
                    createdAt = System.currentTimeMillis(),
                    isLocked = false
                )
            )
        }
    }

    /** Find an existing wallet profile whose address for [coinType] matches. */
    fun findWalletByAddress(address: String, coinType: CoinType = CoinType.COCO): String? {
        for (id in getWalletIds()) {
            if (prefs.getString("wallet_${id}_addr_${coinType.name}", null) == address) return id
        }
        return null
    }
    
    /** Get active wallet's public key for a coin type */
    fun getPublicKey(coinType: CoinType): ByteArray? {
        val walletId = getActiveWalletId() ?: return null
        val encoded = prefs.getString("wallet_${walletId}_pub_${coinType.name}", null) ?: return null
        return try { Base64.decode(encoded, Base64.NO_WRAP) } catch (e: Exception) { null }
    }
    
    /** Get active wallet's private key for a coin type */
    fun getPrivateKey(coinType: CoinType): ByteArray? {
        val walletId = getActiveWalletId() ?: return null
        val encoded = prefs.getString("wallet_${walletId}_priv_${coinType.name}", null) ?: return null
        return try {
            val decrypted = decrypt(encoded, "wallet_${walletId}_priv_iv_${coinType.name}")
            Base64.decode(decrypted, Base64.NO_WRAP)
        } catch (e: Exception) { null }
    }
    
    /** Get active wallet's mnemonic (requires decryption) */
    fun getMnemonic(): String? {
        val walletId = getActiveWalletId() ?: return null
        val encrypted = prefs.getString("wallet_${walletId}_mnemonic", null) ?: return null
        return try { decrypt(encrypted, "wallet_${walletId}_iv") } catch (e: Exception) { null }
    }
    
    /** Raw 32-byte Ed25519 seed (hex) for the active wallet, if available. */
    fun getSeedHex(): String? {
        val walletId = getActiveWalletId() ?: return null
        val enc = prefs.getString("wallet_${walletId}_seed", null) ?: return null
        return try {
            val b64 = decrypt(enc, "wallet_${walletId}_seed_iv")
            Base64.decode(b64, Base64.NO_WRAP).joinToString("") { String.format("%02x", it) }
        } catch (e: Exception) { null }
    }

    /** Get all addresses for active wallet */
    fun getAllAddresses(): Map<CoinType, String> {
        val map = mutableMapOf<CoinType, String>()
        for (coin in CoinType.entries) {
            val addr = getAddress(coin) ?: continue
            map[coin] = addr
        }
        return map
    }
    
    // ─── PRIVATE HELPER METHODS ─────────────────────────────────────
    
    /** Save wallet data (mnemonic, seed, derived keys) */
    private fun saveWalletData(walletId: String, mnemonic: String, seed: ByteArray) {
        val encryptedMnemonic = encrypt(mnemonic, "wallet_${walletId}_iv")
        val encryptedSeed = encrypt(Base64.encodeToString(seed, Base64.NO_WRAP), "wallet_${walletId}_seed_iv")
        
        val editor = prefs.edit()
        editor.putString("wallet_${walletId}_mnemonic", encryptedMnemonic)
        editor.putString("wallet_${walletId}_seed", encryptedSeed)
        
        // Derive and save all coin keys
        val allKeys = HDWallet.deriveAllKeys(seed)
        for ((coin, key) in allKeys) {
            editor.putString("wallet_${walletId}_addr_${coin.name}", key.address)
            editor.putString("wallet_${walletId}_pub_${coin.name}", Base64.encodeToString(key.publicKey, Base64.NO_WRAP))
            val encryptedPriv = encrypt(
                Base64.encodeToString(key.privateKey, Base64.NO_WRAP),
                "wallet_${walletId}_priv_iv_${coin.name}"
            )
            editor.putString("wallet_${walletId}_priv_${coin.name}", encryptedPriv)
        }
        editor.apply()
    }
    
    /** Add wallet ID to the list */
    private fun addWalletToList(walletId: String) {
        val currentList = getWalletIds().toMutableList()
        if (walletId !in currentList) {
            currentList.add(walletId)
            updateWalletList(currentList)
        }
    }
    
    /** Update wallet list in preferences */
    private fun updateWalletList(walletIds: List<String>) {
        val array = org.json.JSONArray()
        walletIds.forEach { array.put(it) }
        prefs.edit().putString(PREF_WALLET_LIST, array.toString()).apply()
    }
    
    /** Save wallet profile (called after creation) */
    private fun saveWalletProfileInternal(profile: WalletProfile) {
        val json = org.json.JSONObject().apply {
            put("id", profile.id)
            put("name", profile.name)
            put("createdAt", profile.createdAt)
            put("isLocked", profile.isLocked)
        }.toString()
        prefs.edit().putString("wallet_${profile.id}_profile", json).apply()
    }
    
    // ─── MIGRATION FROM SINGLE-WALLET VERSION ───────────────────────
    
    /** Migrate from legacy single-wallet storage */
    fun migrateFromSingleWallet() {
        if (hasWallet()) return  // Already has multi-wallet data
        
        // Check for legacy single-wallet keys
        if (!prefs.contains(PREF_MASTER_MNEMONIC)) return
        
        val oldMnemonic = try {
            decrypt(prefs.getString(PREF_MASTER_MNEMONIC, null) ?: return)
        } catch (e: Exception) { return }
        
        // Import as first wallet
        importWallet(oldMnemonic, "Main Wallet")
        
        // Clear legacy keys
        prefs.edit().apply {
            remove(PREF_MASTER_MNEMONIC)
            remove(PREF_ENCRYPTED_SEED)
            remove(PREF_IV)
        }.apply()
    }
}
