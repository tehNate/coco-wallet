package com.companycoin.wallet.crypto

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * A server/network failure with the server's own error message where available.
 * status == 0 means the request never reached the server (timeout/DNS/etc.).
 */
class ApiException(val status: Int, msg: String) : Exception(msg)

internal fun Request.executeJson(client: OkHttpClient): JSONObject {
    try {
        client.newCall(this).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val serverMsg = runCatching { JSONObject(text).optString("error") }.getOrNull()
                val msg = if (!serverMsg.isNullOrBlank()) serverMsg else "HTTP ${resp.code}"
                throw ApiException(resp.code, msg)
            }
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        }
    } catch (e: ApiException) {
        throw e
    } catch (e: Exception) {
        throw ApiException(0, e.message ?: "Network error")
    }
}
