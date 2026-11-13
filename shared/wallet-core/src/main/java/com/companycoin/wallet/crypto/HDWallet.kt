package com.companycoin.wallet.crypto

import org.bouncycastle.crypto.digests.KeccakDigest
import org.bouncycastle.crypto.digests.RIPEMD160Digest
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.math.ec.ECPoint
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object HDWallet {

    data class DerivedKey(
        val coinType: CoinType,
        val privateKey: ByteArray,
        val publicKey: ByteArray,
        val address: String,
        val derivationPath: String,
    )

    private val SECP256K1_PARAMS by lazy {
        val ec = org.bouncycastle.asn1.sec.SECNamedCurves.getByName("secp256k1")
        org.bouncycastle.crypto.params.ECDomainParameters(ec.curve, ec.getG(), ec.n, ec.h)
    }

    fun deriveAllKeys(masterSeed: ByteArray): Map<CoinType, DerivedKey> {
        val map = mutableMapOf<CoinType, DerivedKey>()
        for (coin in CoinType.entries) {
            val key = deriveKey(masterSeed, coin) ?: continue
            map[coin] = key
        }
        return map
    }

    fun deriveKey(masterSeed: ByteArray, coin: CoinType): DerivedKey? {
        // COCO uses Substrate-native derivation: raw seed directly → Ed25519 keypair.
        // Other coins use BIP44 HD path (m/44'/coinType'/...).
        if (coin == CoinType.COCO) {
            return deriveEd25519(masterSeed, coin)
        }

        val path = coin.derivationPath
        val nodes = path.removePrefix("m/").split("/")
        var seed = masterSeed

        for (node in nodes) {
            val hardened = node.endsWith("'")
            val index = node.removeSuffix("'").toLong()
            val fullIndex = if (hardened) (index or 0x80000000L) else index
            seed = ckdPriv(seed, fullIndex.toInt())
        }

        return when (coin.curve) {
            "Ed25519" -> deriveEd25519(seed, coin)
            "SECP256k1" -> deriveSecp256k1(seed, coin)
            else -> null
        }
    }

    private fun ckdPriv(parentSeed: ByteArray, index: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA512")
        val data = ByteArray(37)
        data[0] = 0
        System.arraycopy(parentSeed, 0, data, 1, 32)
        val idxBytes = byteArrayOf(
            (index shr 24).toByte(), (index shr 16).toByte(),
            (index shr 8).toByte(), index.toByte()
        )
        System.arraycopy(idxBytes, 0, data, 33, 4)
        mac.init(SecretKeySpec(parentSeed, "HmacSHA512"))
        val hash = mac.doFinal(data)
        return hash.copyOfRange(32, 64)
    }

    private fun deriveEd25519(seed: ByteArray, coin: CoinType): DerivedKey {
        val privateKeyParams = Ed25519PrivateKeyParameters(seed.copyOfRange(0, 32), 0)
        val publicKeyParams = privateKeyParams.generatePublicKey()
        val rawPub = publicKeyParams.encoded
        val rawPriv = seed.copyOfRange(0, 32)
        val address = when (coin) {
            CoinType.COCO -> LocalWallet.ss58Encode(rawPub, 42)
            else -> LocalWallet.base58Encode(rawPub)
        }
        return DerivedKey(coin, rawPriv, rawPub, address, coin.derivationPath)
    }

    private fun deriveSecp256k1(seed: ByteArray, coin: CoinType): DerivedKey {
        val privateKeyBigInt = BigInteger(1, seed.copyOfRange(0, 32))
        val q: ECPoint = SECP256K1_PARAMS.g.multiply(privateKeyBigInt)

        val publicKeyBytes = q.getEncoded(true)
        val uncompr = q.getEncoded(false)

        val address = when {
            coin == CoinType.ETHEREUM || CoinType.isErc20(coin) -> {
                val digest = KeccakDigest(256)
                val hash = ByteArray(32)
                digest.update(uncompr, 0, uncompr.size)
                digest.doFinal(hash, 0)
                val addrBytes = hash.copyOfRange(12, 32)
                "0x" + addrBytes.joinToString("") { b -> String.format("%02x", b) }
            }
            coin == CoinType.BITCOIN -> {
                val sha256 = MessageDigest.getInstance("SHA-256")
                val hash = sha256.digest(publicKeyBytes)
                val ripeMd160 = RIPEMD160Digest()
                ripeMd160.update(hash, 0, hash.size)
                val hash160 = ByteArray(20)
                ripeMd160.doFinal(hash160, 0)
                val networkByte = byteArrayOf(0x00) + hash160
                val checksum = sha256.digest(sha256.digest(networkByte)).copyOfRange(0, 4)
                LocalWallet.base58Encode(networkByte + checksum)
            }
            else -> "0x" + publicKeyBytes.joinToString("") { b -> String.format("%02x", b) }
        }

        return DerivedKey(coin, seed.copyOfRange(0, 32), publicKeyBytes, address, coin.derivationPath)
    }
}
