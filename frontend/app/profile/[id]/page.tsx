'use client';

import { useEffect, useState } from 'react';
import { useParams } from 'next/navigation';
import { api } from '@/lib/api';
import type { RatingSummary, User } from '@/lib/types';
import { formatVnd, formatDate } from '@/lib/format';

export default function ProfilePage() {
  const { id } = useParams<{ id: string }>();
  const [profile, setProfile] = useState<User | null>(null);
  const [ratings, setRatings] = useState<RatingSummary | null>(null);

  useEffect(() => {
    api.get<User>(`/api/users/${id}`).then(setProfile).catch(() => {});
    api.get<RatingSummary>(`/api/users/${id}/reviews`).then(setRatings).catch(() => {});
  }, [id]);

  if (!profile) return <p className="py-12 text-center text-slate-500">Đang tải…</p>;

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div className="card">
        <div className="flex items-center gap-4">
          {profile.avatarUrl ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={profile.avatarUrl} alt={profile.fullName} className="h-16 w-16 rounded-full object-cover" />
          ) : (
            <div className="flex h-16 w-16 items-center justify-center rounded-full bg-brand-100 text-2xl font-bold text-brand-700">
              {profile.fullName.charAt(0)}
            </div>
          )}
          <div>
            <h1 className="flex items-center gap-2 text-2xl font-bold">
              {profile.fullName}
              {profile.verified && (
                <span className="rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-semibold text-emerald-700"
                  title="Danh tính đã được VietLancer xác minh">
                  ✅ Đã xác minh
                </span>
              )}
            </h1>
            <p className="text-sm text-slate-500">
              {profile.role === 'CLIENT' ? 'Người thuê' : 'Freelancer'} · Tham gia {formatDate(profile.createdAt)}
            </p>
            {ratings && ratings.count > 0 && (
              <p className="mt-1 text-sm">
                ⭐ <b>{ratings.average?.toFixed(1)}</b> ({ratings.count} đánh giá)
              </p>
            )}
          </div>
        </div>
        {profile.bio && <p className="mt-4 text-slate-700">{profile.bio}</p>}
        {profile.skills.length > 0 && (
          <div className="mt-4 flex flex-wrap gap-2">
            {profile.skills.map((s) => (
              <span key={s} className="rounded-full bg-slate-100 px-3 py-1 text-xs font-medium">{s}</span>
            ))}
          </div>
        )}
        {profile.hourlyRate && (
          <p className="mt-4 text-sm text-slate-600">
            Giá theo giờ: <b>{formatVnd(profile.hourlyRate)}/giờ</b>
          </p>
        )}
      </div>

      {ratings && ratings.reviews.length > 0 && (
        <div className="card">
          <h2 className="font-semibold">Đánh giá</h2>
          <div className="mt-2 divide-y divide-slate-100">
            {ratings.reviews.map((r) => (
              <div key={r.id} className="py-3">
                <div className="flex items-center justify-between">
                  <span className="text-sm font-medium">{r.reviewerName}</span>
                  <span className="text-sm">{'⭐'.repeat(r.rating)}</span>
                </div>
                <p className="text-xs text-slate-400">Job: {r.jobTitle}</p>
                {r.comment && <p className="mt-1 text-sm text-slate-700">{r.comment}</p>}
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
