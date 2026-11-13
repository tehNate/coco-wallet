package com.companycoin.wallet

/** Last wallet operation result, shown in Settings → Diagnostics. */
object WalletDiag {
    @Volatile var lastSendError: String? = null
    @Volatile var lastSendStatus: String? = null
}
