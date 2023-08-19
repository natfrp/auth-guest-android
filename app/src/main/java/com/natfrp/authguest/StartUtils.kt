package com.natfrp.authguest

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
    }
}