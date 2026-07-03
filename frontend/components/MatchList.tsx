'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { api } from '@/lib/api';
import type { FreelancerMatch } from '@/lib/types';
import { formatVnd } from '@/lib/format';

/** AI gợi ý freelancer phù hợp cho job — hiển thị cho chủ job khi đang nhận chào giá. */
export default function MatchList({ jobId }: { jobId: number }) {
  const [matches, setMatches] = useState<FreelancerMatch[] | null>(null);

  useEffect(() => {
    api.get<FreelancerMatch[]>(`/api/jobs/${jobId}/matches`).then(setMatches).catch(() => setMatches([]));
  }, [jobId]);

  if (!matches || matches.length === 0) return null;

  return (
    <div className="card">
      <h2 className="font-semibold">🤖 AI gợi ý freelancer phù hợp</h2>
      <p className="mt-1 text-xs text-slate-500">
        Xếp hạng theo mức khớp kỹ năng với lĩnh vực của job, đánh giá và kinh nghiệm trên nền tảng.
      </p>
      <div className="mt-4 space-y-3">
        {matches.map((m) => (
          <div key={m.freelancerId} className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-slate-200 p-4">
            <div className="flex min-w-0 items-center gap-3">
              {m.avatarUrl ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img src={m.avatarUrl} alt={m.fullName} className="h-11 w-11 shrink-0 rounded-full object-cover" />
              ) : (
                <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-brand-100 font-bold text-brand-700">
                  {m.fullName.charAt(0)}
                </div>
              )}
              <div className="min-w-0">
                <Link href={`/profile/${m.freelancerId}`} className="font-semibold hover:text-brand-700">
                  {m.fullName} {m.premium && '⭐'}
                </Link>
                <div className="mt-0.5 flex flex-wrap gap-x-3 text-xs text-slate-500">
                  {m.reasons.slice(0, 3).map((r) => (
                    <span key={r}>✓ {r}</span>
                  ))}
                </div>
              </div>
            </div>
            <div className="flex items-center gap-4">
              {m.hourlyRate && (
                <span className="text-sm text-slate-500">{formatVnd(m.hourlyRate)}/giờ</span>
              )}
              <div className="text-right">
                <div className="text-lg font-bold text-brand-700">{Math.round(m.score * 100)}%</div>
                <div className="text-[10px] uppercase tracking-wide text-slate-400">phù hợp</div>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
