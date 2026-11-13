package com.companycoin.wallet.crypto

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

val JSON_MEDIA = "application/json".toMediaType()

interface ChainRpcClient {
    fun getBalance(address: String, coinType: CoinType): String
    fun getTransactionHistory(address: String, coinType: CoinType): List<CoinTransaction>
    fun submitTransfer(privateKey: ByteArray, to: String, amount: String, coinType: CoinType): String?
    fun getGasEstimate(from: String, to: String, amount: String, coinType: CoinType): String
    fun isValidAddress(address: String, coinType: CoinType): Boolean
}

data class CoinTransaction(
    val hash: String,
    val from: String,
    val to: String,
    val amount: String,
    val timestamp: Long,
    val status: String,
    val explorerUrl: String,
)

object SharedHttpClient {
    val instance: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            // Claim/registration and on-chain relays can exceed 15s.
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}

// CoCo (substrate) bridge. Talks to the SITE (beta proxy), never raw node RPC:
//   mainnet base = ServerConfig.base(context) (companycoin.org or cocoio.cc)
//   -> /api/chain/mainnet/*
//   testnet base = CoinType.COCO.testnetRpc (localhost:4000, via `adb reverse`)
//     — the dev server must run with MAINNET_CHAIN_URL=ws://127.0.0.1:9933
// Signing is 100% on-device: server frames a SignerPayload (prepare-transfer),
// wallet signs the raw bytes with Ed25519 (LocalWallet/RFC 8032), server
// reassembles and submits (submit-transfer). The seed never crosses the network.
class CoCoChainClient(private val serverUrl: String, private val authToken: String? = null) : ChainRpcClient {

    private val client = SharedHttpClient.instance

    private fun post(path: String, body: JSONObject): JSONObject {
        val req = Request.Builder()
            .url("$serverUrl$path")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .apply { authToken?.let { header("Authorization", "Bearer $it") } }
            .build()
        return req.executeJson(client)
    }

    private fun get(path: String): JSONObject {
        val req = Request.Builder()
            .url("$serverUrl$path")
            .get()
            .apply { authToken?.let { header("Authorization", "Bearer $it") } }
            .build()
        return req.executeJson(client)
    }

    override fun getBalance(address: String, coinType: CoinType): String {
        val resp = post("/api/chain/mainnet/balance", JSONObject().apply { put("address", address) })
        return if (coinType == CoinType.COCO) {
            resp.optString("coco", "0")
        } else {
            val assets = resp.optJSONArray("bridgedAssets")
            if (assets != null) for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("symbol") == coinType.symbol) return a.getString("balance")
            }
            "0"
        }
    }

    override fun getTransactionHistory(address: String, coinType: CoinType): List<CoinTransaction> = emptyList()
    override fun getGasEstimate(from: String, to: String, amount: String, coinType: CoinType) = "0"
    override fun isValidAddress(address: String, coinType: CoinType) =
        if (coinType == CoinType.COCO) LocalWallet.isValidAddress(address)
        else address.matches(Regex("^5[1-9A-HJ-NP-Za-km-z]{46,47}$"))

    override fun submitTransfer(privateKey: ByteArray, to: String, amount: String, coinType: CoinType): String? {
        if (!LocalWallet.isValidAddress(to)) throw ApiException(0, "Invalid recipient address")
        val keypair = LocalWallet.deriveKeyPair(privateKey.copyOf(32))
        val fromAddress = keypair.address
        val publicKey = keypair.publicKey
        val prepBody = JSONObject().apply { put("from", fromAddress); put("to", to); put("amount", amount) }
        val prepResp = post("/api/chain/mainnet/prepare-transfer", prepBody)
        val payloadHex = prepResp.getString("payload")
        val ctxId = prepResp.optString("ctxId", "")
        val cleanPayload = payloadHex.removePrefix("0x")
        val payloadBytes = ByteArray(cleanPayload.length / 2) {
            cleanPayload.substring(it * 2, it * 2 + 2).toInt(16).toByte()
        }
        val signature = LocalWallet.sign(privateKey, payloadBytes)
        val submitBody = JSONObject().apply {
            put("from", fromAddress); put("to", to); put("amount", amount)
            put("signature", signature.joinToString("") { String.format("%02x", it) })
            put("publicKey", publicKey.joinToString("") { String.format("%02x", it) })
            if (ctxId.isNotBlank()) put("ctxId", ctxId)
        }
        val out = post("/api/chain/mainnet/submit-transfer", submitBody)
        if (out.optString("status") == "failed") {
            throw ApiException(0, out.optString("error", "Transfer failed"))
        }
        return out.optString("blockHash", "").ifBlank { out.optString("hash", "").ifBlank { null } }
    }
}

class EthereumRpcClient(private val rpcUrl: String, private val useTestnet: Boolean = false) : ChainRpcClient {

    private val client = SharedHttpClient.instance

    private fun jsonRpc(method: String, params: List<Any>): JSONObject? {
        return try {
            val paramsJson = JSONArray()
            for (param in params) paramsJson.put(JSONObject.wrap(param))
            val body = JSONObject().apply {
                put("jsonrpc", "2.0"); put("id", 1); put("method", method)
                put("params", paramsJson)
            }
            val req = Request.Builder()
                .url(rpcUrl)
                .post(body.toString().toRequestBody(JSON_MEDIA))
                .build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return null
            JSONObject(resp.body!!.string())
        } catch (e: Exception) { null }
    }

    override fun getBalance(address: String, coinType: CoinType): String {
        val hexAddr = address.removePrefix("0x")
        if (CoinType.isErc20(coinType)) {
            val contract = coinType.getErc20Contract(useTestnet) ?: return "0"
            val data = "0x70a08231" + "000000000000000000000000" + hexAddr
            val resp = jsonRpc("eth_call", listOf(
                mapOf("to" to contract, "data" to data), "latest"
            ))
            val hex = resp?.getString("result") ?: "0x0"
            return hex.removePrefix("0x").toBigIntegerOrNull(16)?.toString() ?: "0"
        }
        val resp = jsonRpc("eth_getBalance", listOf(address, "latest"))
        val hex = resp?.getString("result") ?: "0x0"
        return hex.removePrefix("0x").toBigIntegerOrNull(16)?.toString() ?: "0"
    }

    override fun getTransactionHistory(address: String, coinType: CoinType): List<CoinTransaction> = emptyList()
    override fun getGasEstimate(from: String, to: String, amount: String, coinType: CoinType): String = "21000"
    override fun isValidAddress(address: String, coinType: CoinType) = address.matches(Regex("^0x[a-fA-F0-9]{40}$"))
    override fun submitTransfer(privateKey: ByteArray, to: String, amount: String, coinType: CoinType): String? = null
}

class BitcoinRpcClient(private val apiUrl: String) : ChainRpcClient {

    private val client = SharedHttpClient.instance

    private fun get(path: String): JSONObject? {
        return try {
            val req = Request.Builder().url("$apiUrl$path").get().build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) null
            else JSONObject(resp.body!!.string())
        } catch (e: Exception) { null }
    }

    override fun getBalance(address: String, coinType: CoinType): String {
        val resp = get("/address/$address") ?: return "0"
        val stats = resp.optJSONObject("address") ?: resp
        val funded = stats.optJSONObject("chain_stats")?.optString("funded_txo_sum", "0") ?: "0"
        val spent = stats.optJSONObject("chain_stats")?.optString("spent_txo_sum", "0") ?: "0"
        val fundedValue = funded.toBigIntegerOrNull() ?: return "0"
        val spentValue = spent.toBigIntegerOrNull() ?: return "0"
        return fundedValue.subtract(spentValue).toString()
    }

    override fun getTransactionHistory(address: String, coinType: CoinType): List<CoinTransaction> = emptyList()
    override fun getGasEstimate(from: String, to: String, amount: String, coinType: CoinType): String = "150"
    override fun isValidAddress(address: String, coinType: CoinType) = address.matches(Regex("^[13][a-km-zA-HJ-NP-Z1-9]{25,34}$|^(bc1)[a-zA-HJ-NP-Z0-9]{39,59}$"))
    override fun submitTransfer(privateKey: ByteArray, to: String, amount: String, coinType: CoinType): String? = null
}

class SolanaRpcClient(private val rpcUrl: String) : ChainRpcClient {

    private val client = SharedHttpClient.instance

    private fun post(body: JSONObject): JSONObject? {
        return try {
            val req = Request.Builder()
                .url(rpcUrl)
                .post(body.toString().toRequestBody(JSON_MEDIA))
                .build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return null
            JSONObject(resp.body!!.string())
        } catch (e: Exception) { null }
    }

    override fun getBalance(address: String, coinType: CoinType): String {
        val resp = post(JSONObject().apply {
            put("jsonrpc", "2.0"); put("id", 1); put("method", "getBalance")
            put("params", JSONArray().put(address))
        })
        val value = resp?.optJSONObject("result")?.optString("value") ?: return "0"
        return value
    }

    override fun getTransactionHistory(address: String, coinType: CoinType): List<CoinTransaction> = emptyList()
    override fun getGasEstimate(from: String, to: String, amount: String, coinType: CoinType): String = "5000"
    override fun isValidAddress(address: String, coinType: CoinType) = address.matches(Regex("^[1-9A-HJ-NP-Za-km-z]{32,44}$"))
    override fun submitTransfer(privateKey: ByteArray, to: String, amount: String, coinType: CoinType): String? = null
}
