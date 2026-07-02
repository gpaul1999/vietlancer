import Link from 'next/link';
import type { Job } from '@/lib/types';
import { formatVnd, timeAgo } from '@/lib/format';
import TopicBadge from './TopicBadge';

const statusLabel: Record<string, { text: string; cls: string }> = {
  OPEN: { text: 'Đang nhận chào giá', cls: 'bg-emerald-50 text-emerald-700' },
  IN_PROGRESS: { text: 'Đang thực hiện', cls: 'bg-amber-50 text-amber-700' },
  COMPLETED: { text: 'Hoàn thành', cls: 'bg-slate-100 text-slate-600' },
  CANCELLED: { text: 'Đã hủy', cls: 'bg-rose-50 text-rose-600' },
};

export default function JobCard({ job }: { job: Job }) {
  const st = statusLabel[job.status];
  return (
    <Link href={`/jobs/${job.id}`} className="card block transition hover:border-brand-500 hover:shadow-md">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <h3 className="text-lg font-semibold text-slate-900">{job.title}</h3>
        <div className="flex items-center gap-2">
          {job.client.premium && (
            <span className="rounded-full bg-amber-100 px-2.5 py-1 text-xs font-semibold text-amber-700">
              ⭐ Ưu tiên
            </span>
          )}
          <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${st.cls}`}>{st.text}</span>
        </div>
      </div>

      <p className="mt-2 line-clamp-2 text-sm text-slate-600">{job.description}</p>

      <div className="mt-3 flex flex-wrap gap-2">
        {job.topics.map((t) => (
          <TopicBadge key={t.slug} name={t.name} icon={t.icon} />
        ))}
      </div>

      <div className="mt-4 flex flex-wrap items-center justify-between gap-2 text-sm text-slate-500">
        <span>
          Ngân sách:{' '}
          <b className="text-slate-800">
            {formatVnd(job.budgetMin)} – {formatVnd(job.budgetMax)}
          </b>
        </span>
        <span>
          {job.bidCount} chào giá · {timeAgo(job.createdAt)} · bởi {job.client.fullName}
        </span>
      </div>
    </Link>
  );
}
