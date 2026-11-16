package com.nemotron.voiceime.chat

import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.FileTime

class ConversationStoreTest {
    @get:Rule val directory = TemporaryFolder()
    private lateinit var file: File
    private val conversation = Conversation("remote-1", "Trip", 123L,
        mutableListOf(ChatMessage("user", "hello", 124L)), source = "remote", pinned = true)

    @Before fun initialize() {
        ConversationStore.initializeStorage(directory.root)
        file = File(directory.root, "conversations.json")
        ConversationStore.save(conversation)
        Files.setLastModifiedTime(file.toPath(), FileTime.fromMillis(1_000_000L))
    }

    @Test fun unchangedRefreshDoesNotRewriteConversationHistory() {
        val before = file.readText()
        ConversationStore.purgeEmpty()
        ConversationStore.replaceRemote(listOf(conversation.copy(title = "Server title", messages = mutableListOf(), pinned = false)))
        ConversationStore.save(conversation.copy())
        ConversationStore.delete("not-present")
        assertEquals(before, file.readText())
        assertEquals(1_000_000L, file.lastModified())
        assertEquals(conversation, ConversationStore.get(conversation.id))
    }

    @Test fun newMessageIsSavedWithoutLosingPinnedStateOrTitle() {
        val changed = conversation.copy(messages = conversation.messages.toMutableList().apply {
            add(ChatMessage("assistant", "hi", 125L))
        })
        ConversationStore.save(changed)
        assertEquals(changed, ConversationStore.get(conversation.id))
        assertNotEquals(1_000_000L, file.lastModified())
    }

    @Test fun emptyLocalDraftIsPurgedButRemoteConversationRemains() {
        ConversationStore.save(Conversation("draft", "New chat", 1L))
        ConversationStore.save(Conversation("empty-remote", "New chat", 2L, source = "remote"))
        ConversationStore.purgeEmpty()
        assertNull(ConversationStore.get("draft"))
        assertNotNull(ConversationStore.get("empty-remote"))
        assertEquals(conversation, ConversationStore.get(conversation.id))
    }
}
