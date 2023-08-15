package com.natfrp.authguest

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * Implementation of App Widget functionality.
 * App Widget Configuration implemented in [AuthWidgetConfigureActivity]
 */

class AuthWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            AuthWidgetManager.updateAppWidget(context, appWidgetManager, appWidgetId)
        }

    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            AuthWidgetManager.deletePref(context, appWidgetId)
        }
    }
}

private const val PREFS_NAME = "com.natfrp.authguest.AuthWidget"
private const val PREF_PREFIX_KEY = "appwidget_"
private const val PREF_ADDR = "_addr"
private const val PREF_PORT = "_port"
private const val PREF_PASS = "_pass"

class AuthWidgetManager {
    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val widgetName = loadPrefName(context, appWidgetId)
            // Construct the RemoteViews object

            val pendingIntent: PendingIntent = Intent(context, AuthWidgetClickReceiver::class.java)
                .let { intent ->
                    intent.action = "com.natfrp.authwidget.widgetClick"
                    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    PendingIntent.getBroadcast(
                        context,
                        appWidgetId,
                        intent,
                        PendingIntent.FLAG_MUTABLE
                    )
                }

            val views: RemoteViews = RemoteViews(
                context.packageName,
                R.layout.auth_widget
            ).apply {
                setTextViewText(R.id.widget_name, widgetName)
                setOnClickPendingIntent(R.id.widget_name, pendingIntent)
            }

            // Instruct the widget manager to update the widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun savePref(
            context: Context,
            appWidgetId: Int,
            name: String,
            addr: String,
            port: String,
            pass: String
        ) {
            val prefs = context.getSharedPreferences(PREFS_NAME, 0).edit()
            prefs.putString(PREF_PREFIX_KEY + appWidgetId, name)
            prefs.putString(PREF_PREFIX_KEY + appWidgetId + PREF_ADDR, addr)
            prefs.putInt(PREF_PREFIX_KEY + appWidgetId + PREF_PORT, port.toInt())
            prefs.putString(PREF_PREFIX_KEY + appWidgetId + PREF_PASS, pass)
            prefs.apply()
        }

        fun loadPrefName(context: Context, appWidgetId: Int): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, 0)
            val titleValue = prefs.getString(PREF_PREFIX_KEY + appWidgetId, null)
            return titleValue ?: context.getString(R.string.widget_name)
        }

        fun loadPrefReq(context: Context, appWidgetId: Int): Triple<String, Int, String>? {
            val prefs = context.getSharedPreferences(PREFS_NAME, 0)
            prefs.getString(PREF_PREFIX_KEY + appWidgetId, null) ?: return null
            return Triple(
                prefs.getString(PREF_PREFIX_KEY + appWidgetId + PREF_ADDR, "") ?: return null,
                prefs.getInt(PREF_PREFIX_KEY + appWidgetId + PREF_PORT, 0),
                prefs.getString(PREF_PREFIX_KEY + appWidgetId + PREF_PASS, "") ?: return null
            )
        }

        fun deletePref(context: Context, appWidgetId: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, 0).edit()
            prefs.remove(PREF_PREFIX_KEY + appWidgetId)
            prefs.apply()
        }
    }
}

