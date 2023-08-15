package com.natfrp.authguest

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import com.natfrp.authguest.databinding.AuthWidgetConfigureBinding

/**
 * The configuration screen for the [AuthWidget] AppWidget.
 */
class AuthWidgetConfigureActivity : Activity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var appWidgetName: EditText
    private lateinit var widgetAddr: EditText
    private lateinit var widgetPort: EditText
    private lateinit var widgetPass: EditText
    private var onClickListener = View.OnClickListener {
        val context = this@AuthWidgetConfigureActivity

        AuthWidgetManager.savePref(
            context,
            appWidgetId,
            appWidgetName.text.toString(),
            widgetAddr.text.toString(),
            widgetPort.text.toString(),
            widgetPass.text.toString()
        )

        val appWidgetManager = AppWidgetManager.getInstance(context)
        AuthWidgetManager.updateAppWidget(context, appWidgetManager, appWidgetId)

        // Make sure we pass back the original appWidgetId
        val resultValue = Intent()
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, resultValue)
        finish()
    }
    private lateinit var binding: AuthWidgetConfigureBinding

    public override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)

        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED)

        binding = AuthWidgetConfigureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appWidgetName = binding.appwidgetName
        widgetAddr = binding.widgetAddr
        widgetPort = binding.widgetPort
        widgetPass = binding.widgetPass
        binding.addButton.setOnClickListener(onClickListener)

        // Find the widget id from the intent.
        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        // If this activity was started with an intent without an app widget ID, finish with an error.
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        appWidgetName.setText(AuthWidgetManager.loadPrefName(this@AuthWidgetConfigureActivity, appWidgetId))
    }

}



