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

        val url: HttpUrl = try {
            "https://${reqData.first}:${reqData.second}".toHttpUrlOrNull().let { u1 ->
                u1 ?: "https://${reqData.first}".toHttpUrlOrNull()
                ?: "${reqData.first}:${reqData.second}".toHttpUrlOrNull()
                ?: reqData.first.toHttpUrlOrNull() ?: "".toHttpUrl()
            }
        } catch (_: IllegalArgumentException) {
            toast(context, "$name 请求失败, Url 不合法", Toast.LENGTH_SHORT)
            return
        }

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
            b.add("pw", reqData.third)
            b.add("persist_auth", "on")
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
                    return
                }
                val n = noticeMatch.group(1)
                val notice = if (n == null) {
                    toast(
                        context,
                        "$name 返回不正确, 可能已经通过认证?",
                        Toast.LENGTH_SHORT
                    )
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
                    if (notice.startsWith("认证成功, 正在为您跳转到后续链接")) {
                        val m = redirRe.matcher(resp)
                        if (m.find()) {
                            m.group(1).let { url ->
                                val urlIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                urlIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(urlIntent)
                             }
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
