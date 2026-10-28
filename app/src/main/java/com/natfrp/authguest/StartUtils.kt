package com.natfrp.authguest

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.ContextCompat

class StartUtils {
    companion object {
        fun toast(context: Context, text: String, duration: Int) {
            ContextCompat.getMainExecutor(context).execute {
                Toast.makeText(context, text, duration).show()
            }
        }

        fun startUrl(context: Context, url: String) {
            val uri = Uri.parse(url)
            val intent = if (uri.scheme == "open-app" && uri.host != null) {
                context.packageManager.getLaunchIntentForPackage(uri.host!!) ?: run {
                    toast(context, "无法打开后续 app", Toast.LENGTH_SHORT)
                    return
                }
            } else if (uri.scheme == "open-activity" && uri.host != null && uri.path != null) {
                Intent().setClassName(uri.host!!, uri.path!!.trim('/'))
            } else {
                Intent(Intent.ACTION_VIEW, uri)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                toast(context, "无法打开后续链接，未找到可用的应用", Toast.LENGTH_SHORT)
            } catch (_: SecurityException) {
                toast(context, "无法打开后续链接，没有启动权限", Toast.LENGTH_SHORT)
            }
        }
    }
}
