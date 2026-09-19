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
import android.view.ViewGroup
import android.view.HapticFeedbackConstants
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nemotron.voiceime.R
import org.json.JSONArray
import org.json.JSONObject

class ChatsListActivity : Activity() {
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshLoop = object : Runnable { override fun run() { refresh(); refreshHandler.postDelayed(this, 1000) } }

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
        if (!intent.getBooleanExtra("openDrawer", false)) {
            startActivity(Intent(this, ChatActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_chats)
        ConversationStore.init(this)
        ConversationStore.purgeEmpty()
        val prefs = getSharedPreferences("hermes_chat", Context.MODE_PRIVATE)
        val savedUrl = prefs.getString("server_url", null)
        val base = if (savedUrl.isNullOrBlank() || !savedUrl.startsWith("https://adarlpz-2.tail4988cb.ts.net")) {
            prefs.edit().putString("server_url", "https://adarlpz-2.tail4988cb.ts.net").apply()
            "https://adarlpz-2.tail4988cb.ts.net"
        } else savedUrl
        chat = ChatClient(base)

        recycler = findViewById(R.id.recyclerChats)
        emptyView = findViewById(R.id.emptyView)
        incognitoBtn = findViewById(R.id.incognitoBtn)
        recycler.visibility = View.GONE
        emptyView.visibility = View.GONE
        findViewById<ImageButton>(R.id.fabNew).visibility = View.GONE
        val homeInput = findViewById<EditText>(R.id.homeInput)
        findViewById<ImageButton>(R.id.homeSend).setOnClickListener { haptic(it); sendHomeMessage(homeInput) }
        homeInput.setOnEditorActionListener { _, _, _ -> sendHomeMessage(homeInput); true }
        findViewById<ImageButton>(R.id.menuBtn).setOnClickListener { haptic(it); showMenu() }

        adapter = ChatListAdapter(list, ::openConv, ::deleteConv, ::renameConv, { _, c -> togglePin(c) })
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter
        incognitoBtn.setOnClickListener {
            haptic(it)
            startActivity(Intent(this, ChatActivity::class.java).putExtra("incognito", true))
        }
        incognitoBtn.setOnLongClickListener {
            Toast.makeText(this, "Incognito chat: discarded on exit", Toast.LENGTH_SHORT).show()
            true
        }

        val fab: ImageButton = findViewById(R.id.fabNew)
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
        if (intent.getBooleanExtra("openDrawer", false)) {
            window.decorView.postDelayed({ showMenu() }, 50)
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
        refreshHandler.postDelayed(refreshLoop, 2000)
    }

    override fun onPause() {
        refreshHandler.removeCallbacks(refreshLoop)
        super.onPause()
    }

    private fun refresh() {
        ConversationStore.purgeEmpty()
        if (!hasRemoteSnapshot) renderChats(ConversationStore.list())
        chat.conversations({ arr ->
            val remote = parseConversations(arr).sortedWith(compareByDescending<Conversation> { it.createdAt }.thenBy { it.id })
            runOnUiThread {
                ConversationStore.replaceRemote(remote)
                hasRemoteSnapshot = true
                renderChats(remote)
            }
        }, {})
    }

    private fun renderChats(items: List<Conversation>) {
        val signature = items.joinToString("|") { "${it.id}:${it.title}:${it.createdAt}:${it.source}:${it.displayName}:${it.pinned}" }
        if (signature == renderedSignature) return
        renderedSignature = signature
        adapter.replaceItems(items)
        list.clear()
        list.addAll(items)
        emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openConv(c: Conversation) {
        startActivity(Intent(this, ChatActivity::class.java).putExtra("convId", c.id))
    }

    private fun deleteConv(c: Conversation) {
        ConversationStore.delete(c.id)
        list.removeAll { it.id == c.id }
        renderedSignature = ""
        adapter.replaceItems(list)
        emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        chat.deleteConversation(c.id, {}, {})
    }

    private fun togglePin(c: Conversation) {
        haptic(recycler)
        c.pinned = !c.pinned
        ConversationStore.save(c)
        renderedSignature = ""
        renderChats(ConversationStore.list())
    }

    private fun renameConv(c: Conversation) {
        val input = EditText(this).apply { setText(c.title); selectAll(); hint = "Conversation name" }
        AlertDialog.Builder(this).setTitle("Rename conversation").setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val title = input.text.toString().trim()
                if (title.isNotEmpty()) { c.title = title; ConversationStore.save(c); refresh() }
            }.show()
    }

    private fun showChatActions(anchor: View, conversation: Conversation) {
        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#111C2D"))
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), Color.parseColor("#34547E"))
            }
        }
        lateinit var popup: PopupWindow
        fun row(icon: Int, label: String, color: Int, action: () -> Unit): View = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            setPadding(dp(10), dp(9), dp(18), dp(9))
            addView(ImageView(context).apply {
                setImageResource(icon)
                setColorFilter(color)
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
            })
            addView(TextView(context).apply {
                text = label
                textSize = 15f
                setTextColor(Color.WHITE)
                setPadding(dp(12), 0, 0, 0)
            })
            setOnClickListener { haptic(this); action(); popup.dismiss() }
        }
        menu.addView(row(R.drawable.ic_rename, "Rename", Color.parseColor("#9BC4FF")) { renameConv(conversation) })
        menu.addView(row(R.drawable.ic_trash, "Delete conversation", Color.parseColor("#FF9A9A")) { deleteConv(conversation) })
        popup = PopupWindow(menu, dp(220), ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            elevation = dp(18).toFloat()
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
        }
        menu.post { popup.showAsDropDown(anchor, dp(18), -anchor.height - dp(92)) }
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
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(26), dp(30), dp(26), dp(24)) }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(this).apply { text = "adarbot"; textSize = 26f; setTextColor(Color.WHITE); setTypeface(null, android.graphics.Typeface.BOLD); layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        top.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_close); setColorFilter(Color.WHITE); background = ColorDrawable(Color.TRANSPARENT)
            contentDescription = "Close menu"
        }, LinearLayout.LayoutParams(dp(52), dp(52)))
        panel.addView(top)
        panel.addView(drawerRow(R.drawable.ic_plus, "New chat") { (root.tag as? PopupWindow)?.dismiss(); createNewChat() })
        panel.addView(drawerRow(R.drawable.ic_search, "Search chats") { (root.tag as? PopupWindow)?.dismiss(); searchChats() })
        panel.addView(drawerRow(R.drawable.ic_ghost, "Incognito chat") { (root.tag as? PopupWindow)?.dismiss(); startActivity(Intent(this, ChatActivity::class.java).putExtra("incognito", true)) })
        panel.addView(TextView(this).apply { text = "adarbot"; textSize = 14f; setTextColor(Color.parseColor("#777B8A")); setPadding(dp(14), dp(28), 0, dp(8)) })
        panel.addView(drawerRow(R.drawable.ic_server, "My NAS and server") { Toast.makeText(this, "192.168.0.2 · connected", Toast.LENGTH_SHORT).show() })
        panel.addView(drawerRow(R.drawable.ic_profile, "Profile and settings") { Toast.makeText(this, "adarbot profile", Toast.LENGTH_SHORT).show() })
        val chats = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, 0) }
        chats.addView(TextView(this).apply { text = "Conversations"; textSize = 14f; setTextColor(Color.parseColor("#777B8A")); setPadding(dp(14), dp(10), 0, dp(6)) })
        list.forEach { c ->
            val row = drawerRow(R.drawable.ic_profile, c.title) {
                (root.tag as? PopupWindow)?.dismiss()
                openConv(c)
            }
            row.setOnLongClickListener {
                showChatActions(row, c)
                true
            }
            chats.addView(row)
        }
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = true
            addView(chats, ViewGroup.LayoutParams(-1, -2))
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }
        panel.addView(scroll)
        root.addView(panel, FrameLayout.LayoutParams(-1, -1))
        val popup = PopupWindow(root, -1, -1, true).apply {
            setBackgroundDrawable(ColorDrawable(Color.BLACK)); isOutsideTouchable = true; elevation = dp(12).toFloat()
            setOnDismissListener {
                if (intent.getBooleanExtra("openDrawer", false) && !isFinishing) finish()
            }
        }
        root.tag = popup
        top.getChildAt(1).setOnClickListener { popup.dismiss() }
        popup.showAtLocation(findViewById(android.R.id.content), Gravity.FILL, 0, 0)
        panel.post {
            panel.translationX = -panel.width.toFloat()
            panel.animate()
                .translationX(0f)
                .setDuration(260L)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }
    }

    private fun drawerRow(icon: Int, label: String, click: () -> Unit): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL; isClickable = true; setPadding(dp(14), dp(13), dp(14), dp(13))
        addView(ImageView(context).apply { setImageResource(icon); setColorFilter(Color.parseColor("#E8EBF5")); layoutParams = LinearLayout.LayoutParams(dp(30), dp(30)) })
        addView(TextView(context).apply { text = label; textSize = 18f; setTextColor(Color.WHITE); setPadding(dp(18), 0, 0, 0) })
        setOnClickListener { haptic(this); click() }
    }

    private fun createNewChat() {
        chat.createConversation({ json -> runOnUiThread { val c = parseConversation(json); ConversationStore.save(c); openConv(c) } }, { runOnUiThread { openConv(ConversationStore.create()) } })
    }

    private fun sendHomeMessage(input: EditText) {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        input.setText("")
        chat.createConversation({ json ->
            val c = parseConversation(json)
            runOnUiThread {
                ConversationStore.save(c)
                startActivity(Intent(this, ChatActivity::class.java).putExtra("convId", c.id).putExtra("draft", text))
            }
        }, {
            runOnUiThread { startActivity(Intent(this, ChatActivity::class.java).putExtra("draft", text)) }
        })
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
        val input = EditText(this).apply { hint = "Search by name"; setSingleLine(true); setPadding(32, 8, 32, 8) }
        AlertDialog.Builder(this).setTitle("Search chats").setView(input).setPositiveButton("Search") { _, _ ->
            val query = input.text.toString().trim().lowercase()
            val filtered = if (query.isEmpty()) ConversationStore.list() else ConversationStore.list().filter { it.title.lowercase().contains(query) }
            list.clear(); list.addAll(filtered); adapter.notifyDataSetChanged(); emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }.setNegativeButton("Cancel", null).show()
    }

    private fun parseConversations(arr: JSONArray): List<Conversation> = buildList {
        for (i in 0 until arr.length()) add(parseConversation(arr.getJSONObject(i)))
    }.sortedWith(compareByDescending<Conversation> { it.createdAt }.thenBy { it.id })

    private fun parseConversation(o: JSONObject): Conversation = Conversation(
        o.optString("id"), o.optString("title", "New chat"),
        o.optLong("updated_at", o.optLong("created_at", System.currentTimeMillis())),
        mutableListOf(), o.optString("source"), o.optString("display_name"), o.optString("chat_id"), o.optBoolean("pinned", false)
    )
}
