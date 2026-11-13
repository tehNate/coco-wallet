package com.companycoin.wallet.crypto

import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Voter-registration client for CoCo Vote.
 * The admin pre-generates a wallet per registered voter; the voter claims it
 * with the identity details from the official register (SSN + name). The
 * server matches a peppered hash and hands the mnemonic over exactly once
 * (with a short retry window so a dropped connection cannot lose it).
 */
class ClaimApi(private val serverUrl: String) {

    private val client = SharedHttpClient.instance

    private fun post(path: String, body: JSONObject, authToken: String? = null): JSONObject {
        val req = Request.Builder()
            .url("$serverUrl$path")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .apply { authToken?.let { header("Authorization", "Bearer $it") } }
            .build()
        return req.executeJson(client)
    }

    private fun get(path: String): JSONObject {
        val req = Request.Builder().url("$serverUrl$path").get().build()
        return req.executeJson(client)
    }

    data class ClaimResult(
        val mnemonic: String,
        val address: String,
        val registry: String?,
        val kycApproved: Boolean,
    )

    fun claim(ssn: String, name: String, dob: String? = null, authToken: String? = null): ClaimResult {
        val body = JSONObject().apply {
            put("ssn", ssn)
            put("name", name)
            if (!dob.isNullOrBlank()) put("dob", dob)
        }
        val resp = post("/api/registry/claim", body, authToken)
        val mnemonic = resp.optString("mnemonic", "")
        val address = resp.optString("address", "")
        if (mnemonic.isEmpty() || address.isEmpty()) {
            throw ApiException(0, "Claim response did not include a wallet")
        }
        return ClaimResult(mnemonic, address, resp.optString("registry"), resp.optBoolean("kycApproved"))
    }

    data class RegistryStatus(
        val registered: Boolean,
        val status: String?,
        val kycApproved: Boolean,
        val registryName: String?,
        val tokenBalance: Long?,
    )

    fun status(address: String, electionId: Int? = null): RegistryStatus {
        val q = if (electionId != null) "?electionId=$electionId" else ""
        val resp = get("/api/registry/status/$address$q")
        val entry = resp.optJSONObject("entry")
        return RegistryStatus(
            registered = resp.optBoolean("registered", false),
            status = entry?.optString("status"),
            kycApproved = resp.optBoolean("kycApproved", false),
            registryName = entry?.optString("registry_name"),
            tokenBalance = if (resp.has("tokenBalance") && !resp.isNull("tokenBalance")) {
                resp.optString("tokenBalance").toLongOrNull()
            } else null,
        )
    }
}
