<script lang="ts">
  type Chat = { id: string; title: string; updated_at?: number; model?: string; remote?: boolean };
  type Message = { id?: number | string; role: string; content: string; model?: string; created_at?: number };

  const server = 'https://adarlpz-2.tail4988cb.ts.net';
  let chats: Chat[] = [];
  let activeChat: Chat | null = null;
  let input = '';
  let connected = false;
  let loading = true;
  let menuOpen = false;
  let messages: Message[] = [];
  let messagesLoading = false;
  let messagesError = '';
  let messageRequest = 0;

  async function loadMessages(id: string) {
    const request = ++messageRequest;
    messagesLoading = true;
    messagesError = '';
    try {
      const response = await fetch(`${server}/api/conversations/${encodeURIComponent(id)}`, { cache: 'no-store' });
      if (!response.ok) throw new Error('conversation');
      const conversation = await response.json();
      if (request === messageRequest) {
        messages = Array.isArray(conversation.messages) ? conversation.messages : [];
        connected = true;
      }
    } catch {
      if (request === messageRequest) messagesError = 'No se pudieron cargar los mensajes.';
    } finally {
      if (request === messageRequest) messagesLoading = false;
    }
  }

  async function loadChats() {
    loading = true;
    try {
      const response = await fetch(`${server}/api/conversations`, { cache: 'no-store' });
      if (!response.ok) throw new Error('server');
      chats = (await response.json()).conversations ?? [];
      connected = true;
    } catch {
      connected = false;
    } finally {
      loading = false;
    }
  }

  function newChat() {
    activeChat = null;
    messages = [];
    messagesError = '';
    menuOpen = false;
    input = '';
  }

  function selectChat(chat: Chat) {
    activeChat = chat;
    messages = [];
    messagesError = '';
    menuOpen = false;
    loadMessages(chat.id);
  }

  function submit() {
    if (!input.trim()) return;
    // El envío completo se conecta en la siguiente capa del cliente.
    input = '';
  }

  loadChats();
  const refresh = window.setInterval(async () => {
    await loadChats();
    if (activeChat) await loadMessages(activeChat.id);
  }, 5000);
</script>

<svelte:head><title>adarbot</title></svelte:head>

<main class="shell">
  <aside class:open={menuOpen} class="sidebar">
    <div class="brand-row"><span class="brand-mark">a</span><strong>adarbot</strong><button class="icon close" on:click={() => menuOpen = false}>×</button></div>
    <button class="action primary" on:click={newChat}><span>＋</span>Nuevo chat</button>
    <button class="action" on:click={() => {}}><span>⌕</span>Buscar chats</button>
    <div class="section-label">Conversaciones</div>
    <div class="chat-list">
      {#if loading}<div class="muted">Cargando chats…</div>{/if}
      {#each chats as chat (chat.id)}
        <button class:active={activeChat?.id === chat.id} class="chat-item" on:click={() => selectChat(chat)}>
          <span>{chat.title || 'Nuevo chat'}</span><small>{chat.model || 'adarbot'}</small>
        </button>
      {/each}
    </div>
    <div class="sidebar-footer" aria-hidden="true"></div>
  </aside>

  <section class="workspace">
    <header class="topbar">
      <button class="icon menu" on:click={() => menuOpen = !menuOpen}>☰</button>
      <div class="title"><span class:online={connected} class="status-dot"></span><strong>adarbot</strong></div>
      <button class="avatar" aria-label="Perfil">a</button>
    </header>

    <div class="conversation">
      {#if activeChat}
        <div class="chat-view">
          <div class="chat-heading"><span class="eyebrow">CONVERSACIÓN</span><h1>{activeChat.title}</h1></div>
          {#if messagesLoading && messages.length === 0}
            <div class="loading-state">Cargando mensajes…</div>
          {:else if messagesError}
            <div class="loading-state error">{messagesError}</div>
          {:else if messages.length === 0}
            <div class="empty-state compact"><p>Los mensajes aparecerán aquí.</p></div>
          {:else}
            <div class="message-list">
              {#each messages as message (message.id ?? `${message.role}-${message.created_at}-${message.content.slice(0, 12)}`)}
                <article class:mine={message.role === 'user'} class="message-row">
                  <div class="message-bubble">
                    <div class="message-content">{message.content}</div>
                    <div class="message-meta">{message.model || activeChat.model || 'adarbot'}</div>
                  </div>
                </article>
              {/each}
            </div>
          {/if}
        </div>
      {:else}
        <div class="empty-state"><span class="spark">✦</span><h1>¿Qué hacemos hoy?</h1><p>Pregunta lo que quieras a adarbot.</p></div>
      {/if}
    </div>

    <form class="composer" on:submit|preventDefault={submit}>
      <button type="button" class="attach" aria-label="Adjuntar"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m20.5 11.5-8.7 8.7a5 5 0 0 1-7.1-7.1l9.2-9.2a3.5 3.5 0 0 1 5 5l-9.2 9.2a2 2 0 0 1-2.8-2.8l8.3-8.3"/></svg></button>
      <input bind:value={input} placeholder="Pregúntale a adarbot…" aria-label="Mensaje" />
      <button type="button" class="mic" aria-label="Micrófono"><svg viewBox="0 0 24 24" aria-hidden="true"><rect x="9" y="3" width="6" height="11" rx="3"/><path d="M5.5 11a6.5 6.5 0 0 0 13 0M12 17.5V21M9 21h6"/></svg></button>
      <button type="submit" class="send" aria-label="Enviar"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m3 4 18 8-18 8 3-8-3-8Z"/><path d="M6 12h15"/></svg></button>
    </form>
  </section>
</main>
