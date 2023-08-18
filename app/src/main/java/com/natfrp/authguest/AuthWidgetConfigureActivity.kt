package com.natfrp.authguest

import android.annotation.SuppressLint
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.natfrp.authguest.databinding.AuthWidgetConfigureBinding
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The configuration screen for the [AuthWidget] AppWidget.
 */
class AuthWidgetConfigureActivity : AppCompatActivity() {
    private val TAG = "AuthWidgetConfigureActivity"

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var appWidgetName: EditText
    private lateinit var widgetAddr: EditText
    private lateinit var widgetPort: EditText
    private lateinit var widgetPass: EditText
    private lateinit var widgetCall: EditText
    private lateinit var widgetTotp: EditText
    private lateinit var widgetPersist: com.google.android.material.materialswitch.MaterialSwitch
    private lateinit var widgetAlwaysCb: com.google.android.material.materialswitch.MaterialSwitch
    private var onClickListener = View.OnClickListener {
        val context = this@AuthWidgetConfigureActivity

        AuthWidgetManager.savePref(
            context,
            appWidgetId,
            appWidgetName.text.toString(),
            widgetAddr.text.toString(),
            widgetPort.text.toString(),
            widgetPass.text.toString(),
            widgetCall.text.toString(),
            widgetTotp.text.toString(),
            widgetPersist.isChecked,
            widgetAlwaysCb.isChecked
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

        appWidgetName = binding.appwidgetName.editText!!
        widgetAddr = binding.widgetAddr.editText!!
        widgetPort = binding.widgetPort.editText!!
        widgetPass = binding.widgetPass.editText!!
        widgetCall = binding.widgetCall.editText!!
        widgetTotp = binding.widgetTotp.editText!!
        widgetPersist = binding.widgetPersist
        binding.addButton.setOnClickListener(onClickListener)
        binding.fab.setOnClickListener(scanCodeClick)

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

        appWidgetName.setText(
            AuthWidgetManager.loadPrefName(
                this@AuthWidgetConfigureActivity,
                appWidgetId
            )
        )
        binding.widgetCall.setEndIconOnClickListener {
            listAppStart.launch(Intent(this, AppSelectActivity::class.java))
        }
    }

    private val scanCodeStart =
        this.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult? ->
            if (result?.resultCode == Activity.RESULT_OK) {
                result.data?.let { data: Intent ->
                    val value = data.getStringExtra("url") ?: ""
                    Log.d(TAG, "get qrcode result: $value")
                    val url = value.toHttpUrlOrNull()
                    if (url == null) {
                        Toast.makeText(this, "扫码内容不合法", Toast.LENGTH_SHORT).show()
                        return@let
                    }
                    val addr = url.queryParameter("addr")
                    if (url.host != "natfrp.com" || addr == null) {
                        Toast.makeText(this, "扫码内容无效", Toast.LENGTH_SHORT).show()
                        return@let
                    }
                    appWidgetName.setText(url.queryParameter("name"))
                    widgetAddr.setText(addr)
                    widgetPort.setText(url.queryParameter("port"))
                    widgetPass.setText(url.queryParameter("pw"))
                    widgetCall.setText(url.queryParameter("cb"))
                    widgetTotp.setText(url.queryParameter("totp"))
                }
            }
        }

    private var scanCodeClick = View.OnClickListener {
        scanCodeStart.launch(Intent(this, WidgetQrScanActivity::class.java))
    }

    private val listAppStart =
        this.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult? ->
            if (result?.resultCode == Activity.RESULT_OK) {
                result.data?.let { data: Intent ->
                    val value = data.getStringExtra("appPackageName") ?: ""
                    Log.d(TAG, "received $value")
                    @SuppressLint("SetTextI18n")
                    if (value != "") {
                        widgetCall.setText("open-app://$value")
                    }
                }
            }
        }
}



