package com.companycoin.wallet.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * One-shot live offline-vote proof using the real app code.
 * Requires a funded voter wallet + an active election:
 *   COCO_TEST_MNEMONIC=... COCO_TEST_ELECTION=3 COCO_LIVE_TEST=1 ./gradlew ...
 */
class LiveVoteCastTest {

    private val base = System.getenv("COCO_TEST_BASE") ?: "https://cocoio.cc"

    @Test
    fun `prepare sign submit cast`() {
        val mnemonic = System.getenv("COCO_TEST_MNEMONIC")
        val election = System.getenv("COCO_TEST_ELECTION")?.toIntOrNull()
        assumeTrue("set COCO_TEST_MNEMONIC and COCO_TEST_ELECTION", !mnemonic.isNullOrBlank() && election != null)

        val kp = LocalWallet.deriveKeyPair(LocalWallet.mnemonicToSeed(mnemonic!!))
        val api = VotingApi(base, null, "/api/voting/mainnet")

        val prep = api.prepareCast(election!!, 0, kp.address)
        assertTrue("payload", prep.payloadHex.length > 100)
        val payload = ByteArray(prep.payloadHex.removePrefix("0x").length / 2) {
            prep.payloadHex.removePrefix("0x").substring(it * 2, it * 2 + 2).toInt(16).toByte()
        }
        val sig = LocalWallet.sign(kp.privateKey, payload)
        assertEquals(64, sig.size)

        val out = api.submitCast(election, 0, kp.address, kp.publicKey, sig)
        assertEquals(kp.address, out.optString("voter"))
        assertTrue("tx hash", out.optString("txHash").startsWith("0x"))
        println("LIVE CAST OK voter=${kp.address} tx=${out.optString("txHash")} block=${out.optInt("blockNumber")}")
    }
}
