'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { api, ApiError } from '@/lib/api';
import { useAuth } from '@/lib/auth-context';
import type { Dispute } from '@/lib/types';
import { formatVnd, formatDateTime } from '@/lib/format';

export default function AdminDisputesPage() {
  const { user, loading } = useAuth();
  const [status, setStatus] = useState<'OPEN' | 'RESOLVED'>('OPEN');
  const [disputes, setDisputes] = useState<Dispute[]>([]);
  const [resolving, setResolving] = useState<number | null>(null);
  const [form, setForm] = useState({ amountToFreelancer: '', note: '' });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      setDisputes(await api.get<Dispute[]>(`/api/admin/disputes?status=${status}`));
    } catch {
      /* ignore */
    }
  }, [status]);

  useEffect(() => {
    if (user?.role === 'ADMIN') load();
  }, [user, load]);

  if (loading) return <p className="py-12 text-center text-slate-500">Đang tải…</p>;
  if (!user || user.role !== 'ADMIN') {
    return <p className="py-12 text-center text-slate-500">Trang này chỉ dành cho quản trị viên.</p>;
  }

  const resolve = async (id: number) => {
    setBusy(true);
    setError('');
    try {
      await api.post(`/api/admin/disputes/${id}/resolve`, {
        amountToFreelancer: Number(form.amountToFreelancer),
        note: form.note,
      });
      setResolving(null);
      setForm({ amountToFreelancer: '', note: '' });
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Phân xử thất bại');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="mx-auto max-w-4xl">
      <h1 className="text-2xl font-bold">⚖️ Trung tâm phân xử khiếu nại</h1>
      <div className="mt-4 flex gap-2">
        {(['OPEN', 'RESOLVED'] as const).map((s) => (
          <button key={s}
            className={`rounded-lg px-4 py-2 text-sm font-medium ${status === s ? 'bg-brand-600 text-white' : 'bg-white text-slate-600 hover:bg-slate-100'}`}
            onClick={() => setStatus(s)}>
            {s === 'OPEN' ? 'Đang chờ' : 'Đã xử lý'}
          </button>
        ))}
      </div>

      <div className="mt-6 space-y-4">
        {disputes.length === 0 && (
          <p className="py-12 text-center text-slate-500">
            {status === 'OPEN' ? 'Không có khiếu nại nào đang chờ. 🎉' : 'Chưa có khiếu nại đã xử lý.'}
          </p>
        )}
        {disputes.map((d) => (
          <div key={d.id} className="card">
            <div className="flex flex-wrap items-start justify-between gap-2">
              <div>
                <Link href={`/jobs/${d.jobId}`} className="font-semibold text-brand-700 hover:underline">
                  #{d.id} — {d.jobTitle}
                </Link>
                <p className="mt-1 text-sm text-slate-500">
                  Mở bởi <b>{d.raisedByName}</b> lúc {formatDateTime(d.createdAt)}
                </p>
              </div>
              <div className="text-right">
                <div className="text-sm text-slate-500">Escrow đóng băng</div>
                <div className="font-bold text-brand-700">{formatVnd(d.heldAmount)}</div>
              </div>
            </div>
            <p className="mt-3 rounded-xl bg-slate-50 p-3 text-sm text-slate-700">{d.reason}</p>

            {d.status === 'RESOLVED' && (
              <p className="mt-3 text-sm text-emerald-700">
                ✅ Đã phân xử ({formatDateTime(d.resolvedAt!)}): freelancer nhận {formatVnd(d.amountToFreelancer)},
                client hoàn {formatVnd(d.heldAmount - (d.amountToFreelancer || 0))}.
                {d.resolutionNote && <> Ghi chú: {d.resolutionNote}</>}
              </p>
            )}

            {d.status === 'OPEN' && (
              resolving === d.id ? (
                <div className="mt-4 rounded-xl border border-brand-200 p-4">
                  <label className="label">Số tiền trả freelancer (0 – {formatVnd(d.heldAmount)})</label>
                  <input className="input" type="number" min={0} max={d.heldAmount}
                    value={form.amountToFreelancer}
                    onChange={(e) => setForm({ ...form, amountToFreelancer: e.target.value })} />
                  <label className="label mt-3">Ghi chú phân xử</label>
                  <textarea className="input" value={form.note}
                    onChange={(e) => setForm({ ...form, note: e.target.value })} />
                  <p className="mt-2 text-xs text-slate-500">
                    Phần còn lại ({formatVnd(d.heldAmount - (Number(form.amountToFreelancer) || 0))}) sẽ hoàn cho client.
                    Trả 0đ = hủy job hoàn toàn bộ cho client.
                  </p>
                  <div className="mt-3 flex gap-2">
                    <button className="btn-primary" disabled={busy || form.amountToFreelancer === ''} onClick={() => resolve(d.id)}>
                      Xác nhận phân xử
                    </button>
                    <button className="btn-secondary" onClick={() => setResolving(null)}>Bỏ qua</button>
                  </div>
                </div>
              ) : (
                <button className="btn-primary mt-4" onClick={() => { setResolving(d.id); setForm({ amountToFreelancer: '', note: '' }); }}>
                  Phân xử
                </button>
              )
            )}
          </div>
        ))}
      </div>
      {error && <p className="mt-4 text-sm text-rose-600">{error}</p>}
    </div>
  );
}
