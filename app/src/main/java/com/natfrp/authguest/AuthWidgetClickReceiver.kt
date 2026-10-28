package com.natfrp.authguest

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.natfrp.authguest.StartUtils.Companion.startUrl
import com.natfrp.authguest.StartUtils.Companion.toast
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class AuthWidgetClickReceiver : BroadcastReceiver() {
    private fun totpCode(value: String): String? {
        val input = value.trim()
        if (input.isEmpty()) return null
        val url = if (input.startsWith("otpauth://")) Uri.parse(input) else {
            Uri.Builder().scheme("otpauth").authority("totp").path("auto")
                .appendQueryParameter("secret", input).build()
        }
        require(url.scheme == "otpauth" && url.host == "totp")
        val algorithm = url.getQueryParameter("algorithm") ?: "SHA1"
        require(algorithm.uppercase() in setOf("SHA1", "SHA256", "SHA512"))
        val secret = url.getQueryParameter("secret")?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("缺少 TOTP 密钥")
        val digits = url.getQueryParameter("digits")?.let {
            it.toIntOrNull() ?: throw IllegalArgumentException("TOTP 位数错误")
        } ?: 6
        val period = url.getQueryParameter("period")?.let {
            it.toIntOrNull() ?: throw IllegalArgumentException("TOTP 周期错误")
        } ?: 30
        require(digits in 6..8 && period > 0)
        return TotpUtils(algorithm, secret, digits, period).genCode()
    }

    private fun req(context: Context, widgetId: Int) {
        val name = AuthWidgetManager.loadPrefName(context, widgetId)
        val data = AuthWidgetManager.loadPrefReq(context, widgetId)
        if (data == null) {
            toast(context, "$name 请求失败, 数据错误", Toast.LENGTH_SHORT)
            return
        }
        toast(context, "正在发送请求到 $name", Toast.LENGTH_SHORT)
        val result = try {
            client.authenticate(data) { totpCode(data.totp) }
        } catch (e: IOException) {
            Log.w(TAG, "Authentication request failed", e)
            AuthResult.Failure("请求失败, ${e.localizedMessage}；可能已经通过认证，可使用网页确认")
        } catch (e: Exception) {
            Log.e(TAG, "Invalid authentication request", e)
            AuthResult.Failure("请求失败, ${e.localizedMessage}")
        }
        val message = when (result) {
            is AuthResult.Success -> "认证成功"
            is AuthResult.Failure -> result.reason
        }
        toast(context, "$name $message", Toast.LENGTH_SHORT)

        val destination = data.destinationFor(result)
        if (destination != null) {
            ContextCompat.getMainExecutor(context).execute { startUrl(context, destination) }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.natfrp.authwidget.widgetClick") return
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        if (!inFlight.add(widgetId)) {
            toast(context, "请求正在进行，请稍候", Toast.LENGTH_SHORT)
            return
        }
        val pending = goAsync()
        try {
            Thread({
                try {
                    req(context.applicationContext, widgetId)
                } finally {
                    // Finish after the queued toast/callback has run on the main thread.
                    ContextCompat.getMainExecutor(context).execute {
                        inFlight.remove(widgetId)
                        pending.finish()
                    }
                }
            }, "auth-widget-$widgetId").start()
        } catch (e: Exception) {
            inFlight.remove(widgetId)
            pending.finish()
            Log.e(TAG, "Could not start authentication request", e)
            toast(context, "无法启动认证请求，请重试", Toast.LENGTH_SHORT)
        }
    }

    companion object {
        private const val TAG = "AuthWidgetClickReceiver"
        private val client by lazy { AuthPanelClient() }
        private val inFlight = ConcurrentHashMap.newKeySet<Int>()
    }
}
