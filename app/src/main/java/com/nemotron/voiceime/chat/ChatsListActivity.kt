package com.nemotron.voiceime.chat

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.nemotron.voiceime.R

class ChatsListActivity : Activity() {

    private val list = mutableListOf<Conversation>()
    private lateinit var adapter: ChatListAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var incognitoBtn: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chats)
        ConversationStore.init(this)

        recycler = findViewById(R.id.recyclerChats)
        emptyView = findViewById(R.id.emptyView)
        incognitoBtn = findViewById(R.id.incognitoBtn)

        adapter = ChatListAdapter(list, ::openConv, ::deleteConv)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        incognitoBtn.setOnClickListener {
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
            val c = ConversationStore.create()
            refresh()
            openConv(c)
        }

        applySystemUi()
        updateIncognitoUi()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        list.clear()
        list.addAll(ConversationStore.list())
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

    private fun isIncognito() = prefs().getBoolean("incognito", false)
    private fun setIncognito(v: Boolean) = prefs().edit().putBoolean("incognito", v).apply()
    private fun prefs() = getSharedPreferences("hermes_chat", Context.MODE_PRIVATE)

    private fun updateIncognitoUi() {
        val on = isIncognito()
        incognitoBtn.colorFilter = android.graphics.PorterDuffColorFilter(
            if (on) Color.parseColor("#7C83FD") else Color.parseColor("#5A5A6E"),
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
}