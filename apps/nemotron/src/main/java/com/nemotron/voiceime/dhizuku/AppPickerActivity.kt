package com.nemotron.voiceime.dhizuku

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.nemotron.voiceime.R
import com.nemotron.voiceime.data.SecureStore

class AppPickerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_FREEZE = "freeze"
        const val MODE_AUTO_FREEZE = "auto_freeze"
        const val MODE_STOP_ON_UNLOCK = "stop_on_unlock"
        const val MODE_DOZE_EXEMPT = "doze_exempt"
        const val MODE_STOP_SERVICES = "stop_services"
    }

    private lateinit var listView: ListView
    private lateinit var btnSave: Button
    private lateinit var adapter: AppListAdapter

    private val appList = mutableListOf<AppItem>()
    private val selectedPackages = mutableSetOf<String>()
    private val stopPackages = mutableSetOf<String>()
    private val selectedServices = mutableSetOf<String>()
    private var mode = MODE_FREEZE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_picker)

        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_FREEZE

        listView = findViewById(R.id.appListView)
        btnSave = findViewById(R.id.btnSave)

        val title = findViewById<TextView>(R.id.tvTitle)
        title.text = when (mode) {
            MODE_AUTO_FREEZE -> "Select apps"
            MODE_STOP_ON_UNLOCK -> "Stop on unlock"
            MODE_DOZE_EXEMPT -> "Doze exclusions"
            MODE_STOP_SERVICES -> "Stop running services"
            else -> "Select apps to freeze"
        }

        val hint = findViewById<TextView>(R.id.tvHint)
        hint.text = when (mode) {
            MODE_AUTO_FREEZE ->
                "Select an app to freeze when the screen turns off. Selected apps can also stop on unlock (force-stop)."
            MODE_STOP_ON_UNLOCK ->
                "Apps that stop when unlocked (force-stop; they are not frozen)."
            MODE_DOZE_EXEMPT ->
                "Apps excluded from deep doze: they can sound alarms and work with the screen off (doze whitelist)."
            MODE_STOP_SERVICES ->
                "With Auto Freeze enabled, selected services receive a stop request after screen off. Bound services can remain active while another app still holds them."
            else ->
                "Select apps to freeze or unfreeze manually."
        }
        hint.visibility = View.VISIBLE

        val currentApps = when (mode) {
            MODE_AUTO_FREEZE -> SecureStore.getAutoFreezeApps(this)
            MODE_STOP_ON_UNLOCK -> SecureStore.getStopOnUnlockApps(this)
            MODE_DOZE_EXEMPT -> SecureStore.getDozeExemptApps(this)
            MODE_STOP_SERVICES -> SecureStore.getStopOnScreenOffServices(this).map { it.substringBefore('/') }.toSet()
            else -> SecureStore.getFrozenApps(this)
        }
        selectedPackages.addAll(currentApps)
        selectedServices.addAll(SecureStore.getStopOnScreenOffServices(this))
        if (mode == MODE_AUTO_FREEZE) {
            stopPackages.addAll(SecureStore.getStopOnUnlockApps(this))
        }

        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE

        adapter = AppListAdapter()
        listView.adapter = adapter

        Thread {
            val installed = if (mode == MODE_STOP_SERVICES) {
                val running = ShizukuManager.getRunningServices()
                val serviceComponents = (running.map { it.component } + selectedServices).distinct()
                serviceComponents.mapNotNull { component ->
                    val pkg = component.substringBefore('/')
                    val runningService = running.firstOrNull { it.component == component }
                    val appInfo = runCatching { packageManager.getApplicationInfo(pkg, PackageManager.GET_META_DATA) }.getOrNull()
                    appInfo?.let {
                        val state = if (runningService?.bound == true) " · bound, kept by Android" else ""
                        AppItem(
                            component,
                            "${packageManager.getApplicationLabel(it)} · ${component.substringAfter('/').substringAfterLast('.')}$state",
                            packageManager.getApplicationIcon(it),
                            component,
                            stoppable = runningService?.bound != true
                        )
                    }
                }.sortedBy { it.label.lowercase() }
            } else packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { it.packageName != packageName }
                .map { appInfo ->
                    AppItem(
                        packageName = appInfo.packageName,
                        label = packageManager.getApplicationLabel(appInfo).toString(),
                        icon = packageManager.getApplicationIcon(appInfo)
                    )
                }
                .sortedBy { it.label.lowercase() }

            appList.addAll(installed)

            runOnUiThread {
                adapter.notifyDataSetChanged()

                appList.forEachIndexed { index, item ->
                    if (if (mode == MODE_STOP_SERVICES) selectedServices.contains(item.component) && item.stoppable else selectedPackages.contains(item.packageName)) {
                        listView.setItemChecked(index, true)
                    }
                }
                adapter.notifyDataSetChanged()
            }
        }.start()

        btnSave.setOnClickListener {
            val checked = listView.checkedItemPositions
            selectedPackages.clear()
            selectedServices.clear()
            for (i in 0 until appList.size) {
                if (checked.get(i)) {
                    if (mode == MODE_STOP_SERVICES) selectedServices.add(appList[i].component!!)
                    else selectedPackages.add(appList[i].packageName)
                }
            }

            if (mode == MODE_STOP_SERVICES) {
                SecureStore.setStopOnScreenOffServices(this, selectedServices)
            } else if (mode == MODE_AUTO_FREEZE) {
                SecureStore.setAutoFreezeApps(this, selectedPackages)
                SecureStore.setStopOnUnlockApps(this, stopPackages)
            } else if (mode == MODE_STOP_ON_UNLOCK) {
                SecureStore.setStopOnUnlockApps(this, selectedPackages)
            } else if (mode == MODE_DOZE_EXEMPT) {
                SecureStore.setDozeExemptApps(this, selectedPackages)
            } else {
                SecureStore.setFrozenApps(this, selectedPackages)
                Thread {
                    for (pkg in selectedPackages) {
                        ShizukuManager.hideApp(pkg)
                    }
                }.start()
            }

            val savedCount = if (mode == MODE_STOP_SERVICES) selectedServices.size else selectedPackages.size
            Toast.makeText(this, "Saved: $savedCount ${if (mode == MODE_STOP_SERVICES) "services" else "apps"}", Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
            finish()
        }

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
    }

    data class AppItem(
        val packageName: String,
        val label: String,
        val icon: Drawable,
        val component: String? = null,
        val stoppable: Boolean = true
    )

    inner class AppListAdapter : BaseAdapter() {
        override fun getCount(): Int = appList.size
        override fun getItem(position: Int): AppItem = appList[position]
        override fun getItemId(position: Int): Long = position.toLong()
        override fun areAllItemsEnabled(): Boolean = mode != MODE_STOP_SERVICES
        override fun isEnabled(position: Int): Boolean =
            mode != MODE_STOP_SERVICES || appList[position].stoppable

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(this@AppPickerActivity)
                .inflate(R.layout.item_app_picker, parent, false)

            val item = getItem(position)
            view.findViewById<ImageView>(R.id.appIcon).setImageDrawable(item.icon)
            view.findViewById<TextView>(R.id.appLabel).text = item.label
            view.findViewById<TextView>(R.id.appPackage).text = item.component ?: item.packageName

            val cb = view.findViewById<CheckBox>(R.id.appCheckBox)
            val stopCb = view.findViewById<CheckBox>(R.id.appStopCheckBox)

            cb.isChecked = listView.isItemChecked(position)
            cb.isEnabled = mode != MODE_STOP_SERVICES || item.stoppable
            view.alpha = if (mode == MODE_STOP_SERVICES && !item.stoppable) 0.62f else 1f

            if (mode == MODE_AUTO_FREEZE) {
                stopCb.visibility =
                    if (listView.isItemChecked(position)) View.VISIBLE else View.GONE
                stopCb.isChecked = stopPackages.contains(item.packageName)
                stopCb.setOnClickListener {
                    if (stopPackages.contains(item.packageName)) {
                        stopPackages.remove(item.packageName)
                    } else {
                        stopPackages.add(item.packageName)
                    }
                    notifyDataSetChanged()
                }
            } else {
                stopCb.visibility = View.GONE
            }

            val toggle = View.OnClickListener {
                if (mode == MODE_STOP_SERVICES && !item.stoppable) {
                    Toast.makeText(this@AppPickerActivity, "Android mantiene este servicio enlazado; no se puede detener de forma independiente.", Toast.LENGTH_LONG).show()
                    return@OnClickListener
                }
                val newState = !cb.isChecked
                cb.isChecked = newState
                listView.setItemChecked(position, newState)
                if (mode == MODE_AUTO_FREEZE) {
                    stopCb.visibility = if (newState) View.VISIBLE else View.GONE
                    if (!newState) {
                        stopPackages.remove(item.packageName)
                    }
                    notifyDataSetChanged()
                }
            }
            cb.setOnClickListener(toggle)
            view.setOnClickListener(toggle)

            return view
        }
    }
}
