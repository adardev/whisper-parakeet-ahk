<script lang="ts">
  type Chat = { id: string; title: string; updated_at?: number; model?: string; remote?: boolean };

  const server = 'https://adarlpz-2.tail4988cb.ts.net';
  let chats: Chat[] = [];
  let activeChat: Chat | null = null;
  let input = '';
  let connected = false;
  let loading = true;
  let menuOpen = false;

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
    menuOpen = false;
    input = '';
  }

  function selectChat(chat: Chat) {
    activeChat = chat;
    menuOpen = false;
  }

  function submit() {
    if (!input.trim()) return;
    // El envío completo se conecta en la siguiente capa del cliente.
    input = '';
  }

  loadChats();
  const refresh = window.setInterval(loadChats, 5000);
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
    <div class="sidebar-footer"><span class:online={connected} class="status-dot"></span>{connected ? 'servidor conectado' : 'sin conexión'}</div>
  </aside>

  <section class="workspace">
    <header class="topbar">
      <button class="icon menu" on:click={() => menuOpen = !menuOpen}>☰</button>
      <div class="title"><span class:online={connected} class="status-dot"></span><strong>adarbot</strong></div>
      <button class="avatar" aria-label="Perfil">a</button>
    </header>

    <div class="conversation">
      {#if activeChat}
        <div class="empty-state compact"><span class="eyebrow">CONVERSACIÓN</span><h1>{activeChat.title}</h1><p>Los mensajes aparecerán aquí.</p></div>
      {:else}
        <div class="empty-state"><span class="spark">✦</span><h1>¿Qué hacemos hoy?</h1><p>Pregunta lo que quieras a adarbot.</p></div>
      {/if}
    </div>

    <form class="composer" on:submit|preventDefault={submit}>
      <button type="button" class="attach" aria-label="Adjuntar">⌕</button>
      <input bind:value={input} placeholder="Pregúntale a adarbot…" aria-label="Mensaje" />
      <button type="button" class="mic" aria-label="Micrófono">◉</button>
      <button type="submit" class="send" aria-label="Enviar">➤</button>
    </form>
  </section>
</main>
