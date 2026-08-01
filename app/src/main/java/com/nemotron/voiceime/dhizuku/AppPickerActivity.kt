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
    }

    private lateinit var listView: ListView
    private lateinit var btnSave: Button
    private lateinit var adapter: AppListAdapter

    private val appList = mutableListOf<AppItem>()
    private val selectedPackages = mutableSetOf<String>()
    private val stopPackages = mutableSetOf<String>()
    private var mode = MODE_FREEZE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_picker)

        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_FREEZE

        listView = findViewById(R.id.appListView)
        btnSave = findViewById(R.id.btnSave)

        val title = findViewById<TextView>(R.id.tvTitle)
        title.text = when (mode) {
            MODE_AUTO_FREEZE -> "Seleccionar apps"
            MODE_STOP_ON_UNLOCK -> "Stop on unlock"
            MODE_DOZE_EXEMPT -> "Excluidas de doze"
            else -> "Select apps to freeze"
        }

        val hint = findViewById<TextView>(R.id.tvHint)
        hint.text = when (mode) {
            MODE_AUTO_FREEZE ->
                "Marca una app para congelarla al apagar la pantalla. En las marcadas aparece 'detener' (naranja): cerrarla al desbloquear (force-stop)."
            MODE_STOP_ON_UNLOCK ->
                "Apps que se detendran al desbloquear (force-stop, no se congelan)."
            MODE_DOZE_EXEMPT ->
                "Apps excluidas del doze profundo: pueden sonar alarmas y trabajar con pantalla apagada (whitelist de doze)."
            else ->
                "Selecciona apps para congelar/descongelar manualmente."
        }
        hint.visibility = View.VISIBLE

        val currentApps = when (mode) {
            MODE_AUTO_FREEZE -> SecureStore.getAutoFreezeApps(this)
            MODE_STOP_ON_UNLOCK -> SecureStore.getStopOnUnlockApps(this)
            MODE_DOZE_EXEMPT -> SecureStore.getDozeExemptApps(this)
            else -> SecureStore.getFrozenApps(this)
        }
        selectedPackages.addAll(currentApps)
        if (mode == MODE_AUTO_FREEZE) {
            stopPackages.addAll(SecureStore.getStopOnUnlockApps(this))
        }

        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE

        adapter = AppListAdapter()
        listView.adapter = adapter

        Thread {
            val installed = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
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
                    if (selectedPackages.contains(item.packageName)) {
                        listView.setItemChecked(index, true)
                    }
                }
                adapter.notifyDataSetChanged()
            }
        }.start()

        btnSave.setOnClickListener {
            val checked = listView.checkedItemPositions
            selectedPackages.clear()
            for (i in 0 until appList.size) {
                if (checked.get(i)) {
                    selectedPackages.add(appList[i].packageName)
                }
            }

            if (mode == MODE_AUTO_FREEZE) {
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

            Toast.makeText(this, "Saved: ${selectedPackages.size} apps", Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
            finish()
        }

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
    }

    data class AppItem(
        val packageName: String,
        val label: String,
        val icon: Drawable
    )

    inner class AppListAdapter : BaseAdapter() {
        override fun getCount(): Int = appList.size
        override fun getItem(position: Int): AppItem = appList[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(this@AppPickerActivity)
                .inflate(R.layout.item_app_picker, parent, false)

            val item = getItem(position)
            view.findViewById<ImageView>(R.id.appIcon).setImageDrawable(item.icon)
            view.findViewById<TextView>(R.id.appLabel).text = item.label
            view.findViewById<TextView>(R.id.appPackage).text = item.packageName

            val cb = view.findViewById<CheckBox>(R.id.appCheckBox)
            val stopCb = view.findViewById<CheckBox>(R.id.appStopCheckBox)

            cb.isChecked = listView.isItemChecked(position)

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
