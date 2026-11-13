package com.companycoin.wallet.crypto

import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.sec.SECNamedCurves
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.signers.ECDSASigner
import org.bouncycastle.crypto.signers.HMacDSAKCalculator
import java.math.BigInteger
import java.security.MessageDigest

/**
 * Minimal mainnet/testnet Bitcoin P2PKH transaction builder.
 *
 * Inputs are signed with legacy SIGHASH_ALL (the wallet's own address is
 * P2PKH). Outputs may be P2PKH, P2SH, P2WPKH, P2WSH, or P2TR scripts.
 */
object BitcoinTx {

    data class Utxo(val txid: String, val vout: Long, val value: Long)

    private const val SIGHASH_ALL = 1
    private const val SEQUENCE = 0xffffffffL
    private const val DUST = 546L

    private val SECP256K1: ECDomainParameters by lazy {
        val ec = SECNamedCurves.getByName("secp256k1")
        ECDomainParameters(ec.curve, ec.g, ec.n, ec.h)
    }
    private val CURVE_N: BigInteger get() = SECP256K1.n
    private val HALF_N: BigInteger get() = CURVE_N.shiftRight(1)

    fun parseAmountToSats(amount: String, decimals: Int = 8): Long {
        val value = java.math.BigDecimal(amount.trim())
        require(value.signum() > 0) { "Amount must be positive" }
        return value.movePointRight(decimals).toBigIntegerExact().longValueExact()
    }

    fun estimateVsize(inputs: Int, outputs: Int): Long = 10L + inputs * 148L + outputs * 34L

    /** Build and sign a transaction. Returns the raw transaction hex. */
    fun buildSignedTransaction(
        privateKey: ByteArray,
        fromAddress: String,
        toAddress: String,
        amountSats: Long,
        utxos: List<Utxo>,
        feeRate: Long,
        testnet: Boolean,
    ): String {
        require(amountSats > 0) { "Amount must be positive" }
        require(utxos.isNotEmpty()) { "No spendable UTXOs" }

        val fromScript = scriptPubKey(fromAddress, testnet)
            ?: throw ApiException(0, "Unsupported change address")
        require(fromScript.size == 25 && fromScript[0] == 0x76.toByte()) {
            "Change address must be P2PKH"
        }
        val toScript = scriptPubKey(toAddress, testnet)
            ?: throw ApiException(0, "Unsupported recipient address")

        val pubKey = HDWallet.compressedPublicKey(privateKey)
        val rate = if (feeRate > 0) feeRate else 10L

        var selected = mutableListOf<Utxo>()
        var total = 0L
        var change = 0L
        var useChange = false
        var index = 0
        while (true) {
            val count = selected.size
            val feeWithChange = estimateVsize(count, 2) * rate
            if (total >= amountSats + feeWithChange) {
                val candidate = total - amountSats - feeWithChange
                if (candidate >= DUST) {
                    change = candidate
                    useChange = true
                    break
                }
                val feeNoChange = estimateVsize(count, 1) * rate
                if (total >= amountSats + feeNoChange) {
                    change = 0
                    useChange = false
                    break
                }
            }
            if (index >= utxos.size) {
                throw ApiException(0, "Insufficient BTC balance for amount + fee")
            }
            val utxo = utxos[index++]
            selected.add(utxo)
            total += utxo.value
        }

        val outputs = mutableListOf<Pair<Long, ByteArray>>()
        outputs.add(amountSats to toScript)
        if (useChange && change >= DUST) outputs.add(change to fromScript)

        val signedScripts = selected.mapIndexed { i, _ ->
            val sighash = sighash(selected, outputs, i, fromScript)
            val signature = signDer(sighash, privateKey)
            pushData(signature + byteArrayOf(SIGHASH_ALL.toByte())) + pushData(pubKey)
        }

        val raw = serialize(selected, outputs, signedScripts)
        return LocalWallet.bytesToHex(raw)
    }

    fun txid(rawHex: String): String {
        val raw = LocalWallet.hexToBytes(rawHex)
        return LocalWallet.bytesToHex(doubleSha256(raw).reversedArray())
    }

    // ─── Scripts / address decoding ─────────────────────────────────

    fun scriptPubKey(address: String, testnet: Boolean): ByteArray? {
        val trimmed = address.trim()
        if (trimmed.startsWith("bc1", ignoreCase = true) || trimmed.startsWith("tb1", ignoreCase = true)) {
            return bech32Script(trimmed, testnet)
        }
        val decoded = base58CheckDecode(trimmed) ?: return null
        if (decoded.size != 21) return null
        val version = decoded[0].toInt() and 0xff
        val hash = decoded.copyOfRange(1, 21)
        val p2pkh = if (testnet) version == 0x6f else version == 0x00
        val p2sh = if (testnet) version == 0xc4 else version == 0x05
        return when {
            p2pkh -> byteArrayOf(
                0x76, 0xa9.toByte(), 0x14
            ) + hash + byteArrayOf(0x88.toByte(), 0xac.toByte())
            p2sh -> byteArrayOf(
                0xa9.toByte(), 0x14
            ) + hash + byteArrayOf(0x87.toByte())
            else -> null
        }
    }

    private fun base58CheckDecode(address: String): ByteArray? {
        val data = LocalWallet.base58Decode(address) ?: return null
        if (data.size < 5) return null
        val payload = data.copyOfRange(0, data.size - 4)
        val checksum = data.copyOfRange(data.size - 4, data.size)
        val expected = doubleSha256(payload).copyOfRange(0, 4)
        if (!checksum.contentEquals(expected)) return null
        return payload
    }

    private fun bech32Script(address: String, testnet: Boolean): ByteArray? {
        val lower = address.lowercase()
        val sep = lower.lastIndexOf('1')
        if (sep < 1 || sep + 7 > lower.length) return null
        val hrp = lower.substring(0, sep)
        if ((testnet && hrp != "tb") || (!testnet && hrp != "bc")) return null
        val dataPart = lower.substring(sep + 1)
        val charset = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
        val data = IntArray(dataPart.length) { charset.indexOf(dataPart[it]) }
        if (data.any { it < 0 }) return null
        if (!bech32Verify(hrp, data)) return null
        val payload = data.copyOfRange(0, data.size - 6)
        if (payload.isEmpty()) return null
        val witver = payload[0]
        if (witver > 16) return null
        val program = convertBits(payload.copyOfRange(1, payload.size), 5, 8, false) ?: return null
        if (program.size < 2 || program.size > 40) return null
        if (witver == 0 && program.size !in listOf(20, 32)) return null
        if (witver == 1 && program.size != 32) return null
        val opcode = if (witver == 0) 0x00 else 0x50 + witver
        return byteArrayOf(opcode.toByte(), program.size.toByte()) + program
    }

    private fun bech32Verify(hrp: String, data: IntArray): Boolean {
        val values = IntArray(hrp.length * 2 + 1 + data.size)
        var p = 0
        for (c in hrp) values[p++] = c.code shr 5
        values[p++] = 0
        for (c in hrp) values[p++] = c.code and 31
        for (d in data) values[p++] = d
        val polymod = bech32Polymod(values)
        return polymod == 1 || polymod == 0x2bc830a3
    }

    private fun bech32Polymod(values: IntArray): Int {
        val gen = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)
        var chk = 1
        for (v in values) {
            val top = chk ushr 25
            chk = ((chk and 0x1ffffff) shl 5) xor v
            for (i in 0 until 5) {
                if (((top ushr i) and 1) != 0) chk = chk xor gen[i]
            }
        }
        return chk
    }

    private fun convertBits(data: IntArray, from: Int, to: Int, pad: Boolean): ByteArray? {
        var acc = 0
        var bits = 0
        val out = mutableListOf<Byte>()
        val maxv = (1 shl to) - 1
        for (value in data) {
            if (value < 0 || value ushr from != 0) return null
            acc = (acc shl from) or value
            bits += from
            while (bits >= to) {
                bits -= to
                out.add(((acc ushr bits) and maxv).toByte())
            }
        }
        if (pad) {
            if (bits > 0) out.add(((acc shl (to - bits)) and maxv).toByte())
        } else if (bits >= from || ((acc shl (to - bits)) and maxv) != 0) {
            return null
        }
        return out.toByteArray()
    }

    // ─── Transaction serialization / signing ────────────────────────

    private fun sighash(
        inputs: List<Utxo>,
        outputs: List<Pair<Long, ByteArray>>,
        inputIndex: Int,
        scriptCode: ByteArray,
    ): ByteArray {
        val out = mutableListOf<Byte>()
        out.addAll(int32(1).toList())
        out.addAll(varInt(inputs.size.toLong()).toList())
        for ((i, utxo) in inputs.withIndex()) {
            out.addAll(LocalWallet.hexToBytes(utxo.txid).reversedArray().toList())
            out.addAll(int32(utxo.vout).toList())
            val script = if (i == inputIndex) scriptCode else ByteArray(0)
            out.addAll(varInt(script.size.toLong()).toList())
            out.addAll(script.toList())
            out.addAll(int32(SEQUENCE).toList())
        }
        out.addAll(varInt(outputs.size.toLong()).toList())
        for ((value, script) in outputs) {
            out.addAll(int64(value).toList())
            out.addAll(varInt(script.size.toLong()).toList())
            out.addAll(script.toList())
        }
        out.addAll(int32(0).toList())
        out.addAll(int32(SIGHASH_ALL.toLong()).toList())
        return doubleSha256(out.toByteArray())
    }

    private fun signDer(hash: ByteArray, privateKey: ByteArray): ByteArray {
        val signer = ECDSASigner(HMacDSAKCalculator(SHA256Digest()))
        signer.init(true, ECPrivateKeyParameters(BigInteger(1, privateKey), SECP256K1))
        val sig = signer.generateSignature(hash)
        var r = sig[0]
        var s = sig[1]
        if (s > HALF_N) s = CURVE_N.subtract(s)
        return DERSequence(arrayOf<ASN1Encodable>(ASN1Integer(r), ASN1Integer(s))).encoded
    }

    private fun serialize(
        inputs: List<Utxo>,
        outputs: List<Pair<Long, ByteArray>>,
        scripts: List<ByteArray>,
    ): ByteArray {
        val out = mutableListOf<Byte>()
        out.addAll(int32(1).toList())
        out.addAll(varInt(inputs.size.toLong()).toList())
        for ((i, utxo) in inputs.withIndex()) {
            out.addAll(LocalWallet.hexToBytes(utxo.txid).reversedArray().toList())
            out.addAll(int32(utxo.vout).toList())
            val script = scripts[i]
            out.addAll(varInt(script.size.toLong()).toList())
            out.addAll(script.toList())
            out.addAll(int32(SEQUENCE).toList())
        }
        out.addAll(varInt(outputs.size.toLong()).toList())
        for ((value, script) in outputs) {
            out.addAll(int64(value).toList())
            out.addAll(varInt(script.size.toLong()).toList())
            out.addAll(script.toList())
        }
        out.addAll(int32(0).toList())
        return out.toByteArray()
    }

    private fun pushData(data: ByteArray): ByteArray {
        require(data.size < 0x4c)
        return byteArrayOf(data.size.toByte()) + data
    }

    private fun varInt(value: Long): ByteArray = when {
        value < 0xfd -> byteArrayOf(value.toByte())
        value <= 0xffff -> byteArrayOf(0xfd.toByte()) + uint16(value)
        value <= 0xffffffffL -> byteArrayOf(0xfe.toByte()) + int32(value)
        else -> byteArrayOf(0xff.toByte()) + int64(value)
    }

    private fun uint16(value: Long): ByteArray =
        byteArrayOf((value and 0xff).toByte(), ((value ushr 8) and 0xff).toByte())

    private fun int32(value: Long): ByteArray = byteArrayOf(
        (value and 0xff).toByte(),
        ((value ushr 8) and 0xff).toByte(),
        ((value ushr 16) and 0xff).toByte(),
        ((value ushr 24) and 0xff).toByte(),
    )

    private fun int64(value: Long): ByteArray = ByteArray(8) {
        ((value ushr (8 * it)) and 0xff).toByte()
    }

    private fun doubleSha256(data: ByteArray): ByteArray {
        val sha = MessageDigest.getInstance("SHA-256")
        return sha.digest(sha.digest(data))
    }
}
