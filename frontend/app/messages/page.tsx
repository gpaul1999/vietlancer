'use client';

import { Suspense, useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { api, ApiError } from '@/lib/api';
import { useAuth } from '@/lib/auth-context';
import type { Conversation, Message } from '@/lib/types';
import { formatDateTime } from '@/lib/format';

function MessagesContent() {
  const { user } = useAuth();
  const params = useSearchParams();
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [activeId, setActiveId] = useState<number | null>(params.get('c') ? Number(params.get('c')) : null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [text, setText] = useState('');
  const [error, setError] = useState('');
  const bottomRef = useRef<HTMLDivElement>(null);

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
    const interval = setInterval(loadMessages, 5000); // MVP: polling; phase 2 → WebSocket
    return () => clearInterval(interval);
  }, [loadMessages]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages.length]);

  const send = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!text.trim() || !activeId) return;
    try {
      await api.post(`/api/chats/${activeId}/messages`, { content: text.trim() });
      setText('');
      await loadMessages();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Không gửi được');
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
            <div className="border-b border-slate-100 px-5 py-3">
              <div className="font-semibold">
                {(active.client.id === user.id ? active.freelancer : active.client).fullName}
              </div>
              <div className="text-xs text-slate-500">Job: {active.jobTitle}</div>
            </div>
            <div className="flex-1 space-y-3 overflow-y-auto px-5 py-4">
              {messages.map((m) => (
                <div key={m.id} className={`flex ${m.senderId === user.id ? 'justify-end' : 'justify-start'}`}>
                  <div
                    className={`max-w-[75%] rounded-2xl px-4 py-2 text-sm ${
                      m.senderId === user.id ? 'bg-brand-600 text-white' : 'bg-slate-100 text-slate-800'
                    }`}
                  >
                    <p className="whitespace-pre-wrap">{m.content}</p>
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
