'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { api } from '@/lib/api';
import { useAuth } from '@/lib/auth-context';
import type { ClassifyPreview, Job } from '@/lib/types';
import TopicBadge from '@/components/TopicBadge';
import PriceHint from '@/components/PriceHint';

export default function PostJobPage() {
  const { user, loading } = useAuth();
  const router = useRouter();
  const [form, setForm] = useState({ title: '', description: '', budgetMin: '', budgetMax: '', deadline: '' });
  const [preview, setPreview] = useState<ClassifyPreview | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState<'preview' | 'submit' | null>(null);

  if (!loading && (!user || user.role !== 'CLIENT')) {
    return (
      <div className="card mx-auto max-w-lg text-center">
        <p className="text-lg font-semibold">Chỉ tài khoản Người thuê (client) mới đăng được việc.</p>
        <p className="mt-2 text-sm text-slate-500">Hãy đăng nhập bằng tài khoản client hoặc đăng ký mới.</p>
      </div>
    );
  }

  const canPreview = form.title.length >= 5 && form.description.length >= 30;

  const doPreview = async () => {
    setBusy('preview');
    setError('');
    try {
      setPreview(await api.post<ClassifyPreview>('/api/ai/classify-preview', {
        title: form.title,
        description: form.description,
      }));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không phân tích được');
    } finally {
      setBusy(null);
    }
  };

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy('submit');
    setError('');
    try {
      const job = await api.post<Job>('/api/jobs', {
        title: form.title,
        description: form.description,
        budgetMin: form.budgetMin ? Number(form.budgetMin) : null,
        budgetMax: form.budgetMax ? Number(form.budgetMax) : null,
        deadline: form.deadline || null,
      });
      router.push(`/jobs/${job.id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Đăng việc thất bại');
      setBusy(null);
    }
  };

  return (
    <div className="mx-auto max-w-3xl">
      <h1 className="text-3xl font-bold">Đăng việc mới</h1>
      <p className="mt-2 text-slate-600">
        Bạn <b>không cần chọn danh mục</b> — chỉ cần mô tả càng chi tiết càng tốt, AI sẽ tự phân loại
        và một việc có thể thuộc nhiều lĩnh vực cùng lúc.
      </p>

      <form onSubmit={submit} className="card mt-6 space-y-5">
        <div>
          <label className="label">Tiêu đề</label>
          <input
            className="input"
            placeholder="VD: Thiết kế logo cho quán cà phê"
            value={form.title}
            maxLength={200}
            onChange={(e) => setForm({ ...form, title: e.target.value })}
            required
          />
        </div>
        <div>
          <label className="label">Mô tả chi tiết (tối thiểu 30 ký tự)</label>
          <textarea
            className="input min-h-[160px]"
            placeholder="Mô tả yêu cầu, phạm vi công việc, kỹ năng mong muốn, sản phẩm bàn giao…"
            value={form.description}
            maxLength={10000}
            onChange={(e) => setForm({ ...form, description: e.target.value })}
            required
          />
        </div>
        <div className="grid gap-4 md:grid-cols-3">
          <div>
            <label className="label">Ngân sách từ (VND)</label>
            <input className="input" type="number" min={0} value={form.budgetMin} onChange={(e) => setForm({ ...form, budgetMin: e.target.value })} />
          </div>
          <div>
            <label className="label">Đến (VND)</label>
            <input className="input" type="number" min={0} value={form.budgetMax} onChange={(e) => setForm({ ...form, budgetMax: e.target.value })} />
          </div>
          <div>
            <label className="label">Hạn chót</label>
            <input className="input" type="date" value={form.deadline} onChange={(e) => setForm({ ...form, deadline: e.target.value })} />
          </div>
        </div>

        {/* AI preview */}
        <div className="rounded-xl border border-dashed border-brand-300 bg-brand-50/50 p-4">
          <div className="flex items-center justify-between gap-3">
            <div>
              <p className="font-semibold text-brand-700">🤖 AI phân tích lĩnh vực</p>
              <p className="text-xs text-slate-500">Xem trước topic AI sẽ gán cho việc này.</p>
            </div>
            <button type="button" className="btn-secondary shrink-0" onClick={doPreview} disabled={!canPreview || busy === 'preview'}>
              {busy === 'preview' ? 'Đang phân tích…' : 'Phân tích'}
            </button>
          </div>
          {preview && (
            <div className="mt-3 space-y-2">
              <div className="flex flex-wrap gap-2">
                {preview.topics.map((t) => (
                  <TopicBadge key={t.slug} name={t.name} icon={t.icon} confidence={t.confidence} />
                ))}
              </div>
              <p className="text-xs text-slate-500">
                {preview.explanation} <i>(engine: {preview.engine})</i>
              </p>
              {preview.topics[0] && (
                <PriceHint topicSlug={preview.topics[0].slug} label="Ngân sách tham khảo cho lĩnh vực này" />
              )}
            </div>
          )}
        </div>

        {error && <p className="text-sm text-rose-600">{error}</p>}
        <button className="btn-primary w-full" disabled={busy === 'submit'}>
          {busy === 'submit' ? 'Đang đăng…' : 'Đăng việc'}
        </button>
      </form>
    </div>
  );
}
