'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { api, ApiError } from '@/lib/api';
import { useAuth } from '@/lib/auth-context';
import type { AdminRecentJob, AdminStats, KycEntry } from '@/lib/types';
import { formatVnd, formatDateTime } from '@/lib/format';

export default function AdminDashboardPage() {
  const { user, loading } = useAuth();
  const [stats, setStats] = useState<AdminStats | null>(null);
  const [kyc, setKyc] = useState<KycEntry[]>([]);
  const [jobs, setJobs] = useState<AdminRecentJob[]>([]);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const [s, k, j] = await Promise.all([
        api.get<AdminStats>('/api/admin/stats'),
        api.get<KycEntry[]>('/api/admin/kyc'),
        api.get<AdminRecentJob[]>('/api/admin/jobs'),
      ]);
      setStats(s);
      setKyc(k);
      setJobs(j);
    } catch {
      /* ignore */
    }
  }, []);

  useEffect(() => {
    if (user?.role === 'ADMIN') load();
  }, [user, load]);

  if (loading) return <p className="py-12 text-center text-slate-500">Đang tải…</p>;
  if (!user || user.role !== 'ADMIN') {
    return <p className="py-12 text-center text-slate-500">Trang này chỉ dành cho quản trị viên.</p>;
  }

  const act = async (fn: () => Promise<unknown>) => {
    setBusy(true);
    setError('');
    try {
      await fn();
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Có lỗi xảy ra');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-8">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-bold">🛠️ Bảng điều khiển quản trị</h1>
        <Link href="/admin/disputes" className="btn-secondary">
          ⚖️ Trung tâm khiếu nại {stats && stats.openDisputes > 0 && `(${stats.openDisputes} chờ)`}
        </Link>
      </div>

      {stats && (
        <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
          {[
            ['Khách thuê', stats.totalClients],
            ['Freelancer', stats.totalFreelancers],
            ['Job đang mở', stats.jobsOpen],
            ['Đang thực hiện', stats.jobsInProgress],
            ['Đã hoàn thành', stats.jobsCompleted],
            ['Premium đang hoạt động', stats.activePremiumUsers],
            ['GMV đã giải ngân', formatVnd(stats.gmv)],
            ['Doanh thu phí nền tảng', formatVnd(stats.platformRevenue)],
          ].map(([label, value]) => (
            <div key={label as string} className="card !p-4">
              <div className="text-xs text-slate-500">{label}</div>
              <div className="mt-1 text-xl font-bold">{value}</div>
            </div>
          ))}
        </div>
      )}

      {error && <p className="rounded-xl bg-rose-50 p-3 text-sm text-rose-600">{error}</p>}

      {/* KYC queue */}
      <section className="card">
        <h2 className="font-semibold">🪪 Hồ sơ xác minh chờ duyệt ({kyc.length})</h2>
        {kyc.length === 0 ? (
          <p className="mt-3 text-sm text-slate-500">Không có hồ sơ nào chờ duyệt. 🎉</p>
        ) : (
          <div className="mt-3 divide-y divide-slate-100">
            {kyc.map((entry) => (
              <div key={entry.userId} className="flex flex-wrap items-center justify-between gap-3 py-3">
                <div>
                  <Link href={`/profile/${entry.userId}`} className="font-medium hover:text-brand-700">
                    {entry.fullName}
                  </Link>
                  <div className="text-xs text-slate-500">
                    {entry.email} · {entry.role} · CCCD: {entry.idNumber}
                  </div>
                </div>
                <div className="flex gap-2">
                  <button className="btn-primary !py-1.5 text-sm" disabled={busy}
                    onClick={() => act(() => api.post(`/api/admin/kyc/${entry.userId}/approve`, {}))}>
                    ✅ Duyệt
                  </button>
                  <button className="btn-secondary !py-1.5 text-sm" disabled={busy}
                    onClick={() => act(() => api.post(`/api/admin/kyc/${entry.userId}/reject`, { note: 'Thông tin chưa hợp lệ' }))}>
                    Từ chối
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </section>

      {/* Recent jobs moderation */}
      <section className="card">
        <h2 className="font-semibold">📋 Job gần đây (kiểm duyệt)</h2>
        <div className="mt-3 divide-y divide-slate-100">
          {jobs.map((j) => (
            <div key={j.id} className="flex flex-wrap items-center justify-between gap-3 py-3">
              <div className="min-w-0">
                <Link href={`/jobs/${j.id}`} className="font-medium hover:text-brand-700">
                  #{j.id} — {j.title}
                </Link>
                <div className="text-xs text-slate-500">
                  {j.clientName} · {j.status} · {formatDateTime(j.createdAt)}
                </div>
              </div>
              {j.status === 'OPEN' && (
                <button className="btn-secondary !py-1.5 text-sm !text-rose-600" disabled={busy}
                  onClick={() => act(() => api.post(`/api/admin/jobs/${j.id}/takedown`, { reason: 'Vi phạm quy định đăng tin' }))}>
                  Gỡ job
                </button>
              )}
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
