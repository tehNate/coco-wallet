package com.companycoin.wallet.crypto

import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class VotingApi(private val serverUrl: String, private val authToken: String? = null, private val pathPrefix: String = "/api/voting") {

    private val client = SharedHttpClient.instance

    data class Election(
        val id: Int,
        val assetId: Int,
        val name: String,
        val voteType: String,
        val options: List<String>,
        val startBlock: Int,
        val endBlock: Int,
        val status: String,
        val totalVotes: Int,
        val tokenPrice: Int,
    )

    data class VoteResult(
        val option: String,
        val count: Int,
        val pct: Int,
    )

    data class CastPayload(
        val payloadHex: String,
        val methodHex: String,
        val nonce: Int,
        val blockNumber: Int,
        val blockHash: String,
        val genesisHash: String,
    )

    private fun get(path: String): JSONObject {
        val req = Request.Builder()
            .url("$serverUrl$path")
            .get()
            .apply { authToken?.let { header("Authorization", "Bearer $it") } }
            .build()
        return req.executeJson(client)
    }

    private fun post(path: String, body: JSONObject): JSONObject {
        val req = Request.Builder()
            .url("$serverUrl$path")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .apply { authToken?.let { header("Authorization", "Bearer $it") } }
            .build()
        return req.executeJson(client)
    }

    /** One-request election listing (GET /api/voting/mainnet/elections?offset=&limit=). */
    fun getElections(offset: Int = 0, limit: Int = 100): List<Election> {
        val resp = get("$pathPrefix/elections?offset=$offset&limit=$limit")
        val arr = resp.optJSONArray("elections") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val e = arr.getJSONObject(i)
            Election(
                id = e.getInt("id"),
                assetId = e.getInt("assetId"),
                name = e.getString("name"),
                voteType = e.getString("voteType"),
                options = e.getJSONArray("options").let { opts ->
                    (0 until opts.length()).map { opts.getString(it) }
                },
                startBlock = e.getInt("startBlock"),
                endBlock = e.getInt("endBlock"),
                status = e.getString("status"),
                totalVotes = e.getInt("totalVotes"),
                tokenPrice = e.getInt("tokenPrice"),
            )
        }
    }

    fun getElection(electionId: Int): Election {
        val resp = get("$pathPrefix/election/$electionId")
        return Election(
            id = resp.getInt("id"),
            assetId = resp.getInt("assetId"),
            name = resp.getString("name"),
            voteType = resp.getString("voteType"),
            options = resp.getJSONArray("options").let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            },
            startBlock = resp.getInt("startBlock"),
            endBlock = resp.getInt("endBlock"),
            status = resp.getString("status"),
            totalVotes = resp.getInt("totalVotes"),
            tokenPrice = resp.getInt("tokenPrice"),
        )
    }

    /** Weighted-governance metadata (rules, snapshot, weighted flag). */
    fun getGovernanceElection(electionId: Int): JSONObject =
        get("/api/governance/election/$electionId")

    fun getResults(electionId: Int): List<VoteResult> {
        val resp = get("$pathPrefix/results/$electionId")
        val arr = resp.optJSONArray("pct") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            VoteResult(
                option = obj.getString("option"),
                count = obj.getInt("count"),
                pct = obj.getInt("pct"),
            )
        }
    }

    /**
     * Offline-signed vote, step 1: ask the server to frame the exact
     * voting.castVote SignerPayload. The mnemonic never leaves the device.
     */
    fun prepareCast(electionId: Int, optionIndex: Int, address: String): CastPayload {
        val body = JSONObject().apply {
            put("electionId", electionId)
            put("optionIndex", optionIndex)
            put("address", address)
        }
        val resp = post("$pathPrefix/prepare-cast", body)
        return CastPayload(
            payloadHex = resp.getString("payload"),
            methodHex = resp.optString("methodHex"),
            nonce = resp.optInt("nonce", 0),
            blockNumber = resp.optInt("blockNumber", 0),
            blockHash = resp.optString("blockHash"),
            genesisHash = resp.optString("genesisHash"),
        )
    }

    /** Offline-signed vote, step 2: submit the on-device Ed25519 signature. */
    fun submitCast(
        electionId: Int,
        optionIndex: Int,
        address: String,
        publicKey: ByteArray,
        signature: ByteArray,
    ): JSONObject {
        val body = JSONObject().apply {
            put("electionId", electionId)
            put("optionIndex", optionIndex)
            put("address", address)
            put("publicKey", publicKey.joinToString("") { String.format("%02x", it) })
            put("signature", signature.joinToString("") { String.format("%02x", it) })
        }
        return post("$pathPrefix/submit-cast", body)
    }
}
