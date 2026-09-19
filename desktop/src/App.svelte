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
  let selectedMessage: Message | null = null;
  let pressTimer: number | undefined;

  function escapeHtml(value: string) {
    return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }

  function normalizeMath(value: string) {
    return value
      .replace(/\\frac\{([^{}]*)\}\{([^{}]*)\}/g, '$1⁄$2')
      .replace(/\\sqrt\{([^{}]*)\}/g, '√($1)')
      .replace(/\\partial/g, '∂').replace(/\\nabla/g, '∇').replace(/\\int/g, '∫')
      .replace(/\\sum/g, 'Σ').replace(/\\infty/g, '∞').replace(/\\times/g, '×')
      .replace(/\\cdot/g, '·').replace(/\\approx/g, '≈').replace(/\\pm/g, '±')
      .replace(/\\leq/g, '≤').replace(/\\geq/g, '≥').replace(/\\neq/g, '≠')
      .replace(/\\equiv/g, '≡').replace(/\\pi/g, 'π').replace(/\\theta/g, 'θ')
      .replace(/\\alpha/g, 'α').replace(/\\beta/g, 'β').replace(/\\gamma/g, 'γ')
      .replace(/\\Delta/g, 'Δ').replace(/\\lambda/g, 'λ').replace(/\\mu/g, 'μ')
      .replace(/\\quad/g, ' ').replace(/\\qquad/g, '  ').replace(/\\,/g, ' ')
      .replace(/\\left|\\right/g, '').replace(/\\text\{([^{}]*)\}/g, '$1')
      .replace(/\\mathrm\{([^{}]*)\}/g, '$1').replace(/\\\\/g, '\n')
      .replace(/\^\{([^{}]*)\}/g, '<sup>$1</sup>').replace(/_\{([^{}]*)\}/g, '<sub>$1</sub>')
      .replace(/\^([A-Za-z0-9]+)/g, '<sup>$1</sup>').replace(/_([A-Za-z0-9]+)/g, '<sub>$1</sub>')
      .replace(/\\[A-Za-z]+/g, '').replace(/[{}]/g, '').trim();
  }

  function renderMarkdown(source: string) {
    let body = source.replace(/\r\n/g, '\n');
    const math: string[] = [];
    const saveMath = (value: string, block: boolean) => {
      const content = normalizeMath(value);
      const html = block
        ? `<div class="math-block">${content.replace(/\n/g, '<br>')}</div>`
        : `<span class="math-inline">${content}</span>`;
      math.push(html);
      return `@@MATH${math.length - 1}@@`;
    };
    body = body.replace(/\$\$([\s\S]+?)\$\$/g, (_, v) => saveMath(v, true));
    body = body.replace(/\\\[([\s\S]+?)\\\]/g, (_, v) => saveMath(v, true));
    body = body.replace(/\$([^$\n]+)\$/g, (_, v) => saveMath(v, false));
    let html = escapeHtml(body);
    html = html.replace(/^#{1,6}\s+(.+)$/gm, '<strong>$1</strong>');
    html = html.replace(/^[-*]\s+/gm, '• ');
    html = html.replace(/```(?:[\w+-]+)?\n?([\s\S]*?)```/g, '<pre>$1</pre>');
    html = html.replace(/`([^`]+)`/g, '<code>$1</code>');
    html = html.replace(/\*\*([^*]+)\*\*|__([^_]+)__/g, (_, a, b) => `<strong>${a || b}</strong>`);
    html = html.replace(/(?<!\*)\*([^*]+)\*(?!\*)|(?<!_)_([^_]+)_(?!_)/g, (_, a, b) => `<em>${a || b}</em>`);
    html = html.replace(/\n/g, '<br>');
    math.forEach((value, index) => { html = html.replace(`@@MATH${index}@@`, value); });
    return html;
  }

  function openMessageActions(message: Message) {
    selectedMessage = message;
  }

  function startMessagePress(message: Message) {
    window.clearTimeout(pressTimer);
    pressTimer = window.setTimeout(() => openMessageActions(message), 550);
  }

  function stopMessagePress() {
    window.clearTimeout(pressTimer);
  }

  async function copyMessage(message: Message) {
    await navigator.clipboard?.writeText(message.content);
    selectedMessage = null;
  }

  function readMessage(message: Message) {
    window.speechSynthesis.cancel();
    window.speechSynthesis.speak(new SpeechSynthesisUtterance(message.content));
    selectedMessage = null;
  }

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
    selectedMessage = null;
    messagesError = '';
    menuOpen = false;
    input = '';
  }

  function selectChat(chat: Chat) {
    activeChat = chat;
    messages = [];
    selectedMessage = null;
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

    <div class="conversation" on:click={() => selectedMessage = null}>
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
                <article class:mine={message.role === 'user'} class="message-row"
                  on:pointerdown={() => startMessagePress(message)}
                  on:pointerup={stopMessagePress}
                  on:pointerleave={stopMessagePress}
                  on:contextmenu|preventDefault={() => openMessageActions(message)}>
                  <div class="message-bubble">
                    <div class="message-content">{@html renderMarkdown(message.content)}</div>
                    <div class="message-meta">{message.model || activeChat.model || 'adarbot'}</div>
                  </div>
                  {#if selectedMessage === message}
                    <div class="message-actions" on:click|stopPropagation>
                      <button on:click={() => copyMessage(message)}>Copiar</button>
                      <button on:click={() => readMessage(message)}>Leer en voz alta</button>
                      <span>{message.model || activeChat.model || 'adarbot'}</span>
                    </div>
                  {/if}
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
