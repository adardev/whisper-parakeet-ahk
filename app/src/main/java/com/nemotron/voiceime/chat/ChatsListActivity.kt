package com.nemotron.voiceime.chat

import android.app.Activity
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
import android.app.AlertDialog
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chats)
        ConversationStore.init(this)
        val base = getSharedPreferences("hermes_chat", Context.MODE_PRIVATE)
            .getString("server_url", "http://100.115.113.28:8888") ?: "http://100.115.113.28:8888"
        chat = ChatClient(base)

        recycler = findViewById(R.id.recyclerChats)
        emptyView = findViewById(R.id.emptyView)
        incognitoBtn = findViewById(R.id.incognitoBtn)
        findViewById<ImageButton>(R.id.menuBtn).setOnClickListener { haptic(it); showMenu() }

        adapter = ChatListAdapter(list, ::openConv, ::deleteConv)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        incognitoBtn.setOnClickListener {
            haptic(it)
            setIncognito(!isIncognito())
            updateIncognitoUi()
        }
        incognitoBtn.setOnLongClickListener {
            Toast.makeText(
                this,
                if (isIncognito()) "Modo incognito: ON (no se guarda nada)" else "Modo incognito: OFF",
                Toast.LENGTH_SHORT
            ).show()
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
        list.clear()
        list.addAll(ConversationStore.list())
        adapter.notifyDataSetChanged()
        emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        chat.conversations({ arr ->
            val remote = parseConversations(arr)
            runOnUiThread {
                if (remote.isNotEmpty()) {
                    ConversationStore.replaceRemote(remote)
                    list.clear(); list.addAll(remote); adapter.notifyDataSetChanged()
                    emptyView.visibility = View.GONE
                }
            }
        }, {})
    }

    private fun openConv(c: Conversation) {
        startActivity(Intent(this, ChatActivity::class.java).putExtra("convId", c.id))
    }

    private fun deleteConv(c: Conversation) {
        ConversationStore.delete(c.id)
        refresh()
    }

    private fun isIncognito() = prefs().getBoolean("incognito", false)
    private fun setIncognito(v: Boolean) = prefs().edit().putBoolean("incognito", v).apply()
    private fun prefs() = getSharedPreferences("hermes_chat", Context.MODE_PRIVATE)

    private fun updateIncognitoUi() {
        val on = isIncognito()
        incognitoBtn.colorFilter = android.graphics.PorterDuffColorFilter(
            if (on) Color.parseColor("#2F80FF") else Color.parseColor("#5A5A6E"),
            android.graphics.PorterDuff.Mode.SRC_IN
        )
        incognitoBtn.background = if (on) {
            android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 22f
                setColor(Color.parseColor("#22243A"))
            }
        } else {
            android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 22f
                setColor(Color.TRANSPARENT)
            }
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
        AlertDialog.Builder(this)
            .setTitle("Adarbot")
            .setItems(arrayOf("Buscar conversaciones", "Mi NAS y servidor", "Acerca de Adarbot")) { _, which ->
                when (which) {
                    0 -> searchChats()
                    1 -> AlertDialog.Builder(this).setTitle("Mi NAS").setMessage("Servidor Adarbot\n100.115.113.28:8888\n\nConectado por el proxy de Hermes Agent.").setPositiveButton("Listo", null).show()
                    2 -> AlertDialog.Builder(this).setTitle("Adarbot").setMessage("Tu agente personal\nVersion 0.6\n\nConversaciones sincronizadas con tu servidor.").setPositiveButton("Listo", null).show()
                }
            }.show()
    }

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
    }

    private fun parseConversation(o: JSONObject): Conversation = Conversation(
        o.optString("id"), o.optString("title", "Nuevo chat"),
        o.optLong("created_at", System.currentTimeMillis())
    )
}
