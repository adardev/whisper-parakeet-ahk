package com.nemotron.voiceime.ui

import android.app.Activity
import android.content.Intent
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.nemotron.voiceime.R

/**
 * Lista de apps que el usuario puede solicitar añadir a Inicio.
 */
class ShortcutPickerActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var adapter: ShortcutAdapter

    private var apps: List<AppInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pm = packageManager
        apps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            PackageManager.MATCH_DISABLED_COMPONENTS,
        ).sortedWith(android.content.pm.ResolveInfo.DisplayNameComparator(pm))
            .distinctBy { it.activityInfo.packageName }
            .map { resolved ->
                val pkg = resolved.activityInfo.packageName
                val activity = pm.getLaunchIntentForPackage(pkg)?.component?.className
                    ?: resolved.activityInfo.name
                AppInfo(resolved.loadLabel(pm).toString(), pkg, activity)
            }
        setContentView(R.layout.activity_shortcut_picker)

        if (!androidx.core.content.pm.ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            Toast.makeText(this, "Inicio no acepta solicitudes de iconos", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        listView = findViewById(R.id.shortcutListView)
        adapter = ShortcutAdapter()
        listView.adapter = adapter
    }

    private fun requestShortcut(app: AppInfo) {
        val drawable = try {
            shortcutDrawable(app)
        } catch (_: Throwable) {
            Toast.makeText(this, "No se pudo cargar ${app.label}", Toast.LENGTH_SHORT).show()
            return
        }

        val size = (108 * resources.displayMetrics.density).toInt()
        val bitmap = android.graphics.Bitmap.createBitmap(
            size, size, android.graphics.Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        // Keep the package icon's adaptive background and safe-zone padding.
        val icon = androidx.core.graphics.drawable.IconCompat.createWithBitmap(bitmap)

        val launchIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(app.pkg, app.activity)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        val shortcut = androidx.core.content.pm.ShortcutInfoCompat.Builder(
            this, "nemotron_app_${app.pkg}_${System.currentTimeMillis()}"
        )
            .setShortLabel(app.label)
            .setLongLabel(app.label)
            .setIcon(icon)
            .setIntent(launchIntent)
            .build()

        if (!androidx.core.content.pm.ShortcutManagerCompat.requestPinShortcut(this, shortcut, null)) {
            Toast.makeText(this, "No se pudo enviar la solicitud a Inicio", Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = getSharedPreferences("nemotron_launcher", MODE_PRIVATE)
        val selected = prefs.getStringSet("manual_apps", emptySet()).orEmpty().toMutableSet()
        selected.add(app.pkg)
        prefs.edit().putStringSet("manual_apps", selected).commit()
        adapter.notifyDataSetChanged()
        Toast.makeText(this, "Confirma añadir ${app.label} a Inicio", Toast.LENGTH_SHORT).show()
    }

    data class AppInfo(val label: String, val pkg: String, val activity: String)

    private fun shortcutDrawable(app: AppInfo): android.graphics.drawable.Drawable = packageManager.getApplicationIcon(
        if (app.pkg == INSTAGRAM_PACKAGE) packageName else app.pkg
    )

    private fun renderToBitmap(drawable: Drawable): android.graphics.Bitmap {
        val size = (108 * resources.displayMetrics.density).toInt()
        return android.graphics.Bitmap.createBitmap(
            size, size, android.graphics.Bitmap.Config.ARGB_8888
        ).also { bitmap ->
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
        }
    }

    inner class ShortcutAdapter : BaseAdapter() {
        override fun getCount(): Int = apps.size
        override fun getItem(position: Int): AppInfo = apps[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(this@ShortcutPickerActivity)
                .inflate(R.layout.item_shortcut, parent, false)

            val app = getItem(position)
            val iconView = view.findViewById<ImageView>(R.id.appIcon)
            val labelView = view.findViewById<TextView>(R.id.appLabel)
            val pkgView = view.findViewById<TextView>(R.id.appPackage)
            val btn = view.findViewById<Button>(R.id.btnAddShortcut)
            val selected = getSharedPreferences("nemotron_launcher", MODE_PRIVATE)
                .getStringSet("manual_apps", emptySet()).orEmpty().contains(app.pkg)

            val icon = try { shortcutDrawable(app) } catch (_: Throwable) { null }
            if (icon != null) iconView.setImageDrawable(icon)
            labelView.text = app.label
            pkgView.text = app.pkg
            btn.text = if (selected) "Actualizar" else "+"

            btn.setOnClickListener {
                requestShortcut(app)
            }

            return view
        }
    }

    companion object {
        private const val INSTAGRAM_PACKAGE = "com.instagram.android"
    }
}
