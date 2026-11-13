package com.companycoin.wallet.crypto

/**
 * Keeps the website account wallet list in sync with the wallets that live on
 * this device. Only public metadata (address, curve, network, label) is sent;
 * the mnemonic never leaves the phone unless the user explicitly backs it up.
 */
object WalletSync {

    /** Register every local wallet with the signed-in website account. */
    fun saveAll(walletManager: MultiWalletManager, api: AuthApi, token: String): Int {
        var saved = 0
        for (walletId in walletManager.getWalletIds()) {
            val address = walletManager.getAddressForWallet(walletId, CoinType.COCO) ?: continue
            if (!LocalWallet.isValidAddress(address)) continue
            val label = walletManager.getWalletProfile(walletId)?.name
            try {
                api.saveWallet(token, address, label)
                saved++
            } catch (_: Exception) {
                // A single bad wallet must not block the others.
            }
        }
        return saved
    }
}
