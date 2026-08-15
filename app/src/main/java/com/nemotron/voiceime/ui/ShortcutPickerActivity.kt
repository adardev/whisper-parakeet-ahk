package com.nemotron.voiceime.ui

import android.app.Activity
import android.content.Intent
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
 * Lista de apps con un botón + por cada una para fijar su atajo al home
 * con su icono nativo, uno a la vez.
 */
class ShortcutPickerActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var adapter: ShortcutAdapter

    private val apps = listOf(
        AppInfo("Escolomos", "com.ceti.escolomos", "com.ceti.escolomos.MainActivity"),
        AppInfo("Ingeniería Virtual", "com.ceti.ingenieriavirtual", "com.ceti.ingenieriavirtual.MainActivity"),
        AppInfo("Obsidian", "md.obsidiao", "md.obsidiao.MainActivity"),
        AppInfo("Classroom", "com.google.android.apps.classroom", "com.google.android.apps.classroom.classroomflutter.MainActivity"),
        AppInfo("WhatsApp Business", "com.whatsapp.w4b", "com.whatsapp.Main"),
        AppInfo("WhatsApp", "com.whatsapp", "com.whatsapp.Main"),
        AppInfo("Instagram", "com.instagram.android", "com.instagram.android.activity.MainTabActivity"),
        AppInfo("Proton Pass", "proton.android.past", "proton.android.past.ui.MainActivity"),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shortcut_picker)

        if (!androidx.core.content.pm.ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            Toast.makeText(this, "Este launcher no soporta fijar atajos", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        listView = findViewById(R.id.shortcutListView)
        adapter = ShortcutAdapter()
        listView.adapter = adapter
    }

    private fun requestShortcut(app: AppInfo) {
        val drawable = try {
            packageManager.getApplicationIcon(app.pkg)
        } catch (_: Throwable) {
            Toast.makeText(this, "App no instalada: ${app.pkg}", Toast.LENGTH_SHORT).show()
            return
        }

        val bitmap: android.graphics.Bitmap
        val icon: androidx.core.graphics.drawable.IconCompat

        if (drawable is android.graphics.drawable.AdaptiveIconDrawable) {
            // Para adaptive icons: renderiza el foreground (logo) en alta resolución
            // (108dp × density) y usa createWithAdaptiveBitmap para la máscara nativa.
            val size = (108 * resources.displayMetrics.density).toInt()
            bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.foreground?.setBounds(0, 0, size, size)
            drawable.foreground?.draw(canvas)
            icon = androidx.core.graphics.drawable.IconCompat.createWithAdaptiveBitmap(bitmap)
        } else {
            // Iconos legacy: renderiza en alta resolución sin fondo
            bitmap = renderToBitmap(drawable)
            icon = androidx.core.graphics.drawable.IconCompat.createWithBitmap(bitmap)
        }

        val intent = Intent(this, ShortcutActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            putExtra(ShortcutActivity.EXTRA_PACKAGE, app.pkg)
            putExtra(ShortcutActivity.EXTRA_ACTIVITY, app.activity)
        }

        val shortcut = androidx.core.content.pm.ShortcutInfoCompat.Builder(this, "shortcut_${app.pkg}")
            .setShortLabel(app.label)
            .setLongLabel(app.label)
            .setIcon(icon)
            .setIntent(intent)
            .build()

        androidx.core.content.pm.ShortcutManagerCompat.requestPinShortcut(this, shortcut, null)
    }

    private fun renderToBitmap(drawable: Drawable): android.graphics.Bitmap {
        val size = (108 * resources.displayMetrics.density).toInt()
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bitmap
    }

    data class AppInfo(val label: String, val pkg: String, val activity: String)

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

            val icon = try { packageManager.getApplicationIcon(app.pkg) } catch (_: Throwable) { null }
            if (icon != null) iconView.setImageDrawable(icon)
            labelView.text = app.label
            pkgView.text = app.pkg

            btn.setOnClickListener {
                requestShortcut(app)
            }

            return view
        }
    }
}
