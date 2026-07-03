'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { api } from '@/lib/api';
import type { FreelancerSearchResult } from '@/lib/types';
import { formatVnd } from '@/lib/format';

export default function FreelancersPage() {
  const [q, setQ] = useState('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<FreelancerSearchResult | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const query = new URLSearchParams();
      if (search) query.set('q', search);
      query.set('page', String(page));
      setResult(await api.get<FreelancerSearchResult>(`/api/users/freelancers?${query}`));
    } finally {
      setLoading(false);
    }
  }, [search, page]);

  useEffect(() => {
    load();
  }, [load]);

  return (
    <div>
      <div className="mb-8 text-center">
        <h1 className="text-3xl font-bold">Danh bạ Freelancer</h1>
        <p className="mt-2 text-slate-600">Tìm chuyên gia theo tên, kỹ năng hoặc lĩnh vực.</p>
      </div>

      <form
        onSubmit={(e) => { e.preventDefault(); setSearch(q); setPage(0); }}
        className="mx-auto mb-8 flex max-w-xl gap-2"
      >
        <input
          className="input"
          placeholder="VD: React, dịch thuật, logo…"
          value={q}
          onChange={(e) => setQ(e.target.value)}
        />
        <button className="btn-primary shrink-0">Tìm</button>
      </form>

      {loading ? (
        <p className="py-12 text-center text-slate-500">Đang tải…</p>
      ) : !result || result.freelancers.length === 0 ? (
        <p className="py-12 text-center text-slate-500">Không tìm thấy freelancer phù hợp.</p>
      ) : (
        <>
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {result.freelancers.map((f) => (
              <Link key={f.id} href={`/profile/${f.id}`} className="card transition hover:border-brand-500 hover:shadow-md">
                <div className="flex items-center gap-3">
                  <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-brand-100 text-lg font-bold text-brand-700">
                    {f.fullName.charAt(0)}
                  </div>
                  <div className="min-w-0">
                    <div className="flex items-center gap-1 font-semibold">
                      <span className="truncate">{f.fullName}</span>
                      {f.premium && <span title="Freelancer Premium">⭐</span>}
                      {f.verified && <span title="Danh tính đã xác minh">✅</span>}
                    </div>
                    <div className="text-xs text-slate-500">
                      {f.ratingCount > 0
                        ? `⭐ ${f.ratingAvg?.toFixed(1)} (${f.ratingCount} đánh giá)`
                        : 'Chưa có đánh giá'}
                    </div>
                  </div>
                </div>
                {f.bio && <p className="mt-3 line-clamp-2 text-sm text-slate-600">{f.bio}</p>}
                {f.skills.length > 0 && (
                  <div className="mt-3 flex flex-wrap gap-1.5">
                    {f.skills.slice(0, 4).map((s) => (
                      <span key={s} className="rounded-full bg-slate-100 px-2.5 py-0.5 text-xs">{s}</span>
                    ))}
                    {f.skills.length > 4 && (
                      <span className="text-xs text-slate-400">+{f.skills.length - 4}</span>
                    )}
                  </div>
                )}
                {f.hourlyRate && (
                  <p className="mt-3 text-sm font-semibold text-brand-700">{formatVnd(f.hourlyRate)}/giờ</p>
                )}
              </Link>
            ))}
          </div>

          {result.totalPages > 1 && (
            <div className="mt-8 flex justify-center gap-2">
              <button className="btn-secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>← Trước</button>
              <span className="self-center text-sm text-slate-500">Trang {page + 1}/{result.totalPages}</span>
              <button className="btn-secondary" disabled={page >= result.totalPages - 1} onClick={() => setPage(page + 1)}>Sau →</button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
