'use client';

import { useCallback, useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { api, ApiError } from '@/lib/api';
import { useAuth } from '@/lib/auth-context';
import type { Bid, Job } from '@/lib/types';
import { formatVnd, formatDate, timeAgo } from '@/lib/format';
import TopicBadge from '@/components/TopicBadge';
import MilestonePanel from '@/components/MilestonePanel';
import DisputePanel from '@/components/DisputePanel';
import MatchList from '@/components/MatchList';
import PriceHint from '@/components/PriceHint';

export default function JobDetailClient() {
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
  const [hasOpenDispute, setHasOpenDispute] = useState(false);
  const [acceptingBid, setAcceptingBid] = useState<number | null>(null);
  const [saved, setSaved] = useState(false);

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

  useEffect(() => {
    if (!user) return;
    api.get<{ saved: boolean }>(`/api/jobs/${id}/saved-status`).then((r) => setSaved(r.saved)).catch(() => {});
  }, [id, user]);

  const toggleSave = async () => {
    try {
      await api.post(`/api/jobs/${id}/${saved ? 'unsave' : 'save'}`);
      setSaved(!saved);
    } catch {
      /* ignore */
    }
  };

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
          <div className="flex items-center gap-2">
            {user && (
              <button
                onClick={toggleSave}
                title={saved ? 'Bỏ lưu job' : 'Lưu job để xem sau'}
                className={`rounded-full border px-3 py-1 text-sm font-semibold transition ${
                  saved ? 'border-rose-200 bg-rose-50 text-rose-600' : 'border-slate-300 text-slate-500 hover:bg-slate-50'
                }`}
              >
                {saved ? '❤️ Đã lưu' : '🤍 Lưu'}
              </button>
            )}
            <span className="rounded-full bg-slate-100 px-3 py-1 text-sm font-semibold">{job.status}</span>
          </div>
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
            <button className="btn-primary" disabled={busy || hasOpenDispute} onClick={() => act(() => api.post(`/api/jobs/${job.id}/complete`), 'Đã hoàn thành job!')}>
              ✅ Xác nhận hoàn thành{job.milestoneBased ? '' : ' & giải ngân'}
            </button>
            <button className="btn-secondary" disabled={busy || hasOpenDispute} onClick={() => act(() => api.post(`/api/jobs/${job.id}/cancel`), 'Đã hủy job, escrow được hoàn lại.')}>
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

      {/* Dispute banner + form */}
      <DisputePanel
        jobId={job.id}
        isParticipant={Boolean(isOwner || isAssigned)}
        jobInProgress={job.status === 'IN_PROGRESS'}
        currentUserId={user?.id}
        onChanged={setHasOpenDispute}
      />

      {/* Milestone panel */}
      {job.milestoneBased && (isOwner || isAssigned) && (
        <MilestonePanel
          job={job}
          isOwner={Boolean(isOwner)}
          isAssigned={Boolean(isAssigned)}
          frozen={hasOpenDispute || job.status !== 'IN_PROGRESS'}
          onChanged={load}
        />
      )}

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

      {/* AI matching cho chủ job đang nhận chào giá */}
      {isOwner && job.status === 'OPEN' && <MatchList jobId={job.id} />}

      {/* Bid form for freelancers */}
      {user?.role === 'FREELANCER' && job.status === 'OPEN' && !myBid && (
        <div className="card">
          <h2 className="font-semibold">Gửi chào giá</h2>
          {job.topics[0] && (
            <div className="mt-2">
              <PriceHint topicSlug={job.topics[0].slug} label="Giá chào tham khảo" />
            </div>
          )}
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
                {bid.warnings && bid.warnings.length > 0 && (
                  <div className="mt-2 flex flex-wrap gap-1.5">
                    {bid.warnings.map((w) => (
                      <span key={w} className="rounded-full bg-amber-50 px-2.5 py-0.5 text-xs font-medium text-amber-700">
                        ⚠️ {w}
                      </span>
                    ))}
                  </div>
                )}
                <p className="mt-2 text-sm text-slate-700">{bid.coverLetter}</p>
                <div className="mt-3 flex flex-wrap gap-2">
                  {job.status === 'OPEN' && bid.status === 'PENDING' && acceptingBid !== bid.id && (
                    <button className="btn-primary !py-1.5 text-sm" disabled={busy} onClick={() => setAcceptingBid(bid.id)}>
                      Chọn freelancer này
                    </button>
                  )}
                  <button className="btn-secondary !py-1.5 text-sm" onClick={() => startChat(bid.freelancer.id)}>
                    💬 Nhắn tin
                  </button>
                  <span className="self-center text-xs font-semibold text-slate-400">{bid.status}</span>
                </div>
                {acceptingBid === bid.id && (
                  <div className="mt-3 rounded-xl border border-brand-200 bg-brand-50/50 p-4">
                    <p className="text-sm font-semibold">Chọn hình thức thanh toán:</p>
                    <div className="mt-2 grid gap-2 md:grid-cols-2">
                      <button
                        className="rounded-xl border border-slate-300 bg-white p-3 text-left transition hover:border-brand-500 disabled:opacity-50"
                        disabled={busy}
                        onClick={() => act(() => api.post(`/api/bids/${bid.id}/accept`, { useMilestones: false }), 'Đã chọn freelancer! Toàn bộ tiền được giữ trong escrow.').then(() => setAcceptingBid(null))}
                      >
                        <div className="font-semibold">🔒 Escrow toàn bộ</div>
                        <div className="mt-1 text-xs text-slate-500">
                          Giữ ngay {formatVnd(bid.amount)} vào escrow. Giải ngân một lần khi hoàn thành.
                        </div>
                      </button>
                      <button
                        className="rounded-xl border border-slate-300 bg-white p-3 text-left transition hover:border-brand-500 disabled:opacity-50"
                        disabled={busy}
                        onClick={() => act(() => api.post(`/api/bids/${bid.id}/accept`, { useMilestones: true }), 'Đã chọn freelancer! Hãy tạo các mốc công việc và nạp escrow từng mốc.').then(() => setAcceptingBid(null))}
                      >
                        <div className="font-semibold">📋 Theo milestone</div>
                        <div className="mt-1 text-xs text-slate-500">
                          Chia job thành các mốc, nạp và giải ngân từng mốc. Không khóa toàn bộ vốn.
                        </div>
                      </button>
                    </div>
                    <button className="mt-2 text-xs text-slate-400 hover:underline" onClick={() => setAcceptingBid(null)}>
                      Bỏ qua
                    </button>
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
