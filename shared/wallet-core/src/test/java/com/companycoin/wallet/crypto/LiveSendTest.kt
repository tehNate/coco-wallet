package com.companycoin.wallet.crypto

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.math.BigInteger

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
        val startPlanck = client.getBalance(from.address, CoinType.COCO)
            .toBigIntegerOrNull() ?: BigInteger.ZERO
        assertTrue(
            "from balance planck: $startPlanck",
            startPlanck >= BigInteger("100000000000") // 0.1 COCO
        )

        val tx = client.submitTransfer(from.privateKey, to.address, "0.1", CoinType.COCO)
        assertNotNull("tx hash", tx)

        var receivedPlanck = BigInteger.ZERO
        for (i in 0 until 30) {
            Thread.sleep(2000)
            receivedPlanck = client.getBalance(to.address, CoinType.COCO)
                .toBigIntegerOrNull() ?: BigInteger.ZERO
            if (receivedPlanck >= BigInteger("90000000000")) break // 0.09 COCO
        }
        assertTrue(
            "recipient balance planck: $receivedPlanck",
            receivedPlanck >= BigInteger("90000000000")
        )
        println("LIVE SEND OK from=${from.address} to=${to.address} tx=$tx received=$receivedPlanck planck")
        // Do not persist the recipient mnemonic; the proof only needs public data.
        val receipt = java.io.File("/tmp/opencode/app-send-receipt.json")
        receipt.writeText(
            """{"from":"${from.address}","to":"${to.address}","tx":"$tx","received_planck":"$receivedPlanck"}"""
        )
        receipt.setReadable(false, false)
        receipt.setReadable(true, true)
    }
}
