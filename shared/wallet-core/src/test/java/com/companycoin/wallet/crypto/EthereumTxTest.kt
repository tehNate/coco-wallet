package com.companycoin.wallet.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.web3j.crypto.TransactionDecoder
import java.math.BigInteger

class EthereumTxTest {

    private val privateKey = LocalWallet.hexToBytes(
        "0000000000000000000000000000000000000000000000000000000000000001"
    )

    @Test
    fun `derives the known ethereum address`() {
        assertEquals(
            "0x7e5f4552091a69125d5dfcb7b8c2659029395bdf",
            EthereumTx.addressFromPrivateKey(privateKey).lowercase()
        )
    }

    @Test
    fun `parses ether amounts`() {
        assertEquals(
            BigInteger("1500000000000000000"),
            EthereumTx.parseAmount("1.5", 18)
        )
        assertEquals(BigInteger("1500000"), EthereumTx.parseAmount("1.5", 6))
    }

    @Test
    fun `signs a native transfer with eip155`() {
        val to = "0x9858EfFD232B4033E47d90003D41EC34EcaEda94"
        val raw = EthereumTx.signTransfer(
            privateKey = privateKey,
            chainId = 1,
            nonce = BigInteger.ZERO,
            gasPrice = BigInteger.valueOf(1_000_000_000L),
            gasLimit = BigInteger.valueOf(21_000),
            to = to,
            value = BigInteger.ONE,
        )
        val decoded = TransactionDecoder.decode(raw)
        assertEquals(BigInteger.ZERO, decoded.nonce)
        assertEquals(BigInteger.valueOf(21_000), decoded.gasLimit)
        assertEquals(BigInteger.ONE, decoded.value)
        assertEquals(to.lowercase(), decoded.to.lowercase())
        assertTrue(raw.startsWith("0x"))
    }

    @Test
    fun `builds erc20 transfer data`() {
        val data = EthereumTx.erc20TransferData(
            "0x9858EfFD232B4033E47d90003D41EC34EcaEda94",
            BigInteger("1500000")
        )
        assertTrue(data.startsWith("0xa9059cbb"))
        assertEquals(138, data.length)
    }
}
