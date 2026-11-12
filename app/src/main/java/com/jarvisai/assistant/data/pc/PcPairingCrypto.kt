package com.jarvisai.assistant.data.pc

import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Connect to PC pairing: SPAKE2 cryptographic handshake (RFC 3526 2048-bit MODP group 14)
 * Allows pairing phone with desktop PC via 6-digit PIN without leaking code across network.
 */
object PcPairingCrypto {

    const val LABEL = "antigem-pair-v1"

    private val P = BigInteger(
        ("FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B139B22514A08798E3404DD" +
            "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED" +
            "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3DC2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F" +
            "83655D23DCA3AD961C62F356208552BB9ED529077096966D670C354E4ABC9804F1746C08CA18217C32905E462E36CE3B" +
            "E39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9DE2BCBF6955817183995497CEA956AE515D2261898FA0510" +
            "15728E5A8AACAA68FFFFFFFFFFFFFFFF"),
        16
    )
    private val Q = P.subtract(BigInteger.ONE).shiftRight(1)
    private val G = BigInteger.valueOf(2)
    private const val ELEMENT_BYTES = 256

    private val M = hashToGroup("$LABEL M")
    private val N = hashToGroup("$LABEL N")

    private val random = SecureRandom()

    private fun sha256(vararg parts: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        parts.forEach { md.update(it) }
        return md.digest()
    }

    private fun hashToGroup(seed: String): BigInteger {
        val out = java.io.ByteArrayOutputStream()
        for (i in 0..8) out.write(sha256(seed.toByteArray(Charsets.UTF_8), byteArrayOf(i.toByte())))
        val v = BigInteger(1, out.toByteArray()).mod(P)
        return v.modPow(BigInteger.valueOf(2), P)
    }

    fun passwordScalar(code: String): BigInteger {
        val w = BigInteger(1, sha256("$LABEL w".toByteArray(Charsets.UTF_8), code.trim().toByteArray(Charsets.UTF_8))).mod(Q)
        return if (w.signum() == 0) BigInteger.ONE else w
    }

    fun toBytes(v: BigInteger): ByteArray {
        val raw = v.toByteArray()
        val out = ByteArray(ELEMENT_BYTES)
        val src = if (raw.size > ELEMENT_BYTES) raw.copyOfRange(raw.size - ELEMENT_BYTES, raw.size) else raw
        System.arraycopy(src, 0, out, ELEMENT_BYTES - src.size, src.size)
        return out
    }

    fun toHex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }

    fun fromHex(s: String): ByteArray {
        val t = s.trim()
        require(t.length % 2 == 0 && t.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) { "not hex" }
        return ByteArray(t.length / 2) { t.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    private fun checkElement(v: BigInteger) {
        require(v > BigInteger.ONE && v < P.subtract(BigInteger.ONE)) { "bad pairing value" }
        require(v.modPow(Q, P) == BigInteger.ONE) { "bad pairing value" }
    }

    class PhoneSide(code: String, private val phoneId: String) {
        private val w = passwordScalar(code)
        private val x: BigInteger = run {
            var r: BigInteger
            do { r = BigInteger(Q.bitLength(), random) } while (r.signum() == 0 || r >= Q)
            r
        }

        val xHex: String = toHex(toBytes(G.modPow(x, P).multiply(M.modPow(w, P)).mod(P)))

        fun finish(yHex: String): HandshakeKey {
            val yBytes = fromHex(yHex)
            val yVal = BigInteger(1, yBytes)
            checkElement(yVal)

            val z = yVal.multiply(N.modPow(w.negate().mod(Q), P)).modPow(x, P)

            val ttStream = java.io.ByteArrayOutputStream()
            fun writePart(bytes: ByteArray) {
                ttStream.write(ByteBuffer.allocate(4).putInt(bytes.size).array())
                ttStream.write(bytes)
            }
            writePart(LABEL.toByteArray(Charsets.UTF_8))
            writePart(phoneId.toByteArray(Charsets.UTF_8))
            writePart(fromHex(xHex))
            writePart(yBytes)
            writePart(toBytes(z))
            writePart(toBytes(w))
            val tt = ttStream.toByteArray()

            val k = sha256(tt)
            fun hmac(label: String): ByteArray {
                val mac = Mac.getInstance("HmacSHA256")
                mac.init(SecretKeySpec(k, "HmacSHA256"))
                return mac.doFinal(label.toByteArray(Charsets.UTF_8))
            }

            val encKey = hmac("enc")
            val phoneConfirm = hmac("phone-confirm")
            val pcConfirm = hmac("pc-confirm")

            return HandshakeKey(encKey, phoneConfirm, pcConfirm)
        }
    }

    data class HandshakeKey(
        val encKey: ByteArray,
        val phoneConfirm: ByteArray,
        val pcConfirm: ByteArray
    ) {
        fun decryptBox(nonceAndCipherBase64: String): ByteArray {
            val raw = android.util.Base64.decode(nonceAndCipherBase64, android.util.Base64.NO_WRAP)
            require(raw.size >= 12 + 16) { "ciphertext too short" }
            val nonce = raw.copyOfRange(0, 12)
            val cipherBytes = raw.copyOfRange(12, raw.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(encKey, "AES")
            val gcmSpec = GCMParameterSpec(128, nonce)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
            cipher.updateAAD(LABEL.toByteArray(Charsets.UTF_8))
            return cipher.doFinal(cipherBytes)
        }
    }
}
