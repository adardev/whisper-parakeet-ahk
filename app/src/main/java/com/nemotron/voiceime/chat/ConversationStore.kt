package com.nemotron.voiceime.chat

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ChatMessage(val role: String, val content: String, val ts: Long = System.currentTimeMillis())

data class Conversation(
    val id: String,
    var title: String,
    val createdAt: Long,
    val messages: MutableList<ChatMessage> = mutableListOf()
)

object ConversationStore {
    private lateinit var ctx: Context
    private val file by lazy { File(ctx.filesDir, "conversations.json") }

    fun init(context: Context) {
        ctx = context.applicationContext
    }

    private fun load(): MutableList<Conversation> {
        if (!file.exists()) return mutableListOf()
        return try {
            val arr = JSONArray(file.readText())
            val list = mutableListOf<Conversation>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val msgs = mutableListOf<ChatMessage>()
                val ma = o.optJSONArray("messages") ?: JSONArray()
                for (j in 0 until ma.length()) {
                    val m = ma.getJSONObject(j)
                    msgs.add(ChatMessage(m.optString("role"), m.optString("content"), m.optLong("ts")))
                }
                list.add(Conversation(o.optString("id"), o.optString("title"), o.optLong("createdAt"), msgs))
            }
            list
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    private fun persist(list: List<Conversation>) {
        try {
            val arr = JSONArray()
            for (c in list) {
                val o = JSONObject().apply {
                    put("id", c.id)
                    put("title", c.title)
                    put("createdAt", c.createdAt)
                }
                val msgs = JSONArray()
                for (m in c.messages) {
                    msgs.put(JSONObject().apply {
                        put("role", m.role)
                        put("content", m.content)
                        put("ts", m.ts)
                    })
                }
                o.put("messages", msgs)
                arr.put(o)
            }
            file.writeText(arr.toString())
        } catch (e: Exception) {
        }
    }

    fun list(): List<Conversation> = load().sortedByDescending { it.createdAt }

    fun get(id: String): Conversation? = load().find { it.id == id }

    fun create(): Conversation {
        val c = Conversation(System.currentTimeMillis().toString(), "Nuevo chat", System.currentTimeMillis())
        val l = load()
        l.add(0, c)
        persist(l)
        return c
    }

    fun save(c: Conversation) {
        val l = load()
        val i = l.indexOfFirst { it.id == c.id }
        if (i >= 0) l[i] = c else l.add(0, c)
        persist(l)
    }

    fun delete(id: String) {
        persist(load().filterNot { it.id == id })
    }

    fun replaceRemote(remote: List<Conversation>) {
        persist(remote)
    }
}
