package com.companycoin.wallet.crypto

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * Opt-in live checks.
 *
 *   MULTICHAIN_LIVE_TEST=1  read-only Sepolia / BTC testnet / Solana devnet RPCs
 *   SOLANA_LIVE_TEST=1 + SOLANA_TEST_PRIVATE_KEY=<64 hex>
 *                           real devnet send; the key must already be funded
 *                           via https://faucet.solana.com (the RPC faucet is closed)
 */
class LiveMultiChainTest {

    private val http = OkHttpClient.Builder()
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    @Test
    fun `solana devnet simulates a signed transfer`() {
        assumeTrue("set MULTICHAIN_LIVE_TEST=1", System.getenv("MULTICHAIN_LIVE_TEST") == "1")
        val rpc = "https://api.devnet.solana.com"
        val privateKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val to = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val blockhash = latestBlockhash(rpc)
        val built = SolanaTx.buildTransfer(privateKey, to, 1_000_000, blockhash)

        val response = rpcCall(
            rpc, "simulateTransaction",
            """["${built.base64}",{"encoding":"base64","sigVerify":true}]"""
        )
        val json = JSONObject(response)
        assertFalse("simulate error: ${json.opt("error")}", json.has("error"))
        assertTrue("simulate response: $response", json.has("result"))
        println("SOLANA DEVNET SIMULATE OK: $response")
    }

    @Test
    fun `solana devnet real send with funded key`() {
        val secret = System.getenv("SOLANA_TEST_PRIVATE_KEY")
        assumeTrue(
            "set SOLANA_LIVE_TEST=1 and a funded SOLANA_TEST_PRIVATE_KEY",
            System.getenv("SOLANA_LIVE_TEST") == "1" && !secret.isNullOrBlank()
        )
        val privateKey = LocalWallet.hexToBytes(secret!!)
        val toPrivateKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val to = LocalWallet.base58Encode(SolanaTx.publicKey(toPrivateKey))
        val rpc = "https://api.devnet.solana.com"
        val sol = SolanaRpcClient(rpc)
        val tx = sol.submitTransfer(privateKey, to, "0.001", CoinType.SOLANA)
        assertNotNull("transaction signature", tx)

        var received = "0"
        for (i in 0 until 30) {
            received = sol.getBalance(to, CoinType.SOLANA)
            if ((received.toLongOrNull() ?: 0L) >= 1_000_000L) break
            Thread.sleep(2_000)
        }
        assertTrue("recipient lamports: $received", (received.toLongOrNull() ?: 0L) >= 1_000_000L)
        println("SOLANA LIVE SEND OK to=$to tx=$tx received=$received")
    }

    @Test
    fun `read-only multichain RPCs respond`() {
        assumeTrue("set MULTICHAIN_LIVE_TEST=1", System.getenv("MULTICHAIN_LIVE_TEST") == "1")

        val eth = EthereumRpcClient("https://sepolia.drpc.org", true)
        assertTrue(eth.isValidAddress("0x9858EfFD232B4033E47d90003D41EC34EcaEda94", CoinType.ETHEREUM))
        val ethBalance = eth.getBalance(
            "0x9858EfFD232B4033E47d90003D41EC34EcaEda94", CoinType.ETHEREUM
        )
        assertTrue("eth balance: $ethBalance", ethBalance.toBigIntegerOrNull() != null)

        val btc = BitcoinRpcClient("https://mempool.space/testnet/api", true)
        assertTrue(btc.isValidAddress("mipcBbFg9gMiCh81Kj8tqqdgoZub1ZJRfn", CoinType.BITCOIN))
        assertFalse(btc.isValidAddress("1LqBGSKuX5yYUonjxT5qGfpUsXKYYWeabA", CoinType.BITCOIN))
        val btcBalance = btc.getBalance(
            "mipcBbFg9gMiCh81Kj8tqqdgoZub1ZJRfn", CoinType.BITCOIN
        )
        assertTrue("btc balance: $btcBalance", btcBalance.toBigIntegerOrNull() != null)

        val sol = SolanaRpcClient("https://api.devnet.solana.com")
        assertTrue(sol.isValidAddress("B9sVeu4rJU12oUrUtzjc6BSNuEXdfvurZkdcaTVkP2LY", CoinType.SOLANA))
        val solBalance = sol.getBalance(
            "B9sVeu4rJU12oUrUtzjc6BSNuEXdfvurZkdcaTVkP2LY", CoinType.SOLANA
        )
        assertTrue("sol balance: $solBalance", solBalance.toBigIntegerOrNull() != null)
    }

    private fun latestBlockhash(rpc: String): ByteArray {
        val response = rpcCall(
            rpc, "getLatestBlockhash",
            """[{"commitment":"confirmed"}]"""
        )
        val hash = JSONObject(response)
            .optJSONObject("result")
            ?.optJSONObject("value")
            ?.optString("blockhash")
            .orEmpty()
        require(hash.isNotBlank()) { "No blockhash: $response" }
        return LocalWallet.base58Decode(hash)
            ?: throw AssertionError("Bad blockhash: $hash")
    }

    private fun rpcCall(url: String, method: String, paramsJson: String): String {
        val body = """{"jsonrpc":"2.0","id":1,"method":"$method","params":$paramsJson}"""
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw AssertionError("RPC $url $method failed: ${response.code} $text")
            }
            return text
        }
    }
}
