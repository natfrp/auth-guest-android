package com.natfrp.authguest

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager


fun OkHttpClient.Builder.ignoreAllSSLErrors(): OkHttpClient.Builder {
    val naiveTrustManager = @SuppressLint("CustomX509TrustManager")
    object : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) = Unit
    }

    val insecureSocketFactory = SSLContext.getInstance("TLSv1.2").apply {
        val trustAllCerts = arrayOf<TrustManager>(naiveTrustManager)
        init(null, trustAllCerts, SecureRandom())
    }.socketFactory

    sslSocketFactory(insecureSocketFactory, naiveTrustManager)
    hostnameVerifier { _, _ -> true }
    return this
}

class AuthWidgetClickReceiver : BroadcastReceiver() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(1, TimeUnit.SECONDS)
        .writeTimeout(1, TimeUnit.SECONDS)
        .readTimeout(1, TimeUnit.SECONDS)
        .callTimeout(2, TimeUnit.SECONDS)
        .ignoreAllSSLErrors()
        .build()
    private val TAG = "AuthWidgetClickReceiver"
    private val getRe =
        Pattern.compile("(?s).*name=\"csrf\" value=\"(?<csrf>.*?)\".*name=\"ip\" value=\"(?<ip>.*?)\".*")
    private val noticeRe = Pattern.compile("(?s).*<div class=\"notice\">(.*?)</div>.*")
    private val redirRe = Pattern.compile("(?s)window.location = \"(.*?)\"")

    private fun toast(context: Context, text: String, duration: Int) {
        ContextCompat.getMainExecutor(context).execute {
            Toast.makeText(context, text, duration).show()
        }
    }

    private fun startUrl(context: Context, url: String) {
        val uri = Uri.parse(url)
        val intent: Intent?
        if (uri.scheme == "open-app" && uri.host != null) {
            val pm = context.packageManager
            intent = pm.getLaunchIntentForPackage(uri.host!!)
            if (intent != null) {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            } else {
                toast(context, "无法打开后续 app", Toast.LENGTH_SHORT)
                return
            }
        } else if (uri.scheme == "open-activity" && uri.host != null && uri.path != null) {
            intent = Intent()
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            intent.setClassName(uri.host!!, uri.path!!.trim('/'))
        } else {
            intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }
        context.startActivity(intent)
    }

    private fun totpCode(u: String): String? {
        if (u.isEmpty()) return null

        val url: Uri =
            Uri.parse(if (u.startsWith("otpauth://")) u else "otpauth://totp/auto?secret=$u")
                ?: return null
        val totp = TotpUtils(
            url.getQueryParameter("algorithm").let { it ?: "SHA1" },
            url.getQueryParameter("secret").let { it ?: return null },
            url.getQueryParameter("digits")
                .let { if (it == null || it.toIntOrNull() == null) 6 else it.toInt() },
            url.getQueryParameter("period")
                .let { if (it == null || it.toIntOrNull() == null) 30 else it.toInt() },
        )
        return totp.genCode()
    }

    private fun req(context: Context, intent: Intent) {
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        val name = AuthWidgetManager.loadPrefName(context, widgetId)
        Log.d(TAG, "req from id:$widgetId name:$name")
        toast(context, "正在发送请求到 $name", Toast.LENGTH_SHORT)
        val reqData = AuthWidgetManager.loadPrefReq(context, widgetId)
        if (reqData == null) {
            toast(context, "$name 请求失败, 数据错误", Toast.LENGTH_SHORT)
            return
        }

        val tmpUrl: HttpUrl = try {
            "https://${reqData.addr}:${reqData.port}".toHttpUrlOrNull().let { u1 ->
                u1 ?: "https://${reqData.addr}".toHttpUrlOrNull()
                ?: "${reqData.addr}:${reqData.port}".toHttpUrlOrNull()
                ?: reqData.addr.toHttpUrlOrNull() ?: "".toHttpUrl()
            }
        } catch (_: IllegalArgumentException) {
            toast(context, "$name 请求失败, Url 不合法", Toast.LENGTH_SHORT)
            return
        }

        val url = HttpUrl.Builder()
            .host(InetAddress.getByName(tmpUrl.host).hostAddress?.toString() ?: tmpUrl.host)
            .scheme(tmpUrl.scheme).port(tmpUrl.port).build()
        val requestGet = Request.Builder().url(url).get().build()
        try {
            client.newCall(requestGet).execute().use { response ->
                if (!response.isSuccessful || response.body == null) {
                    toast(
                        context,
                        "$name 不是访问认证, ${response.code}",
                        Toast.LENGTH_SHORT
                    )
                    return
                }
                val resp = response.body!!.string()
                Log.d(TAG, "get: $resp")
                val m = getRe.matcher(resp)
                if (!m.matches()) {
                    toast(context, "$name 不是访问认证", Toast.LENGTH_SHORT)
                    return
                }
                val ipMatch = m.group("ip")
                if (ipMatch == null) {
                    toast(context, "$name 不是访问认证", Toast.LENGTH_SHORT)
                    return
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "page get failed: $e")
            toast(context, "$name 请求失败, ${e.localizedMessage}", Toast.LENGTH_SHORT)
            return
        }


        val postBody: FormBody = FormBody.Builder().let { b ->
            b.add("pw", reqData.pw)
            b.add("persist_auth", if (reqData.persist) "on" else "off")
            totpCode(reqData.totp)?.let { b.add("totp", it) }
            b.build()
        }
        val request = Request.Builder()
            .url(url)
            .post(postBody)
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful || response.body == null) {
                    toast(
                        context,
                        "$name 认证失败, ${response.code}",
                        Toast.LENGTH_SHORT
                    )
                    if (reqData.alwaysCb && reqData.callback != "") {
                        startUrl(context, reqData.callback)
                    }
                    return
                }

                val resp = response.body!!.string()
                Log.d(TAG, "post: $resp")

                val noticeMatch = noticeRe.matcher(resp)
                if (!noticeMatch.matches()) {
                    toast(
                        context,
                        "$name 返回不正确, 可能已经通过认证?",
                        Toast.LENGTH_SHORT
                    )
                    if (reqData.alwaysCb && reqData.callback != "") {
                        startUrl(context, reqData.callback)
                    }
                    return
                }
                val n = noticeMatch.group(1)
                val notice = if (n == null) {
                    toast(
                        context,
                        "$name 返回不正确, 可能已经通过认证?",
                        Toast.LENGTH_SHORT
                    )
                    if (reqData.alwaysCb && reqData.callback != "") {
                        startUrl(context, reqData.callback)
                    }
                    return
                } else {
                    n.trim()
                }

                if (notice.startsWith("认证成功")) {
                    toast(
                        context,
                        "$name 认证成功",
                        Toast.LENGTH_SHORT
                    )
                    if (reqData.callback != "") {
                        startUrl(context, reqData.callback)
                        return@use
                    }
                    if (notice.startsWith("认证成功, 正在为您跳转到后续链接")) {
                        val m = redirRe.matcher(resp)
                        if (m.find()) {
                            m.group(1)?.let { startUrl(context, it) }
                        }
                    }
                } else {
                    toast(
                        context,
                        "$name 认证失败: $notice",
                        Toast.LENGTH_SHORT
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "page post failed: $e")
            toast(context, "$name 请求失败, ${e.localizedMessage}", Toast.LENGTH_SHORT)
            if (reqData.alwaysCb && reqData.callback != "") {
                startUrl(context, reqData.callback)
            }
            return
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "received " + intent.action)

        if (intent.action == "com.natfrp.authwidget.widgetClick") {
            Thread {
                req(context, intent)
            }.start()
        }
    }
}
