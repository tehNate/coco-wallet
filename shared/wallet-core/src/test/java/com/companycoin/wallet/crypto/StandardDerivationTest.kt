package com.companycoin.wallet.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StandardDerivationTest {

    private val phrase =
        "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

    private fun keys(): Map<CoinType, HDWallet.DerivedKey> {
        val substrateSeed = LocalWallet.mnemonicToSeed(phrase)
        return HDWallet.deriveAllKeys(phrase, substrateSeed)
    }

    @Test
    fun `standard BIP44 addresses match known vectors`() {
        val k = keys()
        assertEquals("1LqBGSKuX5yYUonjxT5qGfpUsXKYYWeabA", k[CoinType.BITCOIN]!!.address)
        assertEquals(
            "0x9858effd232b4033e47d90003d41ec34ecaeda94",
            k[CoinType.ETHEREUM]!!.address.lowercase()
        )
        assertEquals(
            "B9sVeu4rJU12oUrUtzjc6BSNuEXdfvurZkdcaTVkP2LY",
            k[CoinType.SOLANA]!!.address
        )
        assertEquals(
            "5FM25N8HGtacre9TnWQGRK32d1kUp1Gs6Dd7YiYpSPZLjmj6",
            k[CoinType.COCO]!!.address
        )
    }

    @Test
    fun `ethereum address from private key one matches`() {
        val privateKey = LocalWallet.hexToBytes(
            "0000000000000000000000000000000000000000000000000000000000000001"
        )
        val publicKey = HDWallet.uncompressedPublicKey(privateKey)
        assertEquals(
            "0x7e5f4552091a69125d5dfcb7b8c2659029395bdf",
            HDWallet.ethAddress(publicKey).lowercase()
        )
    }

    @Test
    fun `btc testnet address uses testnet prefix`() {
        val btc = keys()[CoinType.BITCOIN]!!
        val testnet = HDWallet.btcAddress(btc.publicKey, testnet = true)
        assertTrue(testnet.startsWith("m") || testnet.startsWith("n"))
        assertFalse(testnet == btc.address)
    }

    @Test
    fun `raw seed wallets only expose coco`() {
        val substrateSeed = LocalWallet.mnemonicToSeed(phrase)
        val k = HDWallet.deriveAllKeys(null, substrateSeed)
        assertTrue(k.containsKey(CoinType.COCO))
        assertFalse(k.containsKey(CoinType.BITCOIN))
        assertFalse(k.containsKey(CoinType.ETHEREUM))
        assertFalse(k.containsKey(CoinType.SOLANA))
    }

    @Test
    fun `usdc and usdt share the ethereum key`() {
        val k = keys()
        val eth = k[CoinType.ETHEREUM]!!
        assertEquals(eth.address, k[CoinType.USDC]!!.address)
        assertEquals(eth.address, k[CoinType.USDT]!!.address)
    }
}
