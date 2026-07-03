'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, ApiError } from '@/lib/api';
import type { Job, Milestone, MilestoneStatus } from '@/lib/types';
import { formatVnd, formatDate } from '@/lib/format';

const statusInfo: Record<MilestoneStatus, { label: string; cls: string }> = {
  PENDING: { label: 'Chưa nạp tiền', cls: 'bg-slate-100 text-slate-600' },
  FUNDED: { label: 'Đã nạp escrow', cls: 'bg-brand-50 text-brand-700' },
  SUBMITTED: { label: 'Chờ duyệt', cls: 'bg-amber-50 text-amber-700' },
  RELEASED: { label: 'Đã giải ngân', cls: 'bg-emerald-50 text-emerald-700' },
  CANCELLED: { label: 'Đã hủy', cls: 'bg-rose-50 text-rose-500' },
};

export default function MilestonePanel({
  job,
  isOwner,
  isAssigned,
  frozen,
  onChanged,
}: {
  job: Job;
  isOwner: boolean;
  isAssigned: boolean;
  frozen: boolean;
  onChanged: () => void;
}) {
  const [milestones, setMilestones] = useState<Milestone[]>([]);
  const [form, setForm] = useState({ title: '', amount: '', dueDate: '' });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      setMilestones(await api.get<Milestone[]>(`/api/jobs/${job.id}/milestones`));
    } catch {
      /* ignore */
    }
  }, [job.id]);

  useEffect(() => {
    load();
  }, [load]);

  const act = async (fn: () => Promise<unknown>) => {
    setBusy(true);
    setError('');
    try {
      await fn();
      await load();
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Có lỗi xảy ra');
    } finally {
      setBusy(false);
    }
  };

  const create = () =>
    act(async () => {
      await api.post(`/api/jobs/${job.id}/milestones`, {
        title: form.title,
        amount: Number(form.amount),
        dueDate: form.dueDate || null,
      });
      setForm({ title: '', amount: '', dueDate: '' });
    });

  const total = milestones.filter((m) => m.status !== 'CANCELLED').reduce((s, m) => s + m.amount, 0);
  const released = milestones.filter((m) => m.status === 'RELEASED').reduce((s, m) => s + m.amount, 0);

  return (
    <div className="card">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="font-semibold">📋 Thanh toán theo milestone</h2>
        <span className="text-sm text-slate-500">
          Đã giải ngân <b className="text-emerald-600">{formatVnd(released)}</b> / {formatVnd(total)}
        </span>
      </div>
      <p className="mt-1 text-xs text-slate-500">
        Client nạp escrow từng mốc — freelancer thấy mốc nào đã có tiền mới cần làm mốc đó. An toàn cho cả hai bên.
      </p>

      <div className="mt-4 space-y-3">
        {milestones.length === 0 && (
          <p className="rounded-xl bg-slate-50 p-4 text-sm text-slate-500">
            Chưa có mốc nào. {isOwner ? 'Tạo mốc đầu tiên bên dưới.' : 'Chờ client tạo mốc công việc.'}
          </p>
        )}
        {milestones.map((m, idx) => {
          const st = statusInfo[m.status];
          return (
            <div key={m.id} className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-slate-200 p-4">
              <div>
                <div className="font-medium">
                  <span className="mr-2 text-slate-400">#{idx + 1}</span>
                  {m.title}
                </div>
                <div className="mt-0.5 text-sm text-slate-500">
                  {formatVnd(m.amount)}
                  {m.dueDate && <> · hạn {formatDate(m.dueDate)}</>}
                </div>
              </div>
              <div className="flex items-center gap-2">
                <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${st.cls}`}>{st.label}</span>
                {!frozen && isOwner && m.status === 'PENDING' && (
                  <>
                    <button className="btn-primary !px-3 !py-1.5 text-xs" disabled={busy}
                      onClick={() => act(() => api.post(`/api/milestones/${m.id}/fund`))}>
                      Nạp escrow
                    </button>
                    <button className="btn-secondary !px-3 !py-1.5 text-xs" disabled={busy}
                      onClick={() => act(() => api.post(`/api/milestones/${m.id}/cancel`))}>
                      Hủy
                    </button>
                  </>
                )}
                {!frozen && isAssigned && m.status === 'FUNDED' && (
                  <button className="btn-primary !px-3 !py-1.5 text-xs" disabled={busy}
                    onClick={() => act(() => api.post(`/api/milestones/${m.id}/submit`))}>
                    Nộp mốc này
                  </button>
                )}
                {!frozen && isOwner && (m.status === 'FUNDED' || m.status === 'SUBMITTED') && (
                  <button className="btn-primary !px-3 !py-1.5 text-xs" disabled={busy}
                    onClick={() => act(() => api.post(`/api/milestones/${m.id}/release`))}>
                    Giải ngân
                  </button>
                )}
              </div>
            </div>
          );
        })}
      </div>

      {!frozen && isOwner && job.status === 'IN_PROGRESS' && (
        <div className="mt-4 rounded-xl bg-slate-50 p-4">
          <p className="mb-2 text-sm font-medium">Thêm mốc mới</p>
          <div className="grid gap-2 md:grid-cols-[1fr_160px_150px_auto]">
            <input className="input" placeholder="Tên mốc (VD: Thiết kế giao diện)" value={form.title}
              onChange={(e) => setForm({ ...form, title: e.target.value })} />
            <input className="input" type="number" min={1} placeholder="Số tiền (VND)" value={form.amount}
              onChange={(e) => setForm({ ...form, amount: e.target.value })} />
            <input className="input" type="date" value={form.dueDate}
              onChange={(e) => setForm({ ...form, dueDate: e.target.value })} />
            <button className="btn-primary" disabled={busy || !form.title || !form.amount} onClick={create}>
              Thêm
            </button>
          </div>
        </div>
      )}

      {error && <p className="mt-3 text-sm text-rose-600">{error}</p>}
    </div>
  );
}
