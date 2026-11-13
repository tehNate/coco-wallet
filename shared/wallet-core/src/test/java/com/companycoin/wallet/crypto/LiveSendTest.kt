package com.companycoin.wallet.crypto

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * One-shot live COCO send using the real app wallet code (offline Ed25519 sign
 * + server relay). Requires a funded wallet:
 *   COCO_TEST_MNEMONIC=... COCO_LIVE_TEST=1 ./gradlew ...
 */
class LiveSendTest {

    private val base = System.getenv("COCO_TEST_BASE") ?: "https://cocoio.cc"

    @Test
    fun `send coco on mainnet`() {
        val mnemonic = System.getenv("COCO_TEST_MNEMONIC")
        assumeTrue("set COCO_TEST_MNEMONIC", !mnemonic.isNullOrBlank())

        val from = LocalWallet.deriveKeyPair(LocalWallet.mnemonicToSeed(mnemonic!!))
        val toPhrase = LocalWallet.generateMnemonic()
        val to = LocalWallet.deriveKeyPair(LocalWallet.mnemonicToSeed(toPhrase))

        val client = CoCoChainClient(base, null)
        val start = client.getBalance(from.address, CoinType.COCO)
        assertTrue("from balance: $start", start.toDouble() >= 0.1)

        val tx = client.submitTransfer(from.privateKey, to.address, "0.1", CoinType.COCO)
        assertNotNull("tx hash", tx)

        var received = "0"
        for (i in 0 until 30) {
            Thread.sleep(2000)
            received = client.getBalance(to.address, CoinType.COCO)
            if (received.toDouble() >= 0.09) break
        }
        assertTrue("recipient balance: $received", received.toDouble() >= 0.09)
        println("LIVE SEND OK from=${from.address} to=${to.address} tx=$tx received=$received")
        // Do not persist the recipient mnemonic; the proof only needs public data.
        val receipt = java.io.File("/tmp/opencode/app-send-receipt.json")
        receipt.writeText(
            """{"from":"${from.address}","to":"${to.address}","tx":"$tx","received":"$received"}"""
        )
        receipt.setReadable(false, false)
        receipt.setReadable(true, true)
    }
}
