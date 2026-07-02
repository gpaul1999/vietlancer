'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { api } from '@/lib/api';
import type { Notification } from '@/lib/types';
import { timeAgo } from '@/lib/format';

const typeIcon: Record<Notification['type'], string> = {
  NEW_BID: '💰',
  BID_ACCEPTED: '🎉',
  BID_REJECTED: '😞',
  JOB_COMPLETED: '✅',
  NEW_MESSAGE: '💬',
  NEW_REVIEW: '⭐',
};

export default function NotificationBell() {
  const router = useRouter();
  const [unread, setUnread] = useState(0);
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState<Notification[]>([]);
  const ref = useRef<HTMLDivElement>(null);

  const loadCount = useCallback(() => {
    api.get<{ count: number }>('/api/notifications/unread-count')
      .then((r) => setUnread(r.count))
      .catch(() => {});
  }, []);

  useEffect(() => {
    loadCount();
    const interval = setInterval(loadCount, 20000);
    return () => clearInterval(interval);
  }, [loadCount]);

  useEffect(() => {
    const onClick = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onClick);
    return () => document.removeEventListener('mousedown', onClick);
  }, []);

  const toggle = async () => {
    const next = !open;
    setOpen(next);
    if (next) {
      try {
        setItems(await api.get<Notification[]>('/api/notifications'));
      } catch {
        /* ignore */
      }
    }
  };

  const onItemClick = async (n: Notification) => {
    setOpen(false);
    try {
      await api.post(`/api/notifications/${n.id}/read`);
    } catch {
      /* ignore */
    }
    loadCount();
    if (n.link) router.push(n.link);
  };

  const markAll = async () => {
    try {
      await api.post('/api/notifications/read-all');
      setItems(items.map((i) => ({ ...i, read: true })));
      setUnread(0);
    } catch {
      /* ignore */
    }
  };

  return (
    <div className="relative" ref={ref}>
      <button
        onClick={toggle}
        className="relative rounded-lg px-2.5 py-2 text-lg hover:bg-slate-100"
        aria-label="Thông báo"
      >
        🔔
        {unread > 0 && (
          <span className="absolute -right-0.5 -top-0.5 flex h-5 min-w-5 items-center justify-center rounded-full bg-rose-500 px-1 text-[10px] font-bold text-white">
            {unread > 99 ? '99+' : unread}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 z-50 mt-2 w-96 max-w-[90vw] rounded-2xl border border-slate-200 bg-white shadow-lg">
          <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3">
            <span className="font-semibold">Thông báo</span>
            {unread > 0 && (
              <button onClick={markAll} className="text-xs font-medium text-brand-600 hover:underline">
                Đánh dấu đã đọc tất cả
              </button>
            )}
          </div>
          <div className="max-h-96 overflow-y-auto">
            {items.length === 0 && (
              <p className="px-4 py-8 text-center text-sm text-slate-400">Chưa có thông báo nào.</p>
            )}
            {items.map((n) => (
              <button
                key={n.id}
                onClick={() => onItemClick(n)}
                className={`flex w-full items-start gap-3 px-4 py-3 text-left transition hover:bg-slate-50 ${
                  n.read ? 'opacity-60' : 'bg-brand-50/40'
                }`}
              >
                <span className="text-xl">{typeIcon[n.type]}</span>
                <span>
                  <span className="block text-sm text-slate-800">{n.message}</span>
                  <span className="mt-0.5 block text-xs text-slate-400">{timeAgo(n.createdAt)}</span>
                </span>
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
