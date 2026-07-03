'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { api } from '@/lib/api';
import { useAuth } from '@/lib/auth-context';
import type { Bid, Job, SubscriptionStatus, Wallet } from '@/lib/types';
import { formatVnd, formatDate } from '@/lib/format';
import JobCard from '@/components/JobCard';

export default function DashboardPage() {
  const { user, loading } = useAuth();
  const [jobs, setJobs] = useState<Job[]>([]);
  const [bids, setBids] = useState<Bid[]>([]);
  const [suggested, setSuggested] = useState<Job[]>([]);
  const [savedJobs, setSavedJobs] = useState<Job[]>([]);
  const [wallet, setWallet] = useState<Wallet | null>(null);
  const [sub, setSub] = useState<SubscriptionStatus | null>(null);

  useEffect(() => {
    if (!user) return;
    api.get<Job[]>('/api/jobs/mine').then(setJobs).catch(() => {});
    api.get<Wallet>('/api/wallet').then(setWallet).catch(() => {});
    api.get<SubscriptionStatus>('/api/subscriptions/me').then(setSub).catch(() => {});
    api.get<Job[]>('/api/jobs/saved').then(setSavedJobs).catch(() => {});
    if (user.role === 'FREELANCER') {
      api.get<Bid[]>('/api/bids/mine').then(setBids).catch(() => {});
      api.get<Job[]>('/api/jobs/suggested').then(setSuggested).catch(() => {});
    }
  }, [user]);

  if (loading) return <p className="py-12 text-center text-slate-500">Đang tải…</p>;
  if (!user) return <p className="py-12 text-center text-slate-500">Hãy đăng nhập để xem bảng điều khiển.</p>;

  return (
    <div className="space-y-8">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold">
            Xin chào, {user.fullName} {sub?.premium && '⭐'}
          </h1>
          <p className="text-sm text-slate-500">
            {user.role === 'CLIENT' ? 'Tài khoản người thuê' : 'Tài khoản freelancer'}
            {sub?.premium && ` · Premium đến ${formatDate(sub.expiresAt)}`}
          </p>
        </div>
        <div className="flex gap-2">
          <Link href="/settings" className="btn-secondary">Sửa hồ sơ</Link>
          {user.role === 'CLIENT' && (
            <Link href="/post-job" className="btn-primary">+ Đăng việc mới</Link>
          )}
        </div>
      </div>

      {/* Stats */}
      <div className="grid gap-4 md:grid-cols-3">
        <div className="card">
          <div className="text-sm text-slate-500">Số dư ví</div>
          <div className="mt-1 text-2xl font-bold">{formatVnd(wallet?.balance)}</div>
          <Link href="/wallet" className="mt-2 inline-block text-sm font-medium text-brand-600 hover:underline">
            Quản lý ví →
          </Link>
        </div>
        <div className="card">
          <div className="text-sm text-slate-500">Đang giữ trong escrow</div>
          <div className="mt-1 text-2xl font-bold">{formatVnd(wallet?.escrowBalance)}</div>
        </div>
        <div className="card">
          <div className="text-sm text-slate-500">{user.role === 'CLIENT' ? 'Job đã đăng' : 'Job được giao'}</div>
          <div className="mt-1 text-2xl font-bold">{jobs.length}</div>
        </div>
      </div>

      {/* AI job suggestions for freelancers */}
      {user.role === 'FREELANCER' && suggested.length > 0 && (
        <section>
          <h2 className="mb-1 text-xl font-bold">🤖 Gợi ý cho bạn</h2>
          <p className="mb-4 text-sm text-slate-500">
            AI phân tích kỹ năng trong hồ sơ của bạn và tìm các job đang mở phù hợp nhất.
          </p>
          <div className="space-y-4">
            {suggested.map((j) => <JobCard key={j.id} job={j} />)}
          </div>
        </section>
      )}
      {user.role === 'FREELANCER' && suggested.length === 0 && (
        <p className="rounded-xl bg-amber-50 p-3 text-sm text-amber-700">
          💡 Thêm kỹ năng vào <Link href="/settings" className="font-semibold underline">hồ sơ</Link> để AI
          gợi ý job phù hợp cho bạn.
        </p>
      )}

      {/* Jobs */}
      <section>
        <h2 className="mb-4 text-xl font-bold">
          {user.role === 'CLIENT' ? 'Job của tôi' : 'Job tôi đang làm'}
        </h2>
        {jobs.length === 0 ? (
          <p className="text-slate-500">Chưa có job nào.</p>
        ) : (
          <div className="space-y-4">
            {jobs.map((j) => <JobCard key={j.id} job={j} />)}
          </div>
        )}
      </section>

      {/* Saved jobs */}
      {savedJobs.length > 0 && (
        <section>
          <h2 className="mb-4 text-xl font-bold">❤️ Job đã lưu</h2>
          <div className="space-y-4">
            {savedJobs.map((j) => <JobCard key={j.id} job={j} />)}
          </div>
        </section>
      )}

      {/* Freelancer bids */}
      {user.role === 'FREELANCER' && (
        <section>
          <h2 className="mb-4 text-xl font-bold">Chào giá của tôi</h2>
          {bids.length === 0 ? (
            <p className="text-slate-500">
              Chưa có chào giá nào. <Link href="/jobs" className="text-brand-600 hover:underline">Tìm việc ngay →</Link>
            </p>
          ) : (
            <div className="card divide-y divide-slate-100 !p-0">
              {bids.map((b) => (
                <Link key={b.id} href={`/jobs/${b.jobId}`} className="block px-5 py-4 hover:bg-slate-50">
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-medium">{b.jobTitle}</span>
                    <span className={`rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                      b.status === 'ACCEPTED' ? 'bg-emerald-50 text-emerald-700'
                      : b.status === 'REJECTED' ? 'bg-rose-50 text-rose-600'
                      : 'bg-amber-50 text-amber-700'
                    }`}>
                      {b.status}
                    </span>
                  </div>
                  <div className="mt-1 text-sm text-slate-500">
                    {formatVnd(b.amount)} · {b.deliveryDays} ngày
                  </div>
                </Link>
              ))}
            </div>
          )}
        </section>
      )}
    </div>
  );
}
