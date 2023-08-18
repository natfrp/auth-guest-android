package com.natfrp.authguest

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.natfrp.authguest.databinding.ActivityAppSelectBinding
import java.util.Locale


class AppSelectActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppSelectBinding
    private lateinit var list: ListView
    private var items = ArrayList<AppItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.QUERY_ALL_PACKAGES
            )
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.QUERY_ALL_PACKAGES), 0
            )
        }
        loadPackages()

        binding = ActivityAppSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)
        list = binding.appList

        val adapter = AppListAdapter(this, items)
        list.adapter = adapter
        list.onItemClickListener =
            AdapterView.OnItemClickListener { _: AdapterView<*>, _: View, position: Int, _: Long ->
                val intent = Intent(this, AppSelectActivity::class.java)
                intent.putExtra("appPackageName", items[position].packageName)
                setResult(RESULT_OK, intent)
                finish()
            }
    }

    private fun loadPackages() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val allPackages: List<PackageInfo> = this.packageManager.getInstalledPackages(0)

        val langPref = prefs.getString("language", "System Default")
        val config = Configuration()
        if (langPref != null && langPref.contains("_")) {
            val parts: List<String> = langPref.split("_")
            val locale = Locale(parts[0], parts[1])
            Locale.setDefault(locale)
            @SuppressLint("AppBundleLocaleChanges")
            config.locale = locale
        }

        allPackages.forEach { pi ->
            if (pi.applicationInfo != null) {
                val item = AppItem()
                item.packageName = pi.packageName
                // get app name
                val appRes: Resources =
                    this.packageManager.getResourcesForApplication(pi.applicationInfo)
                appRes.updateConfiguration(config, DisplayMetrics())
                item.name =
                    try {
                        appRes.getString(pi.applicationInfo.labelRes)
                    } catch (_: Exception) {
                        try {
                            pi.applicationInfo.nonLocalizedLabel.toString()
                        } catch (_: Exception) {
                            "_未知 APP_"
                        }
                    }

                // get app icon
                item.icon = try {
                    this.packageManager.getApplicationIcon(pi.applicationInfo)
                } catch (_: Exception) {
                    this.packageManager.defaultActivityIcon
                }
                items.add(item)
            }
        }
        items.sortBy { it.name }
    }
}

private class AppItem {
    lateinit var name: String
    lateinit var packageName: String
    lateinit var icon: Drawable
}

private class ViewHolder(
    private val icon: androidx.appcompat.widget.AppCompatImageView,
    private val li: net.nicbell.materiallists.ListItem
) {
    fun bindItem(item: AppItem) {
        icon.setImageDrawable(item.icon)
        li.headline.text = item.name
        li.supportText.text = item.packageName
    }
}

private class AppListAdapter(private val context: Context, private var items: ArrayList<AppItem>) :
    BaseAdapter() {
    override fun getCount(): Int {
        return items.size
    }

    override fun getItem(position: Int): Any {
        TODO("Not yet implemented, it's useless")
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val inflater = LayoutInflater.from(context)
        if (convertView == null) {
            val view = inflater.inflate(R.layout.app_listitem, parent, false)
            val holder =
                ViewHolder(view.findViewById(R.id.app_icon), view.findViewById(R.id.app_item))
            view.tag = holder
            holder.bindItem(items[position])
            return view
        }

        val holder = (convertView.tag as ViewHolder)
        holder.bindItem(items[position])
        return convertView
    }
}