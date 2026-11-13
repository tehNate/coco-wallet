package com.companycoin.wallet.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Live read-only checks against the real beta backend.
 * Enable with: COCO_LIVE_TEST=1 ./gradlew :app:testDebugUnitTest
 */
class LiveApiTest {

    private val base = System.getenv("COCO_TEST_BASE") ?: "https://cocoio.cc"

    private fun live() = assumeTrue("set COCO_LIVE_TEST=1", System.getenv("COCO_LIVE_TEST") == "1")

    @Test
    fun `mainnet elections load over https`() {
        live()
        val api = VotingApi(base, null, "/api/voting/mainnet")
        val elections = api.getElections(0, 100)
        assertTrue("expected elections", elections.isNotEmpty())
        assertTrue(elections.any { it.status == "Finalized" })
    }

    @Test
    fun `mainnet results load`() {
        live()
        val api = VotingApi(base, null, "/api/voting/mainnet")
        val results = api.getResults(2)
        assertTrue("expected results for election 2", results.isNotEmpty())
    }

    @Test
    fun `mainnet balance bridge works`() {
        live()
        val client = CoCoChainClient(base, null)
        val alice = "5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY"
        val bal = client.getBalance(alice, CoinType.COCO)
        assertTrue("balance: $bal", bal.toDouble() > 0.0)
    }

    @Test
    fun `registry status is reachable`() {
        live()
        val api = ClaimApi(base)
        val alice = "5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY"
        val st = api.status(alice)
        assertEquals(false, st.registered)
    }
}
