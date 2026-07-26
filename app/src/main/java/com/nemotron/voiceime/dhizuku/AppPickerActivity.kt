package com.nemotron.voiceime.dhizuku

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

    private lateinit var listView: ListView
    private lateinit var btnSave: Button
    private lateinit var switchAutoFreeze: Switch
    private lateinit var adapter: AppListAdapter

    private val appList = mutableListOf<AppItem>()
    private val selectedPackages = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_picker)

        listView = findViewById(R.id.appListView)
        btnSave = findViewById(R.id.btnSave)
        switchAutoFreeze = findViewById(R.id.switchAutoFreeze)

        selectedPackages.addAll(SecureStore.getFrozenApps(this))
        switchAutoFreeze.isChecked = SecureStore.isAutoFreeze(this)

        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE

        adapter = AppListAdapter()
        listView.adapter = adapter

        Thread {
            val installed = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { it.packageName != packageName && it.packageName != "com.rosan.dhizuku" }
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
            SecureStore.setFrozenApps(this, selectedPackages)

            val autoFreeze = switchAutoFreeze.isChecked
            SecureStore.setAutoFreeze(this, autoFreeze)
            com.nemotron.voiceime.a11y.FocusPasteService.setAutoFreezeEnabled(this, autoFreeze)

            Thread {
                val apps = SecureStore.getFrozenApps(this)
                val frozenNow = DhizukuManager.isCurrentlyFrozen(this)
                if (!frozenNow && apps.isNotEmpty()) {
                    DhizukuManager.freezeAll(this)
                } else if (frozenNow && apps.isEmpty()) {
                    DhizukuManager.unfreezeAll(this)
                }
            }.start()

            Toast.makeText(this, "Saved: ${selectedPackages.size} apps", Toast.LENGTH_SHORT).show()
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
            view.findViewById<CheckBox>(R.id.appCheckBox).isChecked = listView.isItemChecked(position)

            view.setOnClickListener {
                val cb = view.findViewById<CheckBox>(R.id.appCheckBox)
                cb.isChecked = !cb.isChecked
                listView.setItemChecked(position, cb.isChecked)
            }

            return view
        }
    }
}
