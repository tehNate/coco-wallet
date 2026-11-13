package com.companycoin.wallet.crypto

import org.bouncycastle.asn1.sec.SECNamedCurves
import org.bouncycastle.crypto.digests.KeccakDigest
import org.bouncycastle.crypto.digests.RIPEMD160Digest
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.math.ec.ECPoint
import java.math.BigInteger
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Deterministic multi-coin key derivation.
 *
 * CoCo uses Substrate's mnemonicToMiniSecret (PBKDF2 over entropy) and Ed25519.
 * BTC/ETH/USDC/USDT use standard BIP39 + BIP32 (secp256k1).
 * SOL uses standard BIP39 + SLIP-0010 (Ed25519, hardened path).
 *
 * The standard paths match MetaMask/Electrum/Phantom for the same mnemonic:
 *   BTC  m/44'/0'/0'/0/0
 *   ETH  m/44'/60'/0'/0/0
 *   SOL  m/44'/501'/0'/0'/0'
 */
object HDWallet {

    data class DerivedKey(
        val coinType: CoinType,
        val privateKey: ByteArray,
        val publicKey: ByteArray,
        val address: String,
        val derivationPath: String,
    )

    private const val HARDENED = 0x80000000L

    private val SECP256K1: ECDomainParameters by lazy {
        val ec = SECNamedCurves.getByName("secp256k1")
        ECDomainParameters(ec.curve, ec.g, ec.n, ec.h)
    }
    private val CURVE_N: BigInteger get() = SECP256K1.n

    fun deriveAllKeys(substrateSeed: ByteArray): Map<CoinType, DerivedKey> =
        deriveAllKeys(null, substrateSeed)

    fun deriveAllKeys(mnemonic: String?, substrateSeed: ByteArray): Map<CoinType, DerivedKey> {
        val map = mutableMapOf<CoinType, DerivedKey>()
        map[CoinType.COCO] = deriveCoco(substrateSeed)

        // A raw 32-byte Ed25519 seed has no BIP39 mnemonic, so standard
        // BTC/ETH/SOL derivation is unavailable for those wallets.
        if (mnemonic.isNullOrBlank()) return map

        val bip39Seed = LocalWallet.mnemonicToBip39Seed(mnemonic)
        val btc = deriveBtc(bip39Seed)
        val eth = deriveEth(bip39Seed)
        val sol = deriveSol(bip39Seed)
        map[CoinType.BITCOIN] = btc
        map[CoinType.ETHEREUM] = eth
        map[CoinType.SOLANA] = sol
        map[CoinType.USDC] = eth.copy(coinType = CoinType.USDC)
        map[CoinType.USDT] = eth.copy(coinType = CoinType.USDT)
        return map
    }

    fun deriveCoco(substrateSeed: ByteArray): DerivedKey {
        val privateKey = substrateSeed.copyOfRange(0, 32)
        val publicKey = Ed25519PrivateKeyParameters(privateKey, 0).generatePublicKey().encoded
        return DerivedKey(
            CoinType.COCO, privateKey, publicKey,
            LocalWallet.ss58Encode(publicKey, 42), "substrate"
        )
    }

    fun deriveBtc(bip39Seed: ByteArray): DerivedKey {
        val path = listOf(44L or HARDENED, 0L or HARDENED, 0L or HARDENED, 0L, 0L)
        val privateKey = bip32Derive(bip39Seed, path)
        val publicKey = compressedPublicKey(privateKey)
        return DerivedKey(
            CoinType.BITCOIN, privateKey, publicKey,
            btcAddress(publicKey, testnet = false),
            "m/44'/0'/0'/0/0"
        )
    }

    fun deriveEth(bip39Seed: ByteArray): DerivedKey {
        val path = listOf(44L or HARDENED, 60L or HARDENED, 0L or HARDENED, 0L, 0L)
        val privateKey = bip32Derive(bip39Seed, path)
        val publicKey = uncompressedPublicKey(privateKey)
        return DerivedKey(
            CoinType.ETHEREUM, privateKey, publicKey,
            ethAddress(publicKey), "m/44'/60'/0'/0/0"
        )
    }

    fun deriveSol(bip39Seed: ByteArray): DerivedKey {
        val path = listOf(
            44L or HARDENED, 501L or HARDENED, 0L or HARDENED,
            0L or HARDENED, 0L or HARDENED
        )
        val privateKey = slip10Derive(bip39Seed, path)
        val publicKey = Ed25519PrivateKeyParameters(privateKey, 0).generatePublicKey().encoded
        return DerivedKey(
            CoinType.SOLANA, privateKey, publicKey,
            LocalWallet.base58Encode(publicKey), "m/44'/501'/0'/0'/0'"
        )
    }

    fun btcAddress(publicKeyCompressed: ByteArray, testnet: Boolean = false): String {
        val sha = MessageDigest.getInstance("SHA-256").digest(publicKeyCompressed)
        val ripe = RIPEMD160Digest()
        ripe.update(sha, 0, sha.size)
        val hash160 = ByteArray(20)
        ripe.doFinal(hash160, 0)
        val version: Byte = if (testnet) 0x6f else 0x00
        val payload = byteArrayOf(version) + hash160
        val checksum = doubleSha256(payload).copyOfRange(0, 4)
        return LocalWallet.base58Encode(payload + checksum)
    }

    fun ethAddress(publicKey: ByteArray): String {
        val offset = if (publicKey.size == 65 && publicKey[0] == 0x04.toByte()) 1 else 0
        val digest = KeccakDigest(256)
        digest.update(publicKey, offset, publicKey.size - offset)
        val hash = ByteArray(32)
        digest.doFinal(hash, 0)
        return "0x" + LocalWallet.bytesToHex(hash.copyOfRange(12, 32))
    }

    fun compressedPublicKey(privateKey: ByteArray): ByteArray =
        publicPoint(privateKey).getEncoded(true)

    fun uncompressedPublicKey(privateKey: ByteArray): ByteArray =
        publicPoint(privateKey).getEncoded(false)

    private fun publicPoint(privateKey: ByteArray): ECPoint {
        val n = BigInteger(1, privateKey)
        require(n.signum() > 0 && n < CURVE_N) { "Invalid secp256k1 private key" }
        return SECP256K1.g.multiply(n)
    }

    // ─── BIP32 (secp256k1) ──────────────────────────────────────────

    private data class Bip32Node(val key: ByteArray, val chainCode: ByteArray)

    private fun bip32Master(seed: ByteArray): Bip32Node {
        val i = hmacSha512("Bitcoin seed".toByteArray(Charsets.US_ASCII), seed)
        return Bip32Node(i.copyOfRange(0, 32), i.copyOfRange(32, 64))
    }

    private fun bip32Child(node: Bip32Node, index: Long): Bip32Node {
        val hardened = (index and HARDENED) != 0L
        val data = if (hardened) {
            byteArrayOf(0) + node.key + ser32(index)
        } else {
            compressedPublicKey(node.key) + ser32(index)
        }
        val i = hmacSha512(node.chainCode, data)
        val il = BigInteger(1, i.copyOfRange(0, 32))
        val child = il.add(BigInteger(1, node.key)).mod(CURVE_N)
        require(child.signum() != 0) { "Invalid BIP32 child key" }
        return Bip32Node(to32(child), i.copyOfRange(32, 64))
    }

    private fun bip32Derive(seed: ByteArray, path: List<Long>): ByteArray {
        var node = bip32Master(seed)
        for (index in path) node = bip32Child(node, index)
        return node.key
    }

    // ─── SLIP-0010 (Ed25519) ────────────────────────────────────────

    private data class Slip10Node(val key: ByteArray, val chainCode: ByteArray)

    private fun slip10Master(seed: ByteArray): Slip10Node {
        val i = hmacSha512("ed25519 seed".toByteArray(Charsets.US_ASCII), seed)
        return Slip10Node(i.copyOfRange(0, 32), i.copyOfRange(32, 64))
    }

    private fun slip10Child(node: Slip10Node, index: Long): Slip10Node {
        val data = byteArrayOf(0) + node.key + ser32(index or HARDENED)
        val i = hmacSha512(node.chainCode, data)
        return Slip10Node(i.copyOfRange(0, 32), i.copyOfRange(32, 64))
    }

    private fun slip10Derive(seed: ByteArray, path: List<Long>): ByteArray {
        var node = slip10Master(seed)
        for (index in path) node = slip10Child(node, index)
        return node.key
    }

    // ─── Helpers ────────────────────────────────────────────────────

    private fun hmacSha512(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec(key, "HmacSHA512"))
        return mac.doFinal(data)
    }

    private fun ser32(index: Long): ByteArray = byteArrayOf(
        (index ushr 24).toByte(),
        (index ushr 16).toByte(),
        (index ushr 8).toByte(),
        index.toByte(),
    )

    private fun to32(value: BigInteger): ByteArray {
        val bytes = value.toByteArray()
        return when {
            bytes.size == 32 -> bytes
            bytes.size > 32 -> bytes.copyOfRange(bytes.size - 32, bytes.size)
            else -> ByteArray(32 - bytes.size) + bytes
        }
    }

    private fun doubleSha256(data: ByteArray): ByteArray {
        val sha = MessageDigest.getInstance("SHA-256")
        return sha.digest(sha.digest(data))
    }
}
