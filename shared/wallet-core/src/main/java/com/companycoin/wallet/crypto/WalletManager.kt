package com.companycoin.wallet.crypto

import android.content.Context

/**
 * Legacy single-coin wallet manager.
 * Kept for backward compat. Migrates to MultiWalletManager.
 * New code should use MultiWalletManager directly.
 */
@Deprecated("Use MultiWalletManager for multi-coin support")
class WalletManager(context: Context) {

    private val multiWallet = MultiWalletManager(context)

    fun hasWallet(): Boolean = multiWallet.hasWallet()
    fun getAddress(): String? = multiWallet.getAddress(CoinType.COCO)
    fun createWallet(): LocalWallet.KeyPair {
        multiWallet.createWallet()
        val addr = multiWallet.getAddress(CoinType.COCO) ?: throw Exception("CoCo address generation failed")
        val pubKey = multiWallet.getPublicKey(CoinType.COCO) ?: throw Exception("CoCo public key generation failed")
        val privKey = multiWallet.getPrivateKey(CoinType.COCO) ?: throw Exception("CoCo private key generation failed")
        return LocalWallet.KeyPair(pubKey, privKey, addr)
    }
    fun importWallet(mnemonic: String): LocalWallet.KeyPair? {
        multiWallet.importWallet(mnemonic) ?: return null
        val addr = multiWallet.getAddress(CoinType.COCO) ?: return null
        val pubKey = multiWallet.getPublicKey(CoinType.COCO) ?: return null
        val privKey = multiWallet.getPrivateKey(CoinType.COCO) ?: return null
        return LocalWallet.KeyPair(pubKey, privKey, addr)
    }
    fun getMnemonic(): String? = multiWallet.getMnemonic()
    fun getPublicKey(): ByteArray? = multiWallet.getPublicKey(CoinType.COCO)
    fun getPrivateKey(): ByteArray? = multiWallet.getPrivateKey(CoinType.COCO)
    fun deleteWallet() {
        multiWallet.getActiveWalletId()?.let { multiWallet.deleteWallet(it) }
    }
    fun requiresBiometric() = true
}
