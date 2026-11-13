package com.companycoin.wallet.crypto

import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.util.encoders.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SolanaTxTest {

    private val privateKey = ByteArray(32) { 1 }
    private val to = ByteArray(32) { 2 }
    private val blockhash = ByteArray(32) { 3 }

    @Test
    fun `parses sol amounts`() {
        assertEquals(1_000_000L, SolanaTx.parseAmountToLamports("0.001"))
        assertEquals(1_000_000_000L, SolanaTx.parseAmountToLamports("1"))
    }

    @Test
    fun `encodes compact u16`() {
        assertArrayEquals(byteArrayOf(0x80.toByte(), 0x01), SolanaTx.shortVec(128))
        assertArrayEquals(byteArrayOf(0x7f), SolanaTx.shortVec(127))
    }

    @Test
    fun `builds and signs a system transfer`() {
        val built = SolanaTx.buildTransfer(privateKey, to, 1_000_000, blockhash)
        assertTrue(built.base64.isNotBlank())

        val raw = Base64.decode(built.base64)
        assertEquals(1, raw[0].toInt())
        val signature = raw.copyOfRange(1, 65)
        assertArrayEquals(
            LocalWallet.base58Decode(built.signatureBase58),
            signature
        )
        val message = raw.copyOfRange(65, raw.size)
        assertArrayEquals(byteArrayOf(1, 0, 1), message.copyOfRange(0, 3))

        val publicKey = SolanaTx.publicKey(privateKey)
        val verifier = Ed25519Signer()
        verifier.init(false, Ed25519PublicKeyParameters(publicKey, 0))
        verifier.update(message, 0, message.size)
        assertTrue(verifier.verifySignature(signature))
    }
}
