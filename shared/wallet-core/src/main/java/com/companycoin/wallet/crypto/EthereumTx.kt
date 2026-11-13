package com.companycoin.wallet.crypto

import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.utils.Numeric
import java.math.BigDecimal
import java.math.BigInteger

/** Ethereum/ERC-20 transaction encoding and EIP-155 signing. */
object EthereumTx {

    fun parseAmount(amount: String, decimals: Int): BigInteger {
        val value = BigDecimal(amount.trim())
        require(value.signum() > 0) { "Amount must be positive" }
        return value.movePointRight(decimals).toBigIntegerExact()
    }

    fun addressFromPrivateKey(privateKey: ByteArray): String {
        val credentials = Credentials.create(LocalWallet.bytesToHex(privateKey))
        return "0x" + credentials.address.removePrefix("0x")
    }

    fun erc20TransferData(to: String, amount: BigInteger): String {
        val clean = to.removePrefix("0x").lowercase()
        require(clean.length == 40 && clean.all { it in "0123456789abcdef" }) {
            "Invalid ERC-20 recipient"
        }
        require(amount.signum() > 0) { "Amount must be positive" }
        val paddedTo = clean.padStart(64, '0')
        val paddedAmount = amount.toString(16).padStart(64, '0')
        return "0xa9059cbb$paddedTo$paddedAmount"
    }

    fun signTransfer(
        privateKey: ByteArray,
        chainId: Long,
        nonce: BigInteger,
        gasPrice: BigInteger,
        gasLimit: BigInteger,
        to: String,
        value: BigInteger,
        data: String = "0x",
    ): String {
        val cleanTo = to.removePrefix("0x")
        require(cleanTo.length == 40 && cleanTo.all { it in "0123456789abcdefABCDEF" }) {
            "Invalid Ethereum address"
        }
        val credentials = Credentials.create(LocalWallet.bytesToHex(privateKey))
        val raw = RawTransaction.createTransaction(
            nonce, gasPrice, gasLimit, "0x$cleanTo", value, data
        )
        val signed = TransactionEncoder.signMessage(raw, chainId, credentials)
        return Numeric.toHexString(signed)
    }
}
