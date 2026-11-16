package com.companycoin.wallet.crypto

import android.content.Context

/**
 * The two public CoCo websites have separate account databases:
 *   - companycoin.org (production, 85)
 *   - cocoio.cc        (beta, 204)
 * Both expose the same /api/auth, /api/chain/mainnet and wallet-vault routes.
 * The app defaults to production and lets the user switch to beta in Settings
 * so a wallet can be linked to whichever website account they use.
 */
object ServerConfig {
    const val PRODUCTION = "https://companycoin.org"
    const val BETA = "https://cocoio.cc"
    private const val PREFS = "wallet_prefs"
    private const val KEY = "coco_server"

    fun base(context: Context): String {
        // The production companycoin.org send gateway timed out during the
        // 2026-11-16 mainnet test. Default to the working beta gateway until
        // the production path is revalidated. Users can still switch in Settings.
        val value = context.getSharedPreferences(PREFS, 0).getString(KEY, BETA)
        return if (value.isNullOrBlank()) BETA else value
    }

    fun setBase(context: Context, value: String) {
        val clean = if (value == BETA) BETA else PRODUCTION
        context.getSharedPreferences(PREFS, 0).edit().putString(KEY, clean).apply()
    }

    fun isBeta(context: Context): Boolean = base(context) == BETA
}
