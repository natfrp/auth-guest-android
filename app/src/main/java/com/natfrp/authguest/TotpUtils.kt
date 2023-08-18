package com.natfrp.authguest

import org.apache.commons.codec.binary.Base32
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.and
import kotlin.math.floor
import kotlin.math.pow

class TotpUtils(
    digest: String,
    secretArg: String,
    private val digits: Int = 6,
    private val period: Int = 30
) {
    private val algorithm = Mac.getInstance("Hmac${digest.uppercase()}")

    init {
        val secret = Base32().decode(secretArg.uppercase())
        algorithm.init(SecretKeySpec(secret, "Hmac${digest.uppercase()}"))
    }

    fun genCode(): String {
        var counter = floor(Instant.now().epochSecond.toDouble() / period.toDouble()).toLong()
        val data = ByteArray(8)
        var i = 8
        while (i-- > 0) {
            data[i] = counter.toByte()
            counter = counter ushr 8
        }
        val hash = algorithm.doFinal(data)
        val offset: Int = (hash[hash.size - 1] and 0xF).toInt()
        var truncatedHash: Long = 0
        for (i in 0..3) {
            truncatedHash = truncatedHash shl 8
            truncatedHash = truncatedHash or (hash[offset + i] and 0xFF.toByte()).toLong()
        }
        truncatedHash = truncatedHash and 0x7FFFFFFFL
        truncatedHash %= 10.0.pow(digits.toDouble()).toLong()
        return String.format("%0${digits}d", truncatedHash)
    }
}
