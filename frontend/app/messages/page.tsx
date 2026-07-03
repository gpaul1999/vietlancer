'use client';

import { Suspense, useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { Client } from '@stomp/stompjs';
import { api, ApiError, getToken, uploadFile, WS_URL } from '@/lib/api';
import { useAuth } from '@/lib/auth-context';
import type { Conversation, Message } from '@/lib/types';
import { formatDateTime } from '@/lib/format';

const IMAGE_RE = /\.(png|jpe?g|webp|gif)$/i;

/** Render nội dung tin nhắn: URL file đính kèm → ảnh/link, còn lại là text thuần. */
function MessageContent({ content }: { content: string }) {
  if (/^https?:\/\/\S+$/.test(content)) {
    if (IMAGE_RE.test(content)) {
      // eslint-disable-next-line @next/next/no-img-element
      return <img src={content} alt="Ảnh đính kèm" className="max-h-64 rounded-lg" />;
    }
    return (
      <a href={content} target="_blank" rel="noopener noreferrer" className="underline">
        📎 Tệp đính kèm
      </a>
    );
  }
  return <p className="whitespace-pre-wrap">{content}</p>;
}

function MessagesContent() {
  const { user } = useAuth();
  const params = useSearchParams();
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [activeId, setActiveId] = useState<number | null>(params.get('c') ? Number(params.get('c')) : null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [text, setText] = useState('');
  const [error, setError] = useState('');
  const [live, setLive] = useState(false);
  const [uploading, setUploading] = useState(false);
  const bottomRef = useRef<HTMLDivElement>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!user) return;
    api.get<Conversation[]>('/api/chats').then((list) => {
      setConversations(list);
      if (!activeId && list.length > 0) setActiveId(list[0].id);
    }).catch(() => {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user]);

  const loadMessages = useCallback(async () => {
    if (!activeId) return;
    try {
      setMessages(await api.get<Message[]>(`/api/chats/${activeId}/messages`));
      setError('');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Không tải được tin nhắn');
    }
  }, [activeId]);

  useEffect(() => {
    loadMessages();
    // Fallback polling thưa — kênh chính là WebSocket bên dưới
    const interval = setInterval(loadMessages, 20000);
    return () => clearInterval(interval);
  }, [loadMessages]);

  // Realtime qua STOMP WebSocket: nhận tin nhắn mới ngay lập tức
  useEffect(() => {
    const token = getToken();
    if (!token || !activeId) return;
    const client = new Client({
      brokerURL: WS_URL,
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 5000,
    });
    client.onConnect = () => {
      setLive(true);
      client.subscribe(`/topic/conversations/${activeId}`, (frame) => {
        const incoming = JSON.parse(frame.body) as Message;
        setMessages((prev) => (prev.some((m) => m.id === incoming.id) ? prev : [...prev, incoming]));
      });
    };
    client.onWebSocketClose = () => setLive(false);
    client.activate();
    return () => {
      setLive(false);
      client.deactivate();
    };
  }, [activeId]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages.length]);

  const send = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!text.trim() || !activeId) return;
    try {
      await api.post(`/api/chats/${activeId}/messages`, { content: text.trim() });
      setText('');
      if (!live) await loadMessages();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Không gửi được');
    }
  };

  const attach = async (file: File | undefined) => {
    if (!file || !activeId) return;
    setUploading(true);
    setError('');
    try {
      const uploaded = await uploadFile(file);
      await api.post(`/api/chats/${activeId}/messages`, { content: uploaded.url });
      if (!live) await loadMessages();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Không gửi được file');
    } finally {
      setUploading(false);
      if (fileRef.current) fileRef.current.value = '';
    }
  };

  if (!user) return <p className="py-12 text-center text-slate-500">Hãy đăng nhập để xem tin nhắn.</p>;

  const active = conversations.find((c) => c.id === activeId);

  return (
    <div className="grid gap-4 lg:grid-cols-[300px_1fr]">
      <aside className="card !p-3">
        <h2 className="px-2 py-1 font-semibold">Hội thoại</h2>
        {conversations.length === 0 && (
          <p className="px-2 py-4 text-sm text-slate-500">
            Chưa có hội thoại nào. Chat mở khi bid được chấp nhận hoặc cả hai bên có Premium.
          </p>
        )}
        <div className="mt-1 space-y-1">
          {conversations.map((c) => {
            const other = c.client.id === user.id ? c.freelancer : c.client;
            return (
              <button
                key={c.id}
                onClick={() => setActiveId(c.id)}
                className={`block w-full rounded-xl px-3 py-2.5 text-left transition ${
                  c.id === activeId ? 'bg-brand-50' : 'hover:bg-slate-50'
                }`}
              >
                <div className="text-sm font-semibold">{other.fullName}</div>
                <div className="truncate text-xs text-slate-500">{c.jobTitle}</div>
                {c.lastMessage && <div className="truncate text-xs text-slate-400">{c.lastMessage}</div>}
              </button>
            );
          })}
        </div>
      </aside>

      <section className="card flex min-h-[60vh] flex-col !p-0">
        {active ? (
          <>
            <div className="flex items-center justify-between border-b border-slate-100 px-5 py-3">
              <div>
                <div className="font-semibold">
                  {(active.client.id === user.id ? active.freelancer : active.client).fullName}
                </div>
                <div className="text-xs text-slate-500">Job: {active.jobTitle}</div>
              </div>
              <span className={`flex items-center gap-1.5 text-xs ${live ? 'text-emerald-600' : 'text-slate-400'}`}>
                <span className={`h-2 w-2 rounded-full ${live ? 'bg-emerald-500' : 'bg-slate-300'}`} />
                {live ? 'Real-time' : 'Đang kết nối…'}
              </span>
            </div>
            <div className="flex-1 space-y-3 overflow-y-auto px-5 py-4">
              {messages.map((m) => (
                <div key={m.id} className={`flex ${m.senderId === user.id ? 'justify-end' : 'justify-start'}`}>
                  <div
                    className={`max-w-[75%] rounded-2xl px-4 py-2 text-sm ${
                      m.senderId === user.id ? 'bg-brand-600 text-white' : 'bg-slate-100 text-slate-800'
                    }`}
                  >
                    <MessageContent content={m.content} />
                    <p className={`mt-1 text-[10px] ${m.senderId === user.id ? 'text-brand-100' : 'text-slate-400'}`}>
                      {formatDateTime(m.createdAt)}
                    </p>
                  </div>
                </div>
              ))}
              <div ref={bottomRef} />
            </div>
            {error && <p className="px-5 pb-2 text-sm text-rose-600">{error}</p>}
            <form onSubmit={send} className="flex gap-2 border-t border-slate-100 p-3">
              <input
                ref={fileRef}
                type="file"
                className="hidden"
                accept=".png,.jpg,.jpeg,.webp,.gif,.pdf,.doc,.docx,.xls,.xlsx,.txt,.zip,.rar"
                onChange={(e) => attach(e.target.files?.[0])}
              />
              <button
                type="button"
                className="btn-secondary shrink-0 !px-3"
                title="Gửi file đính kèm (tối đa 5MB)"
                disabled={uploading}
                onClick={() => fileRef.current?.click()}
              >
                {uploading ? '…' : '📎'}
              </button>
              <input
                className="input"
                placeholder="Nhập tin nhắn…"
                value={text}
                onChange={(e) => setText(e.target.value)}
              />
              <button className="btn-primary shrink-0">Gửi</button>
            </form>
          </>
        ) : (
          <p className="flex flex-1 items-center justify-center text-slate-400">Chọn một hội thoại</p>
        )}
      </section>
    </div>
  );
}

export default function MessagesPage() {
  return (
    <Suspense fallback={<p className="py-12 text-center text-slate-500">Đang tải…</p>}>
      <MessagesContent />
    </Suspense>
  );
}
