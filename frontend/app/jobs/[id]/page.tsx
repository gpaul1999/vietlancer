'use client';

import { useCallback, useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { api, ApiError } from '@/lib/api';
import { useAuth } from '@/lib/auth-context';
import type { Bid, Job } from '@/lib/types';
import { formatVnd, formatDate, timeAgo } from '@/lib/format';
import TopicBadge from '@/components/TopicBadge';

export default function JobDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { user } = useAuth();
  const router = useRouter();
  const [job, setJob] = useState<Job | null>(null);
  const [bids, setBids] = useState<Bid[]>([]);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [bidForm, setBidForm] = useState({ amount: '', deliveryDays: '', coverLetter: '' });
  const [review, setReview] = useState({ rating: 5, comment: '' });
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      setJob(await api.get<Job>(`/api/jobs/${id}`));
      setBids(await api.get<Bid[]>(`/api/jobs/${id}/bids`));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không tải được job');
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  if (!job) {
    return <p className="py-12 text-center text-slate-500">{error || 'Đang tải…'}</p>;
  }

  const isOwner = user?.id === job.client.id;
  const isAssigned = user?.id === job.assignedFreelancerId;
  const myBid = user ? bids.find((b) => b.freelancer.id === user.id) : undefined;

  const act = async (fn: () => Promise<unknown>, successMsg?: string) => {
    setBusy(true);
    setError('');
    setNotice('');
    try {
      await fn();
      if (successMsg) setNotice(successMsg);
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Có lỗi xảy ra');
    } finally {
      setBusy(false);
    }
  };

  const placeBid = () =>
    act(
      () => api.post(`/api/jobs/${job.id}/bids`, {
        amount: Number(bidForm.amount),
        deliveryDays: Number(bidForm.deliveryDays),
        coverLetter: bidForm.coverLetter,
      }),
      'Đã gửi chào giá!',
    );

  const startChat = async (freelancerId: number) => {
    setError('');
    try {
      const conv = await api.post<{ id: number }>('/api/chats/start', { jobId: job.id, freelancerId });
      router.push(`/messages?c=${conv.id}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Không mở được hội thoại');
    }
  };

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      {/* Job info */}
      <div className="card">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <h1 className="text-2xl font-bold">{job.title}</h1>
          <span className="rounded-full bg-slate-100 px-3 py-1 text-sm font-semibold">{job.status}</span>
        </div>
        <div className="mt-3 flex flex-wrap gap-2">
          {job.topics.map((t) => (
            <TopicBadge key={t.slug} name={t.name} icon={t.icon} />
          ))}
        </div>
        <p className="mt-4 whitespace-pre-wrap text-slate-700">{job.description}</p>

        <div className="mt-5 grid gap-3 rounded-xl bg-slate-50 p-4 text-sm md:grid-cols-3">
          <div>
            <div className="text-slate-500">Ngân sách</div>
            <div className="font-semibold">{formatVnd(job.budgetMin)} – {formatVnd(job.budgetMax)}</div>
          </div>
          <div>
            <div className="text-slate-500">Hạn chót</div>
            <div className="font-semibold">{formatDate(job.deadline)}</div>
          </div>
          <div>
            <div className="text-slate-500">Người thuê</div>
            <div className="font-semibold">
              {job.client.fullName} {job.client.premium && '⭐'}
            </div>
          </div>
        </div>

        {job.aiExplanation && (
          <p className="mt-4 rounded-xl bg-brand-50 p-3 text-xs text-slate-600">
            🤖 <b>AI ({job.aiEngine}):</b> {job.aiExplanation}
          </p>
        )}

        {/* Owner actions */}
        {isOwner && job.status === 'IN_PROGRESS' && (
          <div className="mt-4 flex gap-2">
            <button className="btn-primary" disabled={busy} onClick={() => act(() => api.post(`/api/jobs/${job.id}/complete`), 'Đã hoàn thành job và giải ngân cho freelancer!')}>
              ✅ Xác nhận hoàn thành & giải ngân
            </button>
            <button className="btn-secondary" disabled={busy} onClick={() => act(() => api.post(`/api/jobs/${job.id}/cancel`), 'Đã hủy job, escrow được hoàn lại.')}>
              Hủy job
            </button>
          </div>
        )}
        {isOwner && job.status === 'OPEN' && (
          <button className="btn-secondary mt-4" disabled={busy} onClick={() => act(() => api.post(`/api/jobs/${job.id}/cancel`), 'Đã hủy job.')}>
            Hủy job
          </button>
        )}
        {isAssigned && job.status === 'IN_PROGRESS' && (
          <button className="btn-primary mt-4" onClick={() => startChat(user!.id)}>
            💬 Nhắn tin với người thuê
          </button>
        )}
      </div>

      {notice && <p className="rounded-xl bg-emerald-50 p-3 text-sm text-emerald-700">{notice}</p>}
      {error && <p className="rounded-xl bg-rose-50 p-3 text-sm text-rose-600">{error}</p>}

      {/* Review form for completed jobs */}
      {job.status === 'COMPLETED' && (isOwner || isAssigned) && (
        <div className="card">
          <h2 className="font-semibold">Đánh giá đối tác</h2>
          <div className="mt-3 flex items-center gap-1">
            {[1, 2, 3, 4, 5].map((star) => (
              <button key={star} type="button" onClick={() => setReview({ ...review, rating: star })} className="text-2xl">
                {star <= review.rating ? '⭐' : '☆'}
              </button>
            ))}
          </div>
          <textarea
            className="input mt-3"
            placeholder="Chia sẻ trải nghiệm hợp tác…"
            value={review.comment}
            onChange={(e) => setReview({ ...review, comment: e.target.value })}
          />
          <button
            className="btn-primary mt-3"
            disabled={busy}
            onClick={() => act(() => api.post('/api/reviews', { jobId: job.id, rating: review.rating, comment: review.comment }), 'Cảm ơn bạn đã đánh giá!')}
          >
            Gửi đánh giá
          </button>
        </div>
      )}

      {/* Bid form for freelancers */}
      {user?.role === 'FREELANCER' && job.status === 'OPEN' && !myBid && (
        <div className="card">
          <h2 className="font-semibold">Gửi chào giá</h2>
          <div className="mt-3 grid gap-3 md:grid-cols-2">
            <div>
              <label className="label">Báo giá (VND)</label>
              <input className="input" type="number" min={1} value={bidForm.amount} onChange={(e) => setBidForm({ ...bidForm, amount: e.target.value })} />
            </div>
            <div>
              <label className="label">Thời gian hoàn thành (ngày)</label>
              <input className="input" type="number" min={1} max={365} value={bidForm.deliveryDays} onChange={(e) => setBidForm({ ...bidForm, deliveryDays: e.target.value })} />
            </div>
          </div>
          <textarea
            className="input mt-3 min-h-[100px]"
            placeholder="Thư chào: kinh nghiệm liên quan, kế hoạch thực hiện…"
            value={bidForm.coverLetter}
            onChange={(e) => setBidForm({ ...bidForm, coverLetter: e.target.value })}
          />
          <button className="btn-primary mt-3" disabled={busy || !bidForm.amount || !bidForm.deliveryDays || !bidForm.coverLetter} onClick={placeBid}>
            Gửi chào giá
          </button>
        </div>
      )}

      {myBid && (
        <div className="card border-brand-200 bg-brand-50/40">
          <p className="text-sm">
            Bạn đã chào giá <b>{formatVnd(myBid.amount)}</b> ({myBid.deliveryDays} ngày) — trạng thái:{' '}
            <b>{myBid.status}</b>
          </p>
        </div>
      )}

      {/* Bid list (owner sees all) */}
      {isOwner && bids.length > 0 && (
        <div className="card">
          <h2 className="font-semibold">Chào giá ({bids.length})</h2>
          <div className="mt-3 divide-y divide-slate-100">
            {bids.map((bid) => (
              <div key={bid.id} className="py-4">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div>
                    <span className="font-semibold">{bid.freelancer.fullName}</span>{' '}
                    {bid.freelancer.premium && <span title="Freelancer Premium">⭐</span>}
                    <span className="ml-2 text-sm text-slate-500">{timeAgo(bid.createdAt)}</span>
                  </div>
                  <div className="text-right">
                    <div className="font-bold text-brand-700">{formatVnd(bid.amount)}</div>
                    <div className="text-xs text-slate-500">{bid.deliveryDays} ngày</div>
                  </div>
                </div>
                {bid.freelancer.skills && (
                  <p className="mt-1 text-xs text-slate-500">Kỹ năng: {bid.freelancer.skills}</p>
                )}
                <p className="mt-2 text-sm text-slate-700">{bid.coverLetter}</p>
                <div className="mt-3 flex gap-2">
                  {job.status === 'OPEN' && bid.status === 'PENDING' && (
                    <button
                      className="btn-primary !py-1.5 text-sm"
                      disabled={busy}
                      onClick={() => act(() => api.post(`/api/bids/${bid.id}/accept`), 'Đã chọn freelancer! Tiền được giữ trong escrow và hội thoại đã mở.')}
                    >
                      Chọn freelancer này
                    </button>
                  )}
                  <button className="btn-secondary !py-1.5 text-sm" onClick={() => startChat(bid.freelancer.id)}>
                    💬 Nhắn tin
                  </button>
                  <span className="self-center text-xs font-semibold text-slate-400">{bid.status}</span>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
