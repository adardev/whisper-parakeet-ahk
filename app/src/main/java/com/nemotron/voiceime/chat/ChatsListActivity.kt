package com.nemotron.voiceime.chat

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.HapticFeedbackConstants
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.nemotron.voiceime.R
import org.json.JSONArray
import org.json.JSONObject

class ChatsListActivity : Activity() {
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshLoop = object : Runnable { override fun run() { refresh(); refreshHandler.postDelayed(this, 5000) } }

    private val list = mutableListOf<Conversation>()
    private lateinit var adapter: ChatListAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var incognitoBtn: ImageButton
    private lateinit var chat: ChatClient
    private var renderedSignature = ""
    private var hasRemoteSnapshot = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chats)
        ConversationStore.init(this)
        val prefs = getSharedPreferences("hermes_chat", Context.MODE_PRIVATE)
        val savedUrl = prefs.getString("server_url", null)
        val base = if (savedUrl.isNullOrBlank() || savedUrl.startsWith("http://100.115.113.28")) {
            prefs.edit().putString("server_url", "http://192.168.0.2:8888").apply()
            "http://192.168.0.2:8888"
        } else savedUrl
        chat = ChatClient(base)

        recycler = findViewById(R.id.recyclerChats)
        emptyView = findViewById(R.id.emptyView)
        incognitoBtn = findViewById(R.id.incognitoBtn)
        findViewById<ImageButton>(R.id.menuBtn).setOnClickListener { haptic(it); showMenu() }

        adapter = ChatListAdapter(list, ::openConv, ::deleteConv, ::renameConv)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        incognitoBtn.setOnClickListener {
            haptic(it)
            startActivity(Intent(this, ChatActivity::class.java).putExtra("incognito", true))
        }
        incognitoBtn.setOnLongClickListener {
            Toast.makeText(this, "Nuevo chat incógnito: se descarta al salir", Toast.LENGTH_SHORT).show()
            true
        }

        val fab: FloatingActionButton = findViewById(R.id.fabNew)
        fab.setOnClickListener {
            haptic(it)
            chat.createConversation({ json ->
                val c = parseConversation(json)
                runOnUiThread { ConversationStore.save(c); refresh(); openConv(c) }
            }, { runOnUiThread {
                val c = ConversationStore.create(); refresh(); openConv(c)
            }})
        }

        applySystemUi()
        updateIncognitoUi()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
        refreshHandler.postDelayed(refreshLoop, 5000)
    }

    override fun onPause() {
        refreshHandler.removeCallbacks(refreshLoop)
        super.onPause()
    }

    private fun refresh() {
        if (!hasRemoteSnapshot) renderChats(ConversationStore.list())
        chat.conversations({ arr ->
            val remote = parseConversations(arr).sortedWith(compareByDescending<Conversation> { it.createdAt }.thenBy { it.id })
            runOnUiThread {
                if (remote.isNotEmpty()) {
                    ConversationStore.replaceRemote(remote)
                    hasRemoteSnapshot = true
                    renderChats(remote)
                }
            }
        }, {})
    }

    private fun renderChats(items: List<Conversation>) {
        val signature = items.joinToString("|") { "${it.id}:${it.title}:${it.createdAt}:${it.source}:${it.displayName}" }
        if (signature == renderedSignature) return
        renderedSignature = signature
        list.clear()
        list.addAll(items)
        adapter.notifyDataSetChanged()
        emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openConv(c: Conversation) {
        startActivity(Intent(this, ChatActivity::class.java).putExtra("convId", c.id))
    }

    private fun deleteConv(c: Conversation) {
        ConversationStore.delete(c.id)
        refresh()
    }

    private fun renameConv(c: Conversation) {
        val input = EditText(this).apply { setText(c.title); selectAll(); hint = "Nombre de la conversación" }
        AlertDialog.Builder(this).setTitle("Renombrar conversación").setView(input)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar") { _, _ ->
                val title = input.text.toString().trim()
                if (title.isNotEmpty()) { c.title = title; ConversationStore.save(c); refresh() }
            }.show()
    }

    private fun prefs() = getSharedPreferences("hermes_chat", Context.MODE_PRIVATE)

    private fun updateIncognitoUi() {
        incognitoBtn.colorFilter = android.graphics.PorterDuffColorFilter(
            Color.parseColor("#2F80FF"),
            android.graphics.PorterDuff.Mode.SRC_IN
        )
        incognitoBtn.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 22f
            setColor(Color.parseColor("#182A49"))
        }
    }

    private fun applySystemUi() {
        window.statusBarColor = Color.parseColor("#0B0C0F")
        window.navigationBarColor = Color.parseColor("#0B0C0F")
    }

    private fun haptic(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    private fun showMenu() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(14))
            background = GradientDrawable().apply { setColor(Color.parseColor("#171B24")); cornerRadius = dp(26).toFloat(); setStroke(dp(1), Color.parseColor("#30415E")) }
        }
        val title = TextView(this).apply { text = "Adarbot"; setTextColor(Color.WHITE); textSize = 21f; setTypeface(null, android.graphics.Typeface.BOLD); setPadding(dp(10), 0, 0, dp(12)) }
        panel.addView(title)
        panel.addView(menuItem(R.drawable.ic_search, "Buscar conversaciones", "Encuentra un chat por nombre") { searchChats() })
        panel.addView(menuItem(R.drawable.ic_server, "Mi NAS y servidor", "192.168.0.2  ·  conectado") { Toast.makeText(this, "Servidor Adarbot conectado", Toast.LENGTH_SHORT).show() })
        panel.addView(menuItem(R.drawable.ic_profile, "Perfil de Adarbot", "Versión 0.6  ·  agente personal") { Toast.makeText(this, "Adarbot · tu agente personal", Toast.LENGTH_SHORT).show() })
        PopupWindow(panel, dp(326), android.view.ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            elevation = dp(20).toFloat()
            setBackgroundDrawable(GradientDrawable().apply { setColor(Color.TRANSPARENT) })
            isOutsideTouchable = true
            showAtLocation(findViewById(android.R.id.content), Gravity.TOP or Gravity.START, dp(14), dp(92))
        }
    }

    private fun menuItem(icon: Int, label: String, detail: String, click: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), dp(10), dp(10), dp(10)); isClickable = true
        setBackgroundColor(Color.TRANSPARENT)
        addView(android.widget.ImageView(context).apply { setImageResource(icon); setColorFilter(Color.parseColor("#77B5FF")); layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)) })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0); layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            addView(TextView(context).apply { text = label; setTextColor(Color.WHITE); textSize = 16f })
            addView(TextView(context).apply { text = detail; setTextColor(Color.parseColor("#8EA3C2")); textSize = 12f; setPadding(0, dp(3), 0, 0) })
        })
        setOnClickListener { haptic(this); click() }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun searchChats() {
        val input = EditText(this).apply { hint = "Buscar por nombre"; setSingleLine(true); setPadding(32, 8, 32, 8) }
        AlertDialog.Builder(this).setTitle("Buscar chats").setView(input).setPositiveButton("Buscar") { _, _ ->
            val query = input.text.toString().trim().lowercase()
            val filtered = if (query.isEmpty()) ConversationStore.list() else ConversationStore.list().filter { it.title.lowercase().contains(query) }
            list.clear(); list.addAll(filtered); adapter.notifyDataSetChanged(); emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }.setNegativeButton("Cancelar", null).show()
    }

    private fun parseConversations(arr: JSONArray): List<Conversation> = buildList {
        for (i in 0 until arr.length()) add(parseConversation(arr.getJSONObject(i)))
    }.sortedWith(compareByDescending<Conversation> { it.createdAt }.thenBy { it.id })

    private fun parseConversation(o: JSONObject): Conversation = Conversation(
        o.optString("id"), o.optString("title", "Nuevo chat"),
        o.optLong("created_at", System.currentTimeMillis()),
        mutableListOf(), o.optString("source"), o.optString("display_name"), o.optString("chat_id")
    )
}
