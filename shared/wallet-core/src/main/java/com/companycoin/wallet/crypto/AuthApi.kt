package com.companycoin.wallet.crypto

import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * CoCo website account client. The account is OPTIONAL and is only used for
 * encrypted wallet backup/restore. Voting and on-device signing never need it.
 */
class AuthApi(private val serverUrl: String) {

    private val client = SharedHttpClient.instance

    private fun post(path: String, body: JSONObject, token: String? = null): JSONObject {
        val req = Request.Builder()
            .url("$serverUrl$path")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .build()
        return req.executeJson(client)
    }

    private fun get(path: String, token: String): JSONObject {
        val req = Request.Builder()
            .url("$serverUrl$path")
            .get()
            .header("Authorization", "Bearer $token")
            .build()
        return req.executeJson(client)
    }

    data class Session(val token: String, val email: String?, val name: String?)

    /** Create a website account. The voter then verifies the email and signs in. */
    fun register(email: String, password: String, name: String? = null): JSONObject {
        return post("/api/auth/register", JSONObject().apply {
            put("username", email)
            put("password", password)
            if (!name.isNullOrBlank()) put("name", name)
        })
    }

    /** Thrown when the account has 2FA enabled and no code was supplied yet. */
    class TotpRequiredException : Exception("Enter your 2FA code")

    fun login(email: String, password: String, totpCode: String? = null): Session {
        var resp = post("/api/auth/login", JSONObject().apply {
            put("username", email)
            put("password", password)
        })
        if (resp.optBoolean("requires_totp", false)) {
            if (totpCode.isNullOrBlank()) throw TotpRequiredException()
            val partial = resp.optString("token", "")
            if (partial.isEmpty()) throw ApiException(0, "2FA login did not return a partial token")
            resp = post("/api/auth/login-totp", JSONObject().apply {
                put("code", totpCode.trim())
            }, partial)
        }
        val token = resp.optString("token", "")
        if (token.isEmpty()) throw ApiException(0, "Login did not return a token")
        val user = resp.optJSONObject("user")
        return Session(token, user?.optString("email") ?: email, user?.optString("name"))
    }

    fun me(token: String): JSONObject = get("/api/auth/me", token)

    /** Register this device wallet in the website account (address only). */
    fun saveWallet(token: String, address: String, label: String? = null,
                   curve: String = "ed25519", network: String = "mainnet"): JSONObject {
        return post("/api/auth/save-wallet", JSONObject().apply {
            put("address", address)
            put("curve", curve)
            put("network", network)
            if (!label.isNullOrBlank()) put("label", label)
        }, token)
    }

    data class AccountWallet(
        val address: String,
        val label: String?,
        val curve: String,
        val network: String,
        val hasBackup: Boolean,
    ) {
        val appCompatible: Boolean get() = curve == "ed25519" && network == "mainnet"
    }

    fun wallets(token: String): List<AccountWallet> {
        val resp = get("/api/auth/wallets", token)
        val arr = resp.optJSONArray("wallets") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val w = arr.getJSONObject(i)
            AccountWallet(
                address = w.getString("address"),
                label = if (w.isNull("label")) null else w.optString("label"),
                curve = w.optString("curve", "sr25519"),
                network = w.optString("network", "legacy"),
                hasBackup = w.optBoolean("hasBackup", false),
            )
        }
    }

    data class RestoreResult(
        val address: String,
        val mnemonic: String,
        val curve: String,
        val network: String,
        val label: String?,
    )

    fun restore(token: String, address: String, password: String): RestoreResult {
        val resp = post("/api/auth/wallet/restore", JSONObject().apply {
            put("address", address)
            put("password", password)
        }, token)
        val mnemonic = resp.optString("mnemonic", "")
        if (mnemonic.isEmpty()) throw ApiException(0, "No seed backup returned")
        return RestoreResult(
            address = resp.optString("address", address),
            mnemonic = mnemonic,
            curve = resp.optString("curve", "ed25519"),
            network = resp.optString("network", "mainnet"),
            label = if (resp.isNull("label")) null else resp.optString("label"),
        )
    }

    fun backup(
        token: String,
        address: String,
        mnemonic: String,
        curve: String,
        network: String,
        label: String?,
        password: String,
    ): JSONObject {
        return post("/api/auth/wallet/backup", JSONObject().apply {
            put("address", address)
            put("mnemonic", mnemonic)
            put("curve", curve)
            put("network", network)
            put("label", label ?: "CoCo Wallet")
            put("password", password)
        }, token)
    }
}
