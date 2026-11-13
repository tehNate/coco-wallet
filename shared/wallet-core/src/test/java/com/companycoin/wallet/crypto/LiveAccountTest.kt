package com.companycoin.wallet.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Live proof of the app's optional account wallet restore.
 *   COCO_TEST_ACCOUNT_EMAIL=... COCO_TEST_ACCOUNT_PASSWORD=...
 *   COCO_TEST_VAULT_ADDRESS=... COCO_TEST_VAULT_MNEMONIC=...
 */
class LiveAccountTest {

    private val base = System.getenv("COCO_TEST_BASE") ?: "https://cocoio.cc"

    @Test
    fun `login list and restore wallet`() {
        val email = System.getenv("COCO_TEST_ACCOUNT_EMAIL")
        val password = System.getenv("COCO_TEST_ACCOUNT_PASSWORD")
        val address = System.getenv("COCO_TEST_VAULT_ADDRESS")
        val expected = System.getenv("COCO_TEST_VAULT_MNEMONIC")
        assumeTrue("set account/vault env", !email.isNullOrBlank() && !password.isNullOrBlank() && !address.isNullOrBlank() && !expected.isNullOrBlank())

        val api = AuthApi(base)
        val session = api.login(email!!, password!!)
        assertTrue(session.token.isNotEmpty())

        // This is the same call WalletSync makes when a local wallet is linked
        // to the website account.
        api.saveWallet(session.token, address!!, "Live Account Test")

        val wallets = api.wallets(session.token)
        val target = wallets.firstOrNull { it.address == address }
        assertTrue("wallet not in account list", target != null)
        assertTrue("wallet has no backup", target!!.hasBackup)
        assertTrue("wallet is not app compatible", target.appCompatible)

        val restored = api.restore(session.token, address, password)
        assertEquals(expected, restored.mnemonic)
        val kp = LocalWallet.deriveKeyPair(LocalWallet.mnemonicToSeed(restored.mnemonic))
        assertEquals(address, kp.address)
        println("LIVE ACCOUNT RESTORE OK address=$address")
    }
}
