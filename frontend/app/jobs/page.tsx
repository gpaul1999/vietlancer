'use client';

import { Suspense, useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { api } from '@/lib/api';
import { useAuth } from '@/lib/auth-context';
import type { JobSearchResult, Topic } from '@/lib/types';
import JobCard from '@/components/JobCard';

function JobsContent() {
  const params = useSearchParams();
  const { user } = useAuth();
  const [topics, setTopics] = useState<Topic[]>([]);
  const [selectedTopic, setSelectedTopic] = useState(params.get('topic') || '');
  const [q, setQ] = useState('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<JobSearchResult | null>(null);
  const [loading, setLoading] = useState(true);
  const [followed, setFollowed] = useState<string[]>([]);

  useEffect(() => {
    api.get<Topic[]>('/api/topics').then(setTopics).catch(() => {});
  }, []);

  useEffect(() => {
    if (user) {
      api.get<string[]>('/api/topics/followed').then(setFollowed).catch(() => {});
    }
  }, [user]);

  const toggleFollow = async (slug: string) => {
    const isFollowing = followed.includes(slug);
    try {
      if (isFollowing) {
        await api.delete(`/api/topics/${slug}/follow`);
        setFollowed(followed.filter((s) => s !== slug));
      } else {
        await api.post(`/api/topics/${slug}/follow`);
        setFollowed([...followed, slug]);
      }
    } catch {
      /* ignore */
    }
  };

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const query = new URLSearchParams();
      if (selectedTopic) query.set('topic', selectedTopic);
      if (search) query.set('q', search);
      query.set('page', String(page));
      setResult(await api.get<JobSearchResult>(`/api/jobs?${query}`));
    } finally {
      setLoading(false);
    }
  }, [selectedTopic, search, page]);

  useEffect(() => {
    load();
  }, [load]);

  return (
    <div className="grid gap-8 lg:grid-cols-[240px_1fr]">
      {/* Sidebar topic filter */}
      <aside>
        <h2 className="mb-3 font-semibold">Lĩnh vực</h2>
        <div className="space-y-1">
          <button
            onClick={() => { setSelectedTopic(''); setPage(0); }}
            className={`block w-full rounded-lg px-3 py-2 text-left text-sm ${!selectedTopic ? 'bg-brand-50 font-semibold text-brand-700' : 'hover:bg-slate-100'}`}
          >
            Tất cả
          </button>
          {topics.map((t) => (
            <button
              key={t.slug}
              onClick={() => { setSelectedTopic(t.slug); setPage(0); }}
              className={`block w-full rounded-lg px-3 py-2 text-left text-sm ${selectedTopic === t.slug ? 'bg-brand-50 font-semibold text-brand-700' : 'hover:bg-slate-100'}`}
            >
              {t.icon} {t.name}
            </button>
          ))}
        </div>
      </aside>

      {/* Job list */}
      <div>
        {user && selectedTopic && (
          <button
            onClick={() => toggleFollow(selectedTopic)}
            className={`mb-4 inline-flex items-center gap-2 rounded-full border px-4 py-2 text-sm font-medium transition ${
              followed.includes(selectedTopic)
                ? 'border-brand-500 bg-brand-50 text-brand-700'
                : 'border-slate-300 text-slate-600 hover:bg-slate-50'
            }`}
          >
            {followed.includes(selectedTopic)
              ? '🔔 Đang theo dõi — sẽ báo khi có job mới'
              : '🔕 Theo dõi lĩnh vực này để nhận thông báo job mới'}
          </button>
        )}
        <form
          onSubmit={(e) => { e.preventDefault(); setSearch(q); setPage(0); }}
          className="mb-6 flex gap-2"
        >
          <input
            className="input"
            placeholder="Tìm kiếm theo từ khóa…"
            value={q}
            onChange={(e) => setQ(e.target.value)}
          />
          <button className="btn-primary shrink-0">Tìm</button>
        </form>

        {loading ? (
          <p className="py-12 text-center text-slate-500">Đang tải…</p>
        ) : !result || result.jobs.length === 0 ? (
          <p className="py-12 text-center text-slate-500">Không có job nào phù hợp.</p>
        ) : (
          <>
            <p className="mb-4 text-sm text-slate-500">{result.totalElements} công việc</p>
            <div className="space-y-4">
              {result.jobs.map((job) => (
                <JobCard key={job.id} job={job} />
              ))}
            </div>
            {result.totalPages > 1 && (
              <div className="mt-6 flex justify-center gap-2">
                <button className="btn-secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>
                  ← Trước
                </button>
                <span className="self-center text-sm text-slate-500">
                  Trang {page + 1}/{result.totalPages}
                </span>
                <button className="btn-secondary" disabled={page >= result.totalPages - 1} onClick={() => setPage(page + 1)}>
                  Sau →
                </button>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}

export default function JobsPage() {
  return (
    <Suspense fallback={<p className="py-12 text-center text-slate-500">Đang tải…</p>}>
      <JobsContent />
    </Suspense>
  );
}
