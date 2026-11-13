package com.companycoin.wallet.crypto

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.util.encoders.Base64
import java.math.BigDecimal

/** Solana SystemProgram.transfer transaction serialization. */
object SolanaTx {

    data class Built(
        val base64: String,
        val signatureBase58: String,
        val message: ByteArray,
    )

    private val SYSTEM_PROGRAM = ByteArray(32)

    fun parseAmountToLamports(amount: String): Long {
        val value = BigDecimal(amount.trim())
        require(value.signum() > 0) { "Amount must be positive" }
        return value.movePointRight(9).toBigIntegerExact().longValueExact()
    }

    fun publicKey(privateKey: ByteArray): ByteArray =
        Ed25519PrivateKeyParameters(privateKey, 0).generatePublicKey().encoded

    fun buildTransfer(
        privateKey: ByteArray,
        toPublicKey: ByteArray,
        lamports: Long,
        recentBlockhash: ByteArray,
    ): Built {
        require(toPublicKey.size == 32) { "Invalid Solana recipient" }
        require(recentBlockhash.size == 32) { "Invalid blockhash" }
        require(lamports > 0) { "Amount must be positive" }

        val fromPublicKey = publicKey(privateKey)
        val data = int32(2) + int64(lamports)

        val instruction = byteArrayOf(2) +          // system program index
            shortVec(2) + byteArrayOf(0, 1) +       // from, to
            shortVec(data.size) + data

        val message = byteArrayOf(1, 0, 1) +        // 1 signer, 0 readonly signed, 1 readonly unsigned
            shortVec(3) +
            fromPublicKey + toPublicKey + SYSTEM_PROGRAM +
            recentBlockhash +
            shortVec(1) + instruction

        val signature = LocalWallet.sign(privateKey, message)
        val transaction = shortVec(1) + signature + message
        return Built(
            base64 = Base64.toBase64String(transaction),
            signatureBase58 = LocalWallet.base58Encode(signature),
            message = message,
        )
    }

    fun shortVec(value: Int): ByteArray {
        var n = value
        val out = mutableListOf<Byte>()
        while (true) {
            var b = n and 0x7f
            n = n ushr 7
            if (n == 0) {
                out.add(b.toByte())
                break
            }
            out.add((b or 0x80).toByte())
        }
        return out.toByteArray()
    }

    private fun int32(value: Int): ByteArray = byteArrayOf(
        (value and 0xff).toByte(),
        ((value ushr 8) and 0xff).toByte(),
        ((value ushr 16) and 0xff).toByte(),
        ((value ushr 24) and 0xff).toByte(),
    )

    private fun int64(value: Long): ByteArray = ByteArray(8) {
        ((value ushr (8 * it)) and 0xff).toByte()
    }
}
