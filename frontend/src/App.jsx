import { useEffect, useMemo, useRef, useState, useCallback } from 'react'
import brandPeople from './assets/brand-people.png'
import advisorAvatar from './assets/advisor-avatar.png'
import botAvatar from './assets/bot-avatar.png'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/counselor/api'
const AUTH_URL = import.meta.env.VITE_AUTH_URL || '/counselor/auth/login'
const AUTH_LOGOUT_URL = import.meta.env.VITE_AUTH_LOGOUT_URL || '/counselor/auth/logout'
const AUTH_CALLBACK_URL = import.meta.env.VITE_AUTH_CALLBACK_URL || '/counselor/?auth_callback=1'
const AUTH_AUTO_REDIRECT = import.meta.env.VITE_AUTH_AUTO_REDIRECT !== 'false'
const AGENT_ID = import.meta.env.VITE_AGENT_ID || '2036332866958286848'

const TOKEN_KEY = 'counselor_token'
const USER_KEY = 'counselor_user'
const MAX_CHAT_FILE_COUNT = 10
const MAX_CHAT_FILE_SIZE = 100 * 1024 * 1024
const ALLOWED_CHAT_FILE_TYPES = new Set(['docx', 'xls', 'xlsx', 'csv', 'pdf', 'txt', 'zip', 'html'])

let idSeed = 0

class AuthExpiredError extends Error {
  constructor(message = '登录状态已失效，请重新登录') {
    super(message)
    this.name = 'AuthExpiredError'
  }
}

function createId(prefix) {
  idSeed += 1
  return `${prefix}-${Date.now()}-${idSeed}`
}

function unwrapApiData(payload) {
  if (payload && typeof payload === 'object' && 'code' in payload && 'data' in payload) {
    return payload.data
  }
  return payload
}

function getApiMessage(payload, fallback = '请求失败') {
  if (payload && typeof payload === 'object' && typeof payload.message === 'string' && payload.message.trim()) {
    return payload.message
  }
  return fallback
}

function parseCallbackUser(value) {
  if (!value) return null

  const candidates = [value]
  try {
    const decoded = decodeURIComponent(value)
    if (decoded !== value) {
      candidates.push(decoded)
    }
  } catch {
    // URLSearchParams may already have decoded the value.
  }

  for (const candidate of candidates) {
    try {
      return JSON.parse(candidate)
    } catch {
      // Try the next representation.
    }
  }
  return null
}

function parseResponseError(text, fallback) {
  if (!text) return fallback
  try {
    return getApiMessage(JSON.parse(text), fallback)
  } catch {
    return text
  }
}

function isApiPayloadOk(payload) {
  if (!payload || typeof payload !== 'object' || !('code' in payload)) {
    return true
  }
  return Number(payload.code) === 200
}

function normalizeUser(rawUser) {
  const source = rawUser?.user ?? rawUser
  if (!source || typeof source !== 'object') {
    return null
  }

  const id = source.id ?? source.userId ?? source.portalUserId ?? ''
  const username = source.username ?? source.account ?? source.userName ?? source.loginName ?? ''
  const displayName = source.displayName ?? source.name ?? source.realName ?? source.nickname ?? ''
  const email = source.email ?? source.mail ?? ''
  const phone = source.phone ?? source.mobile ?? source.mobilePhone ?? ''

  return {
    ...source,
    id,
    username,
    displayName,
    email,
    phone,
    avatar: source.avatar ?? source.avatarUrl ?? source.headImg ?? '',
    userType: source.userType ?? source.type ?? '',
  }
}

function getUserDisplayName(user) {
  return user?.displayName || user?.username || user?.email || user?.phone || user?.id || '当前账户'
}

function getUserSecondaryText(user) {
  if (!user) return ''
  if (user.username && user.username !== user.displayName) return user.username
  if (user.email) return user.email
  if (user.phone) return user.phone
  if (user.id) return `ID ${user.id}`
  return ''
}

function resolveBackendAssetUrl(path) {
  if (!path) return botAvatar
  if (/^(https?:)?\/\//i.test(path) || /^(data|blob):/i.test(path)) {
    return path
  }

  const apiRoot = API_BASE_URL.replace(/\/api\/?$/, '')
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  return `${apiRoot}${normalizedPath}`
}

function parseAttachments(value) {
  if (!value) return []
  if (Array.isArray(value)) return value
  if (typeof value !== 'string') return []
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function getFileType(fileName = '') {
  const cleanName = String(fileName || '')
  const index = cleanName.lastIndexOf('.')
  if (index === -1 || index === cleanName.length - 1) return 'file'
  return cleanName.slice(index + 1).toLowerCase()
}

function formatFileSize(value) {
  const size = Number(value)
  if (!Number.isFinite(size) || size <= 0) return '未知大小'
  if (size >= 1024 * 1024) return `${(size / 1024 / 1024).toFixed(size >= 10 * 1024 * 1024 ? 0 : 1)} MB`
  if (size >= 1024) return `${(size / 1024).toFixed(size >= 10 * 1024 ? 0 : 1)} KB`
  return `${size} B`
}

function normalizeChatFile(file) {
  if (!file) return null
  const name = file.originalName || file.name || '未命名文件'
  return {
    ...file,
    id: file.id || file.uid || file.url || name,
    name,
    type: file.type || getFileType(name),
    size: typeof file.size === 'string' && /\D/.test(file.size) ? file.size : formatFileSize(file.size),
    rawSize: Number(file.size) || 0,
    status: file.status || 'ready',
  }
}

function validateSelectedFiles(files, currentFiles = []) {
  if (!files.length) return ''
  if (currentFiles.length + files.length > MAX_CHAT_FILE_COUNT) {
    return '每轮对话最多上传 10 个文件'
  }
  for (const file of files) {
    if (file.size > MAX_CHAT_FILE_SIZE) {
      return `文件 ${file.name} 超过 100M`
    }
    const type = getFileType(file.name)
    if (!ALLOWED_CHAT_FILE_TYPES.has(type)) {
      return '仅支持 docx、xls、xlsx、csv、pdf、txt、zip、html 文件'
    }
  }
  return ''
}

function normalizeMessage(message) {
  const content = normalizeMessageContent(message)
  const streaming = Boolean(message?.streaming)
  const streamingEnd = message?.streamingEnd !== false
  const attachments = parseAttachments(message?.attachments).map(normalizeChatFile).filter(Boolean)

  return {
    id: message?.id || createId('message'),
    role: message?.role === 'assistant' ? 'assistant' : 'user',
    text: content,
    content,
    attachments,
    status: streaming && !streamingEnd ? 'streaming' : 'done',
    createTime: message?.createTime ?? null,
  }
}

function normalizeConversation(conversation, existingSession) {
  if (!conversation?.id) {
    return null
  }

  return {
    id: conversation.id,
    title: conversation.title || '新会话',
    messages: existingSession?.messages || [],
    messagesLoaded: existingSession?.messagesLoaded || false,
    pending: false,
    messageCount: conversation.messageCount ?? existingSession?.messageCount ?? 0,
    lastMessageTime: conversation.lastMessageTime ?? existingSession?.lastMessageTime ?? null,
    createTime: conversation.createTime ?? existingSession?.createTime ?? null,
  }
}

function isTemporarySessionId(sessionId) {
  return !sessionId || String(sessionId).startsWith('session-')
}

function normalizeMessageContent(message) {
  return message?.content ?? message?.text ?? ''
}

function createBlankSession() {
  return {
    id: createId('session'),
    title: '新会话',
    messages: [],
    messagesLoaded: true,
    pending: true,
  }
}

function toRequestMessages(messages, nextUserMessage) {
  return [...messages, nextUserMessage]
    .filter((message) => ['user', 'assistant', 'system'].includes(message.role))
    .map((message) => ({
      role: message.role,
      content: normalizeMessageContent(message),
    }))
    .filter((message) => message.content !== '')
}

function iconPaths(name) {
  const icons = {
    plus: (
      <>
        <circle cx="12" cy="12" r="9" />
        <path d="M12 8v8M8 12h8" />
      </>
    ),
    search: (
      <>
        <circle cx="11" cy="11" r="7" />
        <path d="m20 20-3.2-3.2" />
      </>
    ),
    message: (
      <>
        <path d="M21 12a8 8 0 0 1-8 8H8l-5 3 1.7-5A8 8 0 1 1 21 12Z" />
        <path d="M8 12h.01M12 12h.01M16 12h.01" />
      </>
    ),
    download: (
      <>
        <path d="M12 3v12" />
        <path d="m8 11 4 4 4-4" />
        <path d="M5 18v3h14v-3" />
      </>
    ),
    attach: (
      <path d="m21.4 11.6-8.7 8.7a5.2 5.2 0 0 1-7.4-7.4l9-9a3.4 3.4 0 0 1 4.8 4.8l-9.1 9.1a1.7 1.7 0 0 1-2.4-2.4l8.2-8.2" />
    ),
    send: (
      <>
        <path d="M22 2 11 13" />
        <path d="m22 2-7 20-4-9-9-4Z" />
      </>
    ),
    more: (
      <>
        <circle cx="5" cy="12" r="1.2" fill="currentColor" stroke="none" />
        <circle cx="12" cy="12" r="1.2" fill="currentColor" stroke="none" />
        <circle cx="19" cy="12" r="1.2" fill="currentColor" stroke="none" />
      </>
    ),
    edit: (
      <>
        <path d="M12 20h9" />
        <path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z" />
      </>
    ),
    copy: (
      <>
        <rect x="9" y="9" width="10" height="10" rx="1" />
        <path d="M5 15H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v1" />
      </>
    ),
    chevron: <path d="m6 9 6 6 6-6" />,
    menu: (
      <>
        <path d="M4 6h16" />
        <path d="M4 12h16" />
        <path d="M4 18h16" />
      </>
    ),
    close: (
      <>
        <path d="M18 6 6 18" />
        <path d="m6 6 12 12" />
      </>
    ),
    trash: (
      <>
        <path d="M3 6h18" />
        <path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6" />
        <path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2" />
      </>
    ),
    logout: (
      <>
        <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
        <polyline points="16,17 21,12 16,7" />
        <line x1="21" y1="12" x2="9" y2="12" />
      </>
    ),
  }
  return icons[name]
}

function Icon({ name, className = '' }) {
  return (
    <svg
      className={`icon ${className}`}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {iconPaths(name)}
    </svg>
  )
}

function FileBadge({ type }) {
  return (
    <div className={`file-badge file-badge-${type}`}>
      <span>{String(type || 'FILE').toUpperCase()}</span>
    </div>
  )
}

function HistoryBubbleIcon() {
  return (
    <svg className="history-bubble-icon" viewBox="0 0 16 16" aria-hidden="true">
      <path d="M2.5 3.25h11v8.25H6.25L3.1 13.6v-2.1h-.6z" />
      <path d="M5.25 7.35h.01M8 7.35h.01M10.75 7.35h.01" />
    </svg>
  )
}

function StreamReply({ text, isLoading, isError }) {
  const displayText = text || (isLoading ? '正在输入...' : '')
  return (
    <div className={`stream-reply ${isLoading ? 'is-loading' : ''} ${isError ? 'is-error' : ''}`}>
      {displayText}
    </div>
  )
}

function TypingIndicator() {
  return (
    <article className="assistant-turn typing-turn">
      <img src={advisorAvatar} alt="" className="advisor-avatar" />
      <div className="assistant-content typing-bubble">
        <span />
        <span />
        <span />
      </div>
    </article>
  )
}

function AttachmentCard({ file, onDownload, isDownloading }) {
  return (
    <div
      className={`attachment-card ${isDownloading ? 'is-downloading' : ''}`}
      role="button"
      tabIndex={0}
      aria-label={`下载 ${file.name}`}
      onClick={onDownload}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault()
          onDownload()
        }
      }}
    >
      <FileBadge type={file.type} />
      <div className="attachment-meta">
        <strong>{file.name}</strong>
        <span>{file.size}</span>
      </div>
      <Icon name="download" className="download-icon" />
    </div>
  )
}

function ComposerFileChip({ file, onRemove }) {
  return (
    <div className={`composer-file-chip composer-file-${file.status || 'ready'}`}>
      <FileBadge type={file.type} />
      <div className="composer-file-meta">
        <strong title={file.name}>{file.name}</strong>
        <span>{file.status === 'uploading' ? '解析中...' : file.size}</span>
      </div>
      <button type="button" aria-label={`移除 ${file.name}`} onClick={() => onRemove(file.id)}>
        <Icon name="close" />
      </button>
    </div>
  )
}

function updateMessageInSession(session, messageId, updater) {
  return {
    ...session,
    messages: session.messages.map((message) => (message.id === messageId ? updater(message) : message)),
  }
}

async function readSseJsonLines(response, { signal, onEvent }) {
  const reader = response.body?.getReader()
  if (!reader) {
    throw new Error('浏览器不支持流式响应')
  }

  const decoder = new TextDecoder()
  let buffer = ''

  try {
    while (true) {
      if (signal.aborted) {
        throw new DOMException('Aborted', 'AbortError')
      }

      const { value, done } = await reader.read()
      if (done) {
        break
      }

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split(/\r?\n/)
      buffer = lines.pop() ?? ''

      for (const rawLine of lines) {
        const line = rawLine.trim()
        if (!line) continue

        const payload = line.startsWith('data:') ? line.slice(5).trim() : line
        if (!payload || payload === '[DONE]') continue

        let event
        try {
          event = JSON.parse(payload)
        } catch {
          continue
        }

        const shouldContinue = onEvent(event)
        if (shouldContinue === false) {
          return
        }
      }
    }

    const tail = decoder.decode().trim() || buffer.trim()
    if (tail) {
      const payload = tail.startsWith('data:') ? tail.slice(5).trim() : tail
      if (payload && payload !== '[DONE]') {
        const event = JSON.parse(payload)
        onEvent(event)
      }
    }
  } finally {
    try {
      await reader.cancel()
    } catch {
      // No-op.
    }
  }
}

function UserMenu({ user, onLogout, onRenameSession, onDeleteSession, sessionId }) {
  const [showMenu, setShowMenu] = useState(false)
  const displayName = getUserDisplayName(user)
  const secondaryText = getUserSecondaryText(user)

  return (
    <div className="user-menu-wrapper">
      <button
        className="user-menu-trigger"
        type="button"
        aria-label="用户菜单"
        onClick={() => setShowMenu(!showMenu)}
      >
        <img src={resolveBackendAssetUrl(user?.avatar)} alt="" className="user-avatar" />
        <div className="user-meta">
          <strong title={displayName}>{displayName}</strong>
          <span title={secondaryText}>{secondaryText || '已认证'}</span>
        </div>
        <Icon name="more" className="more-icon" />
      </button>

      {showMenu && (
        <div className="user-menu-dropdown">
          {!isTemporarySessionId(sessionId) && (
            <>
              <button
                type="button"
                className="user-menu-item"
                onClick={() => { onRenameSession(); setShowMenu(false) }}
              >
                <Icon name="edit" className="menu-item-icon" />
                <span>重命名会话</span>
              </button>
              <button
                type="button"
                className="user-menu-item user-menu-item-danger"
                onClick={() => { onDeleteSession(); setShowMenu(false) }}
              >
                <Icon name="trash" className="menu-item-icon" />
                <span>删除会话</span>
              </button>
              <div className="user-menu-divider" />
            </>
          )}
          <button
            type="button"
            className="user-menu-item user-menu-item-danger"
            onClick={() => { onLogout(); setShowMenu(false) }}
          >
            <Icon name="logout" className="menu-item-icon" />
            <span>退出登录</span>
          </button>
        </div>
      )}
    </div>
  )
}

function RenameModal({ isOpen, currentTitle, onConfirm, onCancel }) {
  const [title, setTitle] = useState(currentTitle)

  useEffect(() => {
    if (isOpen) setTitle(currentTitle)
  }, [isOpen, currentTitle])

  if (!isOpen) return null

  return (
    <div className="modal-overlay" role="dialog" aria-modal="true" aria-label="重命名会话">
      <div className="modal-content">
        <h3 className="modal-title">重命名会话</h3>
        <input
          className="modal-input"
          type="text"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="请输入会话标题"
          autoFocus
          onKeyDown={(e) => { if (e.key === 'Enter') onConfirm(title) }}
        />
        <div className="modal-actions">
          <button type="button" className="modal-btn modal-btn-cancel" onClick={onCancel}>取消</button>
          <button type="button" className="modal-btn modal-btn-confirm" onClick={() => onConfirm(title)}>确认</button>
        </div>
      </div>
    </div>
  )
}

function LoginPage({ onLogin, onDevLogin, authError }) {
  return (
    <div className="login-page">
      <div className="login-card">
        <img src={brandPeople} alt="" className="login-brand" />
        <h1 className="login-title">郑大辅导员</h1>
        <p className="login-desc">智能心理健康咨询助手</p>
        {authError ? <p className="login-error">{authError}</p> : null}
        <button type="button" className="login-btn" onClick={onLogin}>
          使用学校门户登录
        </button>
        <button type="button" className="login-btn login-btn-dev" onClick={onDevLogin}>
          本地开发登录
        </button>
        <p className="login-note">登录后将跳转至学校统一身份认证页面</p>
      </div>
    </div>
  )
}

function Sidebar({
  isOpen = false,
  onClose,
  onNewSession,
  searchValue,
  onSearchChange,
  historyList,
  activeSessionId,
  onSelectSession,
  user,
  onLogout,
  onRenameSession,
  onDeleteSession,
}) {
  return (
    <aside className={`sidebar ${isOpen ? 'sidebar-open' : ''}`} aria-label="侧边导航">
      <div className="brand">
        <img src={brandPeople} alt="" className="brand-img" />
        <h1>郑大辅导员</h1>
        <button className="sidebar-close" type="button" aria-label="关闭菜单" onClick={onClose}>
          <Icon name="close" />
        </button>
      </div>

      <button className="new-chat" type="button" onClick={onNewSession}>
        <Icon name="plus" />
        <span>开启新会话</span>
      </button>

      <div className="history-label">历史记录</div>
      <label className="search-box" aria-label="搜索历史记录">
        <Icon name="search" />
        <input
          value={searchValue}
          onChange={(event) => onSearchChange(event.target.value)}
          placeholder="请输入"
          type="text"
        />
      </label>

      <nav className="history-list" aria-label="历史记录">
        {historyList.map((item) => (
          <div
            className={`history-item ${item.id === activeSessionId ? 'active' : ''}`}
            key={item.id}
            role="button"
            tabIndex={0}
            onClick={() => onSelectSession(item.id)}
            onKeyDown={(event) => {
              if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault()
                onSelectSession(item.id)
              }
            }}
          >
            <HistoryBubbleIcon />
            <span>{item.title}</span>
          </div>
        ))}
        {historyList.length === 0 && (
          <p className="history-empty">暂无历史记录</p>
        )}
      </nav>

      <UserMenu
        user={user}
        onLogout={onLogout}
        onRenameSession={onRenameSession}
        onDeleteSession={onDeleteSession}
        sessionId={activeSessionId}
      />
    </aside>
  )
}

function ChatPage({
  onMenuClick,
  session,
  draft,
  onDraftChange,
  onSendMessage,
  onStopMessage,
  onQuickPrompt,
  onFileSelect,
  onRemovePendingFile,
  onAttachmentDownload,
  downloadingFileId,
  pendingFiles,
  isUploadingFiles,
  isTyping,
  canvasRef,
  networkError,
}) {
  const fileInputRef = useRef(null)
  const handleKeyDown = (event) => {
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault()
      onSendMessage()
    }
  }
  const hasUploadingFile = pendingFiles.some((file) => file.status === 'uploading')

  return (
    <main className="chat-shell">
      <header className="mobile-header">
        <button className="menu-button" type="button" aria-label="打开菜单" onClick={onMenuClick}>
          <Icon name="menu" />
        </button>
        <div className="mobile-brand">
          <img src={brandPeople} alt="" />
          <span>郑大辅导员</span>
        </div>
      </header>

      <section className="chat-canvas" ref={canvasRef}>
        {networkError ? (
          <div className="network-error" role="alert">
            {networkError}
          </div>
        ) : null}

        {(!session?.messages || session.messages.length === 0) && (
          <div className="empty-canvas-hint">
            <img src={advisorAvatar} alt="" className="empty-avatar" />
            <p>你好！有什么我可以帮助你的吗？</p>
          </div>
        )}

        {(session?.messages || []).map((message) => {
          if (message.role === 'user') {
            return (
              <div className="user-row" key={message.id}>
                <div className="user-bubble-stack">
                  {message.attachments?.length ? (
                    <div className="message-attachments">
                      {message.attachments.map((file) => (
                        <AttachmentCard
                          file={normalizeChatFile(file)}
                          key={file.id || file.name}
                          onDownload={() => onAttachmentDownload(file)}
                          isDownloading={downloadingFileId === file.name}
                        />
                      ))}
                    </div>
                  ) : null}
                  <div className="user-bubble">{message.text}</div>
                </div>
                <img src={botAvatar} alt="" className="bot-avatar" />
              </div>
            )
          }

          return (
            <article className="assistant-turn" key={message.id}>
              <img src={advisorAvatar} alt="" className="advisor-avatar" />
              <div className="assistant-content">
                <StreamReply
                  text={normalizeMessageContent(message)}
                  isLoading={message.status === 'loading'}
                  isError={message.status === 'error'}
                />

                {message.attachments?.length ? (
                  <div className="attachments">
                    {message.attachments.map((file) => (
                      <AttachmentCard
                        file={file}
                        key={file.name}
                        onDownload={() => onAttachmentDownload(file)}
                        isDownloading={downloadingFileId === file.name}
                      />
                    ))}
                  </div>
                ) : null}

                {message.showActions ? (
                  <div className="assistant-actions">
                    <button type="button" aria-label="复制回复">
                      <Icon name="copy" />
                    </button>
                    <button type="button" aria-label="展开更多">
                      <Icon name="chevron" />
                    </button>
                  </div>
                ) : null}
              </div>
            </article>
          )
        })}

      </section>

      <button className="quick-action" aria-label="快捷提问" type="button" onClick={onQuickPrompt}>
        <Icon name="edit" />
      </button>

      <div className="composer">
        {pendingFiles.length ? (
          <div className="composer-files" aria-label="待发送附件">
            {pendingFiles.map((file) => (
              <ComposerFileChip file={file} key={file.id} onRemove={onRemovePendingFile} />
            ))}
          </div>
        ) : null}
        <input
          className="composer-placeholder composer-input"
          type="text"
          value={draft}
          placeholder={isTyping ? '正在输入...' : pendingFiles.length ? '请输入关于文件的问题' : '请输入'}
          onChange={(event) => onDraftChange(event.target.value)}
          onKeyDown={handleKeyDown}
          disabled={isTyping}
        />
        <div className="composer-tools">
          <input
            ref={fileInputRef}
            className="file-input"
            type="file"
            multiple
            accept=".docx,.xls,.xlsx,.csv,.pdf,.txt,.zip,.html"
            onChange={(event) => {
              onFileSelect(event.target.files)
              event.target.value = ''
            }}
          />
          <button
            aria-label="添加附件"
            type="button"
            disabled={isTyping || isUploadingFiles}
            onClick={() => fileInputRef.current?.click()}
          >
            <Icon name="attach" />
          </button>
          <button
            className="send-button"
            aria-label={isTyping ? '停止生成' : '发送'}
            type="button"
            onClick={isTyping ? onStopMessage : onSendMessage}
            disabled={!isTyping && (isUploadingFiles || hasUploadingFile)}
          >
            <Icon name="send" />
          </button>
        </div>
      </div>

      <div className="ai-note">{isTyping ? '正在输入...' : isUploadingFiles ? '正在解析附件...' : '内容由AI生成，仅供参考'}</div>
    </main>
  )
}

async function apiFetch(url, options = {}, token = null) {
  const headers = {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...options.headers,
  }

  const response = await fetch(`${API_BASE_URL}${url}`, {
    ...options,
    headers,
  })

  if (response.status === 401) {
    throw new AuthExpiredError()
  }

  const text = await response.text()
  let data = null
  try {
    data = JSON.parse(text)
  } catch {
    data = { code: response.status, message: text }
  }

  if (!response.ok || !isApiPayloadOk(data)) {
    return {
      ok: false,
      status: response.status,
      data,
      message: getApiMessage(data, `请求失败：${response.status}`),
    }
  }

  return { ok: true, status: response.status, data }
}

async function apiFetchSse(url, options = {}, token = null) {
  const headers = {
    'Content-Type': 'application/json',
    Accept: 'text/event-stream',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  }

  const response = await fetch(`${API_BASE_URL}${url}`, {
    ...options,
    headers,
  })

  if (response.status === 401) {
    throw new AuthExpiredError()
  }

  return response
}

async function apiUploadFiles(files, token) {
  const formData = new FormData()
  files.forEach((file) => formData.append('files', file))

  const response = await fetch(`${API_BASE_URL}/chat/files`, {
    method: 'POST',
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: formData,
  })

  if (response.status === 401) {
    throw new AuthExpiredError()
  }

  const text = await response.text()
  let data = null
  try {
    data = JSON.parse(text)
  } catch {
    data = { code: response.status, message: text }
  }

  if (!response.ok || !isApiPayloadOk(data)) {
    throw new Error(getApiMessage(data, `文件上传失败：${response.status}`))
  }

  return (unwrapApiData(data) || []).map(normalizeChatFile).filter(Boolean)
}

export default function App() {
  const [user, setUser] = useState(() => {
    try {
      const stored = localStorage.getItem(USER_KEY)
      return stored ? normalizeUser(JSON.parse(stored)) : null
    } catch {
      return null
    }
  })
  const [token, setToken] = useState(() => localStorage.getItem(TOKEN_KEY))
  const [isLoading, setIsLoading] = useState(true)
  const [sessions, setSessions] = useState([])
  const [activeSessionId, setActiveSessionId] = useState(null)
  const [searchValue, setSearchValue] = useState('')
  const [draft, setDraft] = useState('')
  const [isSidebarOpen, setIsSidebarOpen] = useState(false)
  const [isTyping, setIsTyping] = useState(false)
  const [downloadingFileId, setDownloadingFileId] = useState('')
  const [networkError, setNetworkError] = useState('')
  const [authError, setAuthError] = useState('')
  const [renameModalOpen, setRenameModalOpen] = useState(false)
  const [renameTitle, setRenameTitle] = useState('')
  const [pendingFiles, setPendingFiles] = useState([])
  const [isUploadingFiles, setIsUploadingFiles] = useState(false)

  const chatCanvasRef = useRef(null)
  const downloadTimerRef = useRef(null)
  const activeRequestRef = useRef({
    controller: null,
    sessionId: '',
    assistantId: '',
  })
  const initializedRef = useRef(false)

  const activeSession = useMemo(() => {
    return sessions.find((s) => s.id === activeSessionId) || sessions[0]
  }, [sessions, activeSessionId])

  const filteredHistory = useMemo(() => {
    const keyword = searchValue.trim().toLowerCase()
    if (!keyword) return sessions
    return sessions.filter((session) => session.title.toLowerCase().includes(keyword))
  }, [searchValue, sessions])

  useEffect(() => {
    if (!initializedRef.current) return
    const node = chatCanvasRef.current
    if (!node) return
    window.requestAnimationFrame(() => {
      node.scrollTop = node.scrollHeight
    })
  }, [activeSession?.messages, activeSessionId, isTyping, networkError])

  useEffect(() => () => {
    if (activeRequestRef.current.controller) {
      activeRequestRef.current.controller.abort()
    }
    if (downloadTimerRef.current) {
      window.clearTimeout(downloadTimerRef.current)
    }
  }, [])

  const persistAuth = useCallback((nextToken, nextUser) => {
    const normalizedUser = normalizeUser(nextUser)
    if (!nextToken || !normalizedUser) {
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
      setToken(null)
      setUser(null)
      return null
    }

    localStorage.setItem(TOKEN_KEY, nextToken)
    localStorage.setItem(USER_KEY, JSON.stringify(normalizedUser))
    setToken(nextToken)
    setUser(normalizedUser)
    return normalizedUser
  }, [])

  const clearAuthState = useCallback((message = '') => {
    abortActiveRequest()
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
    setToken(null)
    setUser(null)
    setSessions([])
    setActiveSessionId(null)
    setDraft('')
    setPendingFiles([])
    setIsUploadingFiles(false)
    setIsTyping(false)
    if (message) {
      setAuthError(message)
      setNetworkError(message)
    }
  }, [])

  const handleAuthFailure = useCallback((error) => {
    if (error instanceof AuthExpiredError) {
      clearAuthState(error.message)
      return true
    }
    return false
  }, [clearAuthState])

  const fetchCurrentUser = useCallback(async (authToken) => {
    const res = await apiFetch('/user/me', {}, authToken)
    const currentUser = normalizeUser(unwrapApiData(res.data))
    if (!currentUser) {
      throw new AuthExpiredError('无法获取当前账户信息，请重新登录')
    }
    persistAuth(authToken, currentUser)
    return currentUser
  }, [persistAuth])

  const handleAuthCallback = useCallback(async () => {
    const urlParams = new URLSearchParams(window.location.search)
    if (urlParams.get('auth_callback') !== '1') return

    const callbackToken = urlParams.get('token')
    const callbackUser = urlParams.get('user')
    urlParams.delete('auth_callback')
    urlParams.delete('token')
    urlParams.delete('user')
    const cleanUrl = `${window.location.pathname}${urlParams.toString() ? '?' + urlParams.toString() : ''}`
    window.history.replaceState({}, '', cleanUrl)

    if (callbackToken) {
      const parsedCallbackUser = normalizeUser(parseCallbackUser(callbackUser))
      if (parsedCallbackUser) {
        persistAuth(callbackToken, parsedCallbackUser)
      }
      await fetchCurrentUser(callbackToken)
      return callbackToken
    }
    return null
  }, [fetchCurrentUser, persistAuth])

  useEffect(() => {
    const init = async () => {
      initializedRef.current = false
      setIsLoading(true)

      const storedToken = localStorage.getItem(TOKEN_KEY)

      if (storedToken) {
        try {
          await fetchCurrentUser(storedToken)
          await loadSessions(storedToken)
        } catch (error) {
          if (!handleAuthFailure(error)) {
            clearAuthState('无法连接认证服务，请稍后重试')
          }
        }
      }

      try {
        const callbackToken = await handleAuthCallback()
        if (callbackToken) {
          await loadSessions(callbackToken)
        }
      } catch (error) {
        if (!handleAuthFailure(error)) {
          clearAuthState('认证回调处理失败，请重新登录')
        }
      }
      initializedRef.current = true
      setIsLoading(false)
    }

    init()
  }, [])

  useEffect(() => {
    const urlParams = new URLSearchParams(window.location.search)
    const error = urlParams.get('auth_error')
    const logout = urlParams.get('auth_logout')
    if (!error && !logout) return

    urlParams.delete('auth_error')
    urlParams.delete('auth_logout')
    const cleanUrl = `${window.location.pathname}${urlParams.toString() ? '?' + urlParams.toString() : ''}`
    window.history.replaceState({}, '', cleanUrl)
    setAuthError(logout ? '已退出登录' : `认证失败：${error}`)
  }, [])

  useEffect(() => {
    if (isLoading || user || token || authError || !AUTH_AUTO_REDIRECT) return
    handleLogin()
  }, [isLoading, user, token, authError])

  useEffect(() => {
    if (token && initializedRef.current) {
      loadSessions(token)
    }
  }, [token])

  const loadSessions = async (authToken, options = {}) => {
    try {
      const res = await apiFetch('/conversations', {}, authToken)
      const conversations = unwrapApiData(res.data) || []
      const normalized = []

      setSessions((prev) => {
        const previousById = new Map(prev.map((session) => [session.id, session]))
        const nextSessions = conversations
          .map((conversation) => normalizeConversation(conversation, previousById.get(conversation.id)))
          .filter(Boolean)

        normalized.splice(0, normalized.length, ...nextSessions)
        return nextSessions
      })

      setActiveSessionId((currentId) => {
        if (normalized.some((session) => session.id === currentId)) {
          return currentId
        }
        if (options.selectFirst && normalized.length > 0) {
          return normalized[0].id
        }
        return null
      })

      return normalized
    } catch (e) {
      if (handleAuthFailure(e)) return []
      console.error('Failed to load sessions:', e)
      setNetworkError(handleFetchFailure(e))
    }
    return []
  }

  const loadMessages = async (conversationId, authToken) => {
    try {
      const res = await apiFetch(`/messages?conversationId=${conversationId}`, {}, authToken)
      return (unwrapApiData(res.data) || []).map(normalizeMessage)
    } catch (e) {
      if (handleAuthFailure(e)) return []
      console.error('Failed to load messages:', e)
      setNetworkError(handleFetchFailure(e))
    }
    return []
  }

  const updateSession = (sessionId, updater) => {
    setSessions((prevSessions) =>
      prevSessions.map((session) => (session.id === sessionId ? updater(session) : session)),
    )
  }

  const updateSessionMessage = (sessionId, messageId, updater) => {
    updateSession(sessionId, (session) => updateMessageInSession(session, messageId, updater))
  }

  const abortActiveRequest = () => {
    if (activeRequestRef.current.controller) {
      activeRequestRef.current.controller.abort()
      activeRequestRef.current.controller = null
      activeRequestRef.current.sessionId = ''
      activeRequestRef.current.assistantId = ''
    }
  }

  const closeSidebar = () => setIsSidebarOpen(false)
  const openSidebar = () => setIsSidebarOpen(true)

  const handleLogin = () => {
    window.location.href = `${AUTH_URL}?callback=${encodeURIComponent(AUTH_CALLBACK_URL)}`
  }

  const handleDevLogin = async () => {
    try {
      const response = await fetch(`${AUTH_URL.replace(/\/login$/, '')}/dev-login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({}),
      })
      const text = await response.text()
      let data = null
      try { data = JSON.parse(text) } catch {}
      if (data && data.code === 200 && data.data) {
        const { token: devToken } = data.data
        await fetchCurrentUser(devToken)
        await loadSessions(devToken)
        setAuthError('')
        setNetworkError('')
      } else {
        setAuthError('开发登录失败: ' + (data?.message || '未知错误'))
      }
    } catch (e) {
      if (!handleAuthFailure(e)) {
        setAuthError('开发登录失败: ' + (e instanceof Error ? e.message : String(e)))
      }
    }
  }

  const handleLogout = () => {
    clearAuthState()
    window.location.href = `${AUTH_LOGOUT_URL}?callback=${encodeURIComponent(window.location.origin + import.meta.env.BASE_URL)}`
  }

  const handleNewSession = async () => {
    abortActiveRequest()
    setIsTyping(false)
    setDraft('')
    setPendingFiles([])
    setNetworkError('')

    if (!token) {
      clearAuthState('未登录，请先登录')
      return
    }

    try {
      const res = await apiFetch('/conversations', {
        method: 'POST',
        body: JSON.stringify({ title: '新会话' }),
      }, token)
      const newSession = normalizeConversation(unwrapApiData(res.data))
      if (!newSession) {
        throw new Error(res.message || '创建会话失败')
      }
      newSession.messagesLoaded = true
      setSessions((prev) => [newSession, ...prev.filter((session) => session.id !== newSession.id)])
      setActiveSessionId(newSession.id)
    } catch (error) {
      if (!handleAuthFailure(error)) {
        setNetworkError(handleFetchFailure(error))
      }
    }

    if (window.innerWidth < 768) {
      closeSidebar()
    }
  }

  const handleSelectSession = async (sessionId) => {
    abortActiveRequest()
    setIsTyping(false)
    setDraft('')
    setPendingFiles([])
    setNetworkError('')
    setActiveSessionId(sessionId)

    const session = sessions.find((s) => s.id === sessionId)
    if (session && !session.pending && !session.messagesLoaded && token) {
      const messages = await loadMessages(sessionId, token)
      setSessions((prev) =>
        prev.map((s) => (s.id === sessionId ? { ...s, messages, messagesLoaded: true } : s)),
      )
    }

    if (window.innerWidth < 768) {
      closeSidebar()
    }
  }

  const handleRenameSession = () => {
    if (!activeSession) return
    setRenameTitle(activeSession.title || '')
    setRenameModalOpen(true)
  }

  const handleRenameConfirm = async (newTitle) => {
    setRenameModalOpen(false)
    if (!activeSession || !newTitle.trim()) return

    const title = newTitle.trim()
    updateSession(activeSession.id, (s) => ({ ...s, title }))

    if (token && !isTemporarySessionId(activeSession.id)) {
      try {
        const res = await apiFetch(`/conversations/${activeSession.id}`, {
          method: 'PUT',
          body: JSON.stringify({ title }),
        }, token)
        const updatedSession = normalizeConversation(unwrapApiData(res.data), activeSession)
        if (updatedSession) {
          updateSession(activeSession.id, () => updatedSession)
        }
      } catch (e) {
        if (handleAuthFailure(e)) return
        console.error('Failed to rename session:', e)
        setNetworkError(handleFetchFailure(e))
      }
    }
  }

  const handleDeleteSession = async () => {
    if (!activeSession || !token || isTemporarySessionId(activeSession.id)) return

    try {
      await apiFetch(`/conversations/${activeSession.id}`, {
        method: 'DELETE',
      }, token)
    } catch (e) {
      if (handleAuthFailure(e)) return
      console.error('Failed to delete session:', e)
      setNetworkError(handleFetchFailure(e))
      return
    }

    setSessions((prev) => {
      const remaining = prev.filter((s) => s.id !== activeSession.id)
      if (remaining.length > 0) {
        setActiveSessionId(remaining[0].id)
      } else {
        setActiveSessionId(null)
      }
      return remaining
    })
  }

  const handleStopMessage = () => {
    const { controller, sessionId, assistantId } = activeRequestRef.current
    if (!controller) return

    controller.abort()
    activeRequestRef.current.controller = null
    activeRequestRef.current.sessionId = ''
    activeRequestRef.current.assistantId = ''
    setIsTyping(false)

    if (sessionId && assistantId) {
      updateSessionMessage(sessionId, assistantId, (message) => {
        const currentContent = normalizeMessageContent(message)
        return {
          ...message,
          content: currentContent || '已停止生成',
          text: currentContent || '已停止生成',
          status: 'done',
        }
      })
    }
  }

  const handleAttachmentDownload = (file) => {
    if (downloadTimerRef.current) {
      window.clearTimeout(downloadTimerRef.current)
    }
    setDownloadingFileId(file.name)
    downloadTimerRef.current = window.setTimeout(() => {
      setDownloadingFileId('')
      downloadTimerRef.current = null
    }, 900)
  }

  const handleFileSelect = async (fileList) => {
    const files = Array.from(fileList || [])
    const validationError = validateSelectedFiles(files, pendingFiles)
    if (validationError) {
      setNetworkError(validationError)
      return
    }
    if (!files.length) {
      return
    }
    if (!token) {
      clearAuthState('未登录，请先登录')
      return
    }

    const placeholders = files.map((file) => ({
      id: createId('uploading-file'),
      name: file.name,
      type: getFileType(file.name),
      size: formatFileSize(file.size),
      rawSize: file.size,
      status: 'uploading',
    }))

    setPendingFiles((current) => [...current, ...placeholders])
    setIsUploadingFiles(true)
    setNetworkError('')

    try {
      const uploadedFiles = await apiUploadFiles(files, token)
      setPendingFiles((current) => [
        ...current.filter((file) => !placeholders.some((placeholder) => placeholder.id === file.id)),
        ...uploadedFiles,
      ])
    } catch (error) {
      setPendingFiles((current) => current.filter((file) => !placeholders.some((placeholder) => placeholder.id === file.id)))
      if (!handleAuthFailure(error)) {
        setNetworkError(handleFetchFailure(error))
      }
    } finally {
      setIsUploadingFiles(false)
    }
  }

  const handleRemovePendingFile = (fileId) => {
    setPendingFiles((current) => current.filter((file) => file.id !== fileId))
  }

  const updateAssistantStream = (sessionId, assistantId, content, status) => {
    updateSessionMessage(sessionId, assistantId, (message) => ({
      ...message,
      content,
      text: content,
      status,
    }))
  }

  const handleFetchFailure = (error) => {
    if (error instanceof AuthExpiredError) {
      return error.message
    }
    if (error instanceof DOMException && error.name === 'AbortError') {
      return '请求已取消'
    }
    if (error instanceof TypeError) {
      return '网络连接失败，请检查网络'
    }
    const message = error instanceof Error ? error.message : String(error)
    return `网络连接失败：${message}`
  }

  const handleSendMessage = async (overrideText) => {
    const text = (overrideText ?? draft).trim()
    if ((!text && pendingFiles.length === 0) || isTyping) {
      return
    }
    if (isUploadingFiles || pendingFiles.some((file) => file.status === 'uploading')) {
      setNetworkError('附件还在解析中，请稍后发送')
      return
    }

    if (!token) {
      clearAuthState('未登录，请先登录')
      return
    }

    abortActiveRequest()
    setNetworkError('')
    const filesForMessage = pendingFiles.map(normalizeChatFile).filter(Boolean)
    const userContent = text || '请总结附件内容'

    let sessionId = activeSessionId
    let session = activeSession

    if (!sessionId || !session || isTemporarySessionId(sessionId)) {
      try {
        const res = await apiFetch('/conversations', {
          method: 'POST',
          body: JSON.stringify({ title: userContent.slice(0, 16) || '新会话' }),
        }, token)
        const newSession = normalizeConversation(unwrapApiData(res.data))
        if (!newSession) {
          throw new Error(res.message || '创建会话失败')
        }
        newSession.messagesLoaded = true
        sessionId = newSession.id
        session = newSession
        setSessions((prev) => [newSession, ...prev.filter((item) => item.id !== newSession.id)])
        setActiveSessionId(newSession.id)
      } catch (error) {
        if (!handleAuthFailure(error)) {
          setNetworkError(handleFetchFailure(error))
        }
        return
      }
    }

    const userMessage = {
      id: createId('user'),
      role: 'user',
      text: userContent,
      content: userContent,
      attachments: filesForMessage,
    }
    const assistantId = createId('assistant')
    const assistantPlaceholder = {
      id: assistantId,
      role: 'assistant',
      content: '',
      text: '',
      status: 'loading',
    }

    updateSession(sessionId, (s) => ({
      ...s,
      title: (s.title === '新会话' || !s.title) ? userContent.slice(0, 16) || '新会话' : s.title,
      pending: false,
      messagesLoaded: true,
      messages: [...s.messages, userMessage, assistantPlaceholder],
    }))

    setDraft('')
    setPendingFiles([])
    setIsTyping(true)

    const controller = new AbortController()
    activeRequestRef.current = {
      controller,
      sessionId,
      assistantId,
    }

    const sessionMessages = session?.messages || []
    const requestMessages = toRequestMessages(sessionMessages, userMessage)
    const requestBody = {
      conversationId: sessionId,
      content: userContent,
      messages: requestMessages,
      fileIds: filesForMessage.map((file) => file.id).filter(Boolean),
      agentId: AGENT_ID,
    }

    try {
      const chatUrl = '/chat/stream'
      const response = await apiFetchSse(chatUrl, {
        method: 'POST',
        body: JSON.stringify(requestBody),
        signal: controller.signal,
      }, token)

      if (!response.ok) {
        const errorText = await response.text().catch(() => '')
        throw new Error(parseResponseError(errorText, `请求失败：${response.status}`))
      }

      if (!response.body) {
        throw new Error('浏览器不支持流式响应')
      }

      await readSseJsonLines(response, {
        signal: controller.signal,
        onEvent: (event) => {
          if (event?.error) {
            const errorText = `服务异常：${event.error}`
            updateAssistantStream(sessionId, assistantId, errorText, 'error')
            setNetworkError(errorText)
            return false
          }

          if (typeof event?.content === 'string') {
            updateSessionMessage(sessionId, assistantId, (message) => {
              const nextContent = `${normalizeMessageContent(message)}${event.content}`
              return {
                ...message,
                content: nextContent,
                text: nextContent,
                status: 'streaming',
              }
            })
          }

          if (event?.is_end) {
            updateSessionMessage(sessionId, assistantId, (message) => ({
              ...message,
              status: 'done',
            }))
            return false
          }

          return true
        },
      })

      const messages = await loadMessages(sessionId, token)
      setSessions((prev) =>
        prev.map((s) => (s.id === sessionId ? { ...s, messages, messagesLoaded: true } : s)),
      )
      await loadSessions(token)
    } catch (error) {
      if (controller.signal.aborted) {
        return
      }
      if (handleAuthFailure(error)) {
        return
      }
      const failureMessage = handleFetchFailure(error)
      updateAssistantStream(sessionId, assistantId, failureMessage, 'error')
      setNetworkError(failureMessage)
    } finally {
      if (activeRequestRef.current.controller === controller) {
        activeRequestRef.current.controller = null
        activeRequestRef.current.sessionId = ''
        activeRequestRef.current.assistantId = ''
      }
      setIsTyping(false)
    }
  }

  const handleQuickPrompt = () => {}

  if (isLoading) {
    return (
      <div className="app">
        <div className="loading-page">
          <div className="loading-spinner" />
          <p>加载中...</p>
        </div>
      </div>
    )
  }

  if (!user || !token) {
    return (
      <div className="app">
        <LoginPage onLogin={handleLogin} onDevLogin={handleDevLogin} authError={authError} />
      </div>
    )
  }

  return (
    <div className="app">
      <Sidebar
        isOpen={isSidebarOpen}
        onClose={closeSidebar}
        onNewSession={handleNewSession}
        searchValue={searchValue}
        onSearchChange={setSearchValue}
        historyList={filteredHistory}
        activeSessionId={activeSessionId}
        onSelectSession={handleSelectSession}
        user={user}
        onLogout={handleLogout}
        onRenameSession={handleRenameSession}
        onDeleteSession={handleDeleteSession}
      />
      <button
        className={`sidebar-backdrop ${isSidebarOpen ? 'sidebar-backdrop-show' : ''}`}
        type="button"
        aria-label="关闭菜单"
        onClick={closeSidebar}
      />
      <ChatPage
        onMenuClick={openSidebar}
        session={activeSession}
        draft={draft}
        onDraftChange={setDraft}
        onSendMessage={handleSendMessage}
        onStopMessage={handleStopMessage}
        onQuickPrompt={handleQuickPrompt}
        onFileSelect={handleFileSelect}
        onRemovePendingFile={handleRemovePendingFile}
        onAttachmentDownload={handleAttachmentDownload}
        downloadingFileId={downloadingFileId}
        pendingFiles={pendingFiles}
        isUploadingFiles={isUploadingFiles}
        isTyping={isTyping}
        canvasRef={chatCanvasRef}
        networkError={networkError}
      />
      <RenameModal
        isOpen={renameModalOpen}
        currentTitle={renameTitle}
        onConfirm={handleRenameConfirm}
        onCancel={() => setRenameModalOpen(false)}
      />
    </div>
  )
}
