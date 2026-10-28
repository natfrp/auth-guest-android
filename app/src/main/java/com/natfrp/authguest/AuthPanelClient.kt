package com.natfrp.authguest

import android.annotation.SuppressLint
import okhttp3.ConnectionSpec
import okhttp3.Dns
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.TlsVersion
import okio.BufferedSink
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.Socket
import java.net.URI
import java.net.UnknownHostException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

internal sealed class AuthResult {
    data class Success(val destination: String? = null) : AuthResult()
    data class Failure(val reason: String) : AuthResult()
}

/** Protocol reference: https://github.com/natfrp/auth-guest/blob/master/authpass.go */
internal class AuthPanelClient(private val client: OkHttpClient = createClient()) {
    fun authenticate(data: AuthRequest, totpCode: () -> String?): AuthResult {
        val url = authUrl(data.addr, data.port)
            ?: return AuthResult.Failure("Url 不合法")
        // Leave time for finishing the widget broadcast (goAsync has a limited lifetime).
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8)
        var session = client
        val getRequest = Request.Builder().url(url).get().build()
        val page = try {
            get(session, getRequest, deadline)
        } catch (first: IOException) {
            if (!canRetryWithoutSni(url)) throw first
            session = withoutSni(url.host)
            try {
                get(session, getRequest, deadline)
            } catch (retry: IOException) {
                retry.addSuppressed(first)
                throw retry
            }
        }

        val fields = AuthPanelHtml.formFields(page.body)
            ?: return AuthResult.Failure(
                when {
                    page.server == AUTH_SERVER -> "无法解析认证页面，请更新程序或使用网页认证"
                    page.code !in 200..299 -> "获取认证页面失败, HTTP ${page.code}"
                    else -> "不是访问认证页面，可能已经通过认证"
                }
            )
        val code = try {
            totpCode()
        } catch (_: Exception) {
            return AuthResult.Failure("TOTP 配置错误，请检查密钥、算法、位数和周期")
        }
        val form = FormBody.Builder()
            .add("csrf", fields.csrf)
            .add("ip", fields.ip)
            .add("pw", data.pw)
            .apply {
                if (data.persist) add("persist_auth", "on")
                if (code != null) add("totp", code)
            }.build()
        val oneShotForm = object : RequestBody() {
            override fun contentType() = form.contentType()
            override fun contentLength() = form.contentLength()
            override fun writeTo(sink: BufferedSink) = form.writeTo(sink)
            override fun isOneShot() = true
        }
        val request = Request.Builder().url(page.url).post(oneShotForm).build()
        // A redirect is an authentication result, not another network request.
        val postClient = session.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .build()
        return execute(postClient, request, deadline).use { response ->
            val body = response.body?.string().orEmpty()
            val trusted = response.header("Server") == AUTH_SERVER
            if (trusted && response.code in listOf(302, 303)) {
                AuthPanelHtml.resolve(response.request.url, response.header("Location"))?.let {
                    return@use AuthResult.Success(it)
                }
            }
            if (!response.isSuccessful) {
                return@use AuthResult.Failure("认证失败, HTTP ${response.code}")
            }
            if (trusted) {
                AuthPanelHtml.metaRedirect(response.request.url, body)?.let {
                    return@use AuthResult.Success(it)
                }
            }
            val notice = AuthPanelHtml.notice(body)
                ?: return@use AuthResult.Failure("无法解析认证结果，请使用网页确认是否已经通过认证")
            if (notice == SUCCESS_NOTICE || notice.startsWith(REDIRECT_NOTICE)) {
                val destination = if (trusted && notice.startsWith(REDIRECT_NOTICE)) {
                    AuthPanelHtml.jsRedirect(response.request.url, body)
                } else null
                AuthResult.Success(destination)
            } else {
                AuthResult.Failure("认证失败: $notice")
            }
        }
    }

    private data class Page(val url: HttpUrl, val code: Int, val server: String?, val body: String)

    private fun get(client: OkHttpClient, request: Request, deadline: Long): Page =
        execute(client, request, deadline).use { response ->
            Page(response.request.url, response.code, response.header("Server"),
                response.body?.string().orEmpty())
        }

    private fun execute(client: OkHttpClient, request: Request, deadline: Long): Response {
        val call = client.newCall(request)
        call.timeout().deadlineNanoTime(deadline)
        return call.execute()
    }

    @Suppress("DEPRECATION") // Required to keep OkHttp 4.11 from restoring hostname SNI.
    private fun withoutSni(host: String): OkHttpClient = client.newBuilder()
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                val addresses = client.dns.lookup(hostname)
                return if (hostname != host) addresses else addresses.filterIsInstance<Inet4Address>().ifEmpty {
                    throw UnknownHostException("域名没有可用的 IPv4 地址")
                }
            }
        })
        .sslSocketFactory(IpSniSocketFactory(client.sslSocketFactory, host), trustManager)
        // OkHttp would otherwise set the URL hostname as SNI again during TLS setup.
        .connectionSpecs(listOf(ConnectionSpec.Builder(tlsSpec).supportsTlsExtensions(false).build()))
        .protocols(listOf(Protocol.HTTP_1_1))
        .build()

    companion object {
        private const val AUTH_SERVER = "SakuraFrp-frpc"
        private const val SUCCESS_NOTICE = "认证成功, 现在可以关闭页面并正常连接隧道了"
        private const val REDIRECT_NOTICE = "认证成功, 正在为您跳转到后续链接"

        @SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
        private val trustManager = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        }
        private val tlsSpec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .tlsVersions(TlsVersion.TLS_1_2).build()

        // Auth panels use self-signed certificates; retain the upstream TLS 1.2 behavior.
        @SuppressLint("BadHostnameVerifier")
        private fun createClient(): OkHttpClient {
            val ssl = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
            }
            return OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(3, TimeUnit.SECONDS)
                .writeTimeout(3, TimeUnit.SECONDS)
                .callTimeout(4, TimeUnit.SECONDS)
                .sslSocketFactory(ssl.socketFactory, trustManager)
                .hostnameVerifier { _, _ -> true }
                .connectionSpecs(listOf(tlsSpec, ConnectionSpec.CLEARTEXT))
                .build()
        }

        internal fun authUrl(address: String, port: Int): HttpUrl? {
            if (port !in 1..65535) return null
            val raw = address.trim()
            if (raw.isEmpty()) return null
            val input = when {
                "://" in raw -> raw
                raw.count { it == ':' } > 1 && !raw.startsWith("[") &&
                    raw.none { it in "/?#" } -> "https://[$raw]"
                else -> "https://$raw"
            }
            val url = input.toHttpUrlOrNull() ?: return null
            if (url.username.isNotEmpty() || url.password.isNotEmpty()) return null
            return url.newBuilder().port(port).fragment(null).build()
        }

        internal fun canRetryWithoutSni(url: HttpUrl): Boolean = url.isHttps &&
            url.port != 443 && ':' !in url.host && !url.host.split('.').let { parts ->
                parts.size == 4 && parts.all { (it.toIntOrNull() ?: -1) in 0..255 }
            }
    }
}

/** Change only the TLS peer name. The URL, HTTP Host, path and redirect base stay intact. */
internal class IpSniSocketFactory(
    private val delegate: SSLSocketFactory,
    private val host: String
) : SSLSocketFactory() {
    override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
    override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites
    override fun createSocket(socket: Socket, peerHost: String, port: Int, autoClose: Boolean): Socket =
        delegate.createSocket(socket, if (peerHost == host) socket.inetAddress.hostAddress else peerHost,
            port, autoClose)
    override fun createSocket(host: String, port: Int): Socket = delegate.createSocket(host, port)
    override fun createSocket(host: String, port: Int, local: InetAddress, localPort: Int): Socket =
        delegate.createSocket(host, port, local, localPort)
    override fun createSocket(host: InetAddress, port: Int): Socket = delegate.createSocket(host, port)
    override fun createSocket(host: InetAddress, port: Int, local: InetAddress, localPort: Int): Socket =
        delegate.createSocket(host, port, local, localPort)
}

/** Parse only the auth panel fields and supported redirects; never execute page scripts. */
internal object AuthPanelHtml {
    data class FormFields(val csrf: String, val ip: String)

    private val attributes = Regex("""([a-z_:][-a-z0-9_:.]*)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+))""",
        RegexOption.IGNORE_CASE)
    private val inputs = Regex("""<input\b(?:[^'"<>]|"[^"]*"|'[^']*')*>""", RegexOption.IGNORE_CASE)
    private val divs = Regex("""<div\b(?:[^'"<>]|"[^"]*"|'[^']*')*>""", RegexOption.IGNORE_CASE)
    private val divEnd = Regex("""</div\s*>""", RegexOption.IGNORE_CASE)
    private val head = Regex("""<head(?:\s[^>]*)?>(.*?)</head\s*>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val meta = Regex("""<meta\b(?:[^'"<>]|"[^"]*"|'[^']*')*>""", RegexOption.IGNORE_CASE)
    private val refresh = Regex("""^\s*\d+(?:\.\d+)?\s*;\s*url\s*=\s*(.*?)\s*$""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val js = Regex("""window\.location(?:\.href)?\s*=\s*["'](.*?)["']|window\.location\.replace\(\s*["'](.*?)["']\s*\)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val entities = Regex("""&(#(?:[xX][0-9a-fA-F]+|[0-9]+)|amp|quot|apos|lt|gt|nbsp);""")

    private fun decode(value: String): String = entities.replace(value) { match ->
        when (val entity = match.groupValues[1]) {
            "amp" -> "&"
            "quot" -> "\""
            "apos" -> "'"
            "lt" -> "<"
            "gt" -> ">"
            "nbsp" -> "\u00a0"
            else -> {
                val code = if (entity.startsWith("#x", true)) entity.drop(2).toIntOrNull(16)
                    else entity.drop(1).toIntOrNull()
                if (code != null && Character.isValidCodePoint(code) && code !in 0xD800..0xDFFF) {
                    String(Character.toChars(code))
                } else match.value
            }
        }
    }

    private fun attrs(tag: String): Map<String, String> = attributes.findAll(tag).associate {
        it.groupValues[1].lowercase() to decode(it.groups[2]?.value
            ?: it.groups[3]?.value ?: it.groupValues[4])
    }

    fun formFields(body: String): FormFields? {
        val fields = inputs.findAll(body).map { attrs(it.value) }
            .associate { it["name"] to it["value"] }
        return FormFields(fields["csrf"] ?: return null, fields["ip"] ?: return null)
    }

    fun notice(body: String): String? {
        val tag = divs.findAll(body).firstOrNull {
            attrs(it.value)["class"]?.split(Regex("\\s+"))?.contains("notice") == true
        } ?: return null
        val start = tag.range.last + 1
        val end = divEnd.find(body, start)?.range?.first ?: return null
        return decode(body.substring(start, end).replace(Regex("<[^>]*>"), "")).trim()
    }

    fun metaRedirect(base: HttpUrl, body: String): String? {
        val content = head.find(body)?.groupValues?.get(1) ?: return null
        for (tag in meta.findAll(content)) {
            val attrs = attrs(tag.value)
            if (!attrs["http-equiv"].orEmpty().trim().equals("refresh", true)) continue
            val location = refresh.matchEntire(attrs["content"].orEmpty())
                ?.groupValues?.get(1)?.trim()?.trim('\'', '"') ?: continue
            resolve(base, decode(location))?.let { return it }
        }
        return null
    }

    fun jsRedirect(base: HttpUrl, body: String): String? {
        val match = js.find(body) ?: return null
        return match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }
            ?.let { resolve(base, decode(it)) }
    }

    fun resolve(base: HttpUrl, location: String?): String? {
        val value = location?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            val destination = URI(value)
            if (destination.isAbsolute && !destination.scheme.equals("http", true) &&
                !destination.scheme.equals("https", true)) destination.toString()
            else base.resolve(value)?.toString()
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: java.net.URISyntaxException) {
            null
        }
    }
}
