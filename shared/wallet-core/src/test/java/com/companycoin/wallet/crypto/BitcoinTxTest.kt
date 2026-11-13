package com.companycoin.wallet.crypto

import org.bitcoinj.core.Address
import org.bitcoinj.core.Transaction
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.script.Script
import org.bitcoinj.script.ScriptBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BitcoinTxTest {

    private val privateKey = LocalWallet.hexToBytes(
        "0000000000000000000000000000000000000000000000000000000000000001"
    )
    private val from = HDWallet.btcAddress(HDWallet.compressedPublicKey(privateKey), false)
    private val to = "1LqBGSKuX5yYUonjxT5qGfpUsXKYYWeabA"

    @Test
    fun `parses btc amounts`() {
        assertEquals(50_000L, BitcoinTx.parseAmountToSats("0.0005", 8))
        assertEquals(100_000_000L, BitcoinTx.parseAmountToSats("1", 8))
    }

    @Test
    fun `builds a p2pkh script`() {
        val script = BitcoinTx.scriptPubKey(to, false)
        assertNotNull(script)
        assertEquals(25, script!!.size)
        assertEquals(0x76.toByte(), script[0])
        assertEquals(0xa9.toByte(), script[1])
    }

    @Test
    fun `builds and signs a legacy p2pkh transaction`() {
        val utxo = BitcoinTx.Utxo(
            txid = "11".repeat(32),
            vout = 0,
            value = 100_000,
        )
        val raw = BitcoinTx.buildSignedTransaction(
            privateKey, from, to, 50_000, listOf(utxo), 10, false
        )
        assertTrue(raw.startsWith("01000000"))
        assertEquals(64, BitcoinTx.txid(raw).length)

        val params = MainNetParams.get()
        val tx = Transaction(params, LocalWallet.hexToBytes(raw))
        val fromAddress = Address.fromString(params, from)
        val scriptPubKey = ScriptBuilder.createOutputScript(fromAddress)
        for (i in tx.inputs.indices) {
            tx.inputs[i].scriptSig.correctlySpends(
                tx, i.toLong(), scriptPubKey, Script.ALL_VERIFY_FLAGS
            )
        }
        assertFalse(tx.outputs.isEmpty())
    }
}
