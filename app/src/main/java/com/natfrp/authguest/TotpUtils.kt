package com.natfrp.authguest

import org.apache.commons.codec.binary.Base32
import java.time.Instant
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.and
import kotlin.math.pow

class TotpUtils(
    digest: String,
    secretArg: String,
    private val digits: Int = 6,
    private val period: Int = 30
) {
    private val algorithm = Mac.getInstance("Hmac${digest.uppercase()}")

    init {
        require(digits in 6..8 && period > 0)
        val encoded = secretArg.filterNot { it.isWhitespace() }.uppercase()
        val base32 = Base32()
        require(encoded.isNotEmpty() && base32.isInAlphabet(encoded))
        val secret = base32.decode(encoded)
        algorithm.init(SecretKeySpec(secret, "Hmac${digest.uppercase()}"))
    }

    fun genCode(epochSeconds: Long = Instant.now().epochSecond): String {
        var counter = epochSeconds / period
        val data = ByteArray(8)
        var i = 8
        while (i-- > 0) {
            data[i] = counter.toByte()
            counter = counter ushr 8
        }
        val hash = algorithm.doFinal(data)
        val offset: Int = (hash[hash.size - 1] and 0xF).toInt()
        var truncatedHash: Long = 0
        for (index in 0..3) {
            truncatedHash = truncatedHash shl 8
            truncatedHash = truncatedHash or (hash[offset + index].toInt() and 0xFF).toLong()
        }
        truncatedHash = truncatedHash and 0x7FFFFFFFL
        truncatedHash %= 10.0.pow(digits.toDouble()).toLong()
        return String.format(Locale.ROOT, "%0${digits}d", truncatedHash)
    }
}
