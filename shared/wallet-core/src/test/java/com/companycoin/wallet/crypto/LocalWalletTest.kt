package com.companycoin.wallet.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalWalletTest {

    private val phrase = "bottom drive obey lake curtain smoke basket hold race lonely fit walk"
    private val abandon = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

    @Test
    fun `derivation matches polkadot-js mnemonicToMiniSecret + ed25519`() {
        val seed = LocalWallet.mnemonicToSeed(phrase)
        assertEquals(32, seed.size)
        val kp = LocalWallet.deriveKeyPair(seed)
        assertEquals("5DFJF7tY4bpbpcKPJcBTQaKuCDEPCpiz8TRjpmLeTtweqmXL", kp.address)
    }

    @Test
    fun `abandon vector matches`() {
        val kp = LocalWallet.deriveKeyPair(LocalWallet.mnemonicToSeed(abandon))
        assertEquals("5FM25N8HGtacre9TnWQGRK32d1kUp1Gs6Dd7YiYpSPZLjmj6", kp.address)
    }

    @Test
    fun `ed25519 sign and verify round trip`() {
        val seed = LocalWallet.mnemonicToSeed(phrase)
        val kp = LocalWallet.deriveKeyPair(seed)
        val msg = "coco-offline-vote".toByteArray()
        val sig = LocalWallet.sign(kp.privateKey, msg)
        assertEquals(64, sig.size)
        assertTrue(LocalWallet.verify(kp.publicKey, msg, sig))
        assertFalse(LocalWallet.verify(kp.publicKey, "different".toByteArray(), sig))
    }

    @Test
    fun `mnemonic validation`() {
        assertTrue(LocalWallet.isValidMnemonic(phrase))
        assertTrue(LocalWallet.isValidMnemonic(abandon))
        assertFalse(LocalWallet.isValidMnemonic("abandon abandon abandon"))
        assertFalse(LocalWallet.isValidMnemonic("bottom drive obey lake curtain smoke basket hold race lonely fit zero"))
    }

    @Test
    fun `generated mnemonic derives a real ss58 address`() {
        val m = LocalWallet.generateMnemonic()
        assertTrue(LocalWallet.isValidMnemonic(m))
        val kp = LocalWallet.deriveKeyPair(LocalWallet.mnemonicToSeed(m))
        assertTrue(kp.address.startsWith("5"))
        assertEquals(32, kp.publicKey.size)
        assertEquals(32, kp.privateKey.size)
    }

    @Test
    fun `ss58 decode validates checksums and rejects the pre-1_9 encoding`() {
        assertTrue(LocalWallet.isValidAddress("5DFJF7tY4bpbpcKPJcBTQaKuCDEPCpiz8TRjpmLeTtweqmXL"))
        assertTrue(LocalWallet.isValidAddress("5FM25N8HGtacre9TnWQGRK32d1kUp1Gs6Dd7YiYpSPZLjmj6"))
        // Same public key, checksum computed without the SS58PRE prefix (app <= 1.8).
        assertFalse(LocalWallet.isValidAddress("5DFJF7tY4bpbpcKPJcBTQaKuCDEPCpiz8TRjpmLeTtweqYxD"))
        assertFalse(LocalWallet.isValidAddress("5DFJF7tY4bpbpcKPJcBTQaKuCDEPCpiz8TRjpmLeTtweq"))
        assertFalse(LocalWallet.isValidAddress("not-an-address"))
        assertEquals(32, LocalWallet.ss58Decode("5DFJF7tY4bpbpcKPJcBTQaKuCDEPCpiz8TRjpmLeTtweqmXL")!!.size)
    }

    @Test
    fun `generated wallets are unique and checksum-valid`() {
        val addresses = (1..25).map {
            val mnemonic = LocalWallet.generateMnemonic()
            assertTrue(LocalWallet.isValidMnemonic(mnemonic))
            LocalWallet.deriveKeyPair(LocalWallet.mnemonicToSeed(mnemonic)).address
        }
        assertEquals(25, addresses.toSet().size)
        addresses.forEach { assertTrue(it, LocalWallet.isValidAddress(it)) }
    }

    @Test
    fun `raw seed round trip`() {
        val seed = LocalWallet.mnemonicToSeed(phrase)
        val kp = LocalWallet.deriveKeyPair(seed)
        val fromHex = LocalWallet.fromSeed(seed.joinToString("") { String.format("%02x", it) })
        assertArrayEquals(kp.publicKey, fromHex.publicKey)
        assertEquals(kp.address, fromHex.address)
    }
}
