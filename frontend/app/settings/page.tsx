'use client';

import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { useAuth } from '@/lib/auth-context';
import type { User } from '@/lib/types';

export default function SettingsPage() {
  const { user, loading, refresh } = useAuth();
  const [form, setForm] = useState({ fullName: '', bio: '', skills: '', hourlyRate: '', avatarUrl: '' });
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (user) {
      setForm({
        fullName: user.fullName,
        bio: user.bio || '',
        skills: user.skills.join(', '),
        hourlyRate: user.hourlyRate ? String(user.hourlyRate) : '',
        avatarUrl: user.avatarUrl || '',
      });
    }
  }, [user]);

  if (loading) return <p className="py-12 text-center text-slate-500">Đang tải…</p>;
  if (!user) return <p className="py-12 text-center text-slate-500">Hãy đăng nhập để chỉnh sửa hồ sơ.</p>;

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError('');
    setSaved(false);
    try {
      await api.put<User>('/api/users/me', {
        fullName: form.fullName,
        bio: form.bio,
        skills: form.skills.split(',').map((s) => s.trim()).filter(Boolean),
        hourlyRate: form.hourlyRate ? Number(form.hourlyRate) : null,
        avatarUrl: form.avatarUrl || null,
      });
      await refresh();
      setSaved(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Lưu thất bại');
    } finally {
      setBusy(false);
    }
  };

  const isFreelancer = user.role === 'FREELANCER';

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="text-2xl font-bold">Hồ sơ của tôi</h1>
      <p className="mt-1 text-sm text-slate-500">
        {isFreelancer
          ? 'Hồ sơ chi tiết giúp AI gợi ý job phù hợp hơn và tăng cơ hội được chọn.'
          : 'Thông tin hiển thị với freelancer khi bạn đăng việc.'}
      </p>

      <form onSubmit={submit} className="card mt-6 space-y-4">
        <div>
          <label className="label">Họ và tên</label>
          <input className="input" value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} required />
        </div>
        <div>
          <label className="label">Giới thiệu bản thân</label>
          <textarea
            className="input min-h-[120px]"
            maxLength={2000}
            placeholder={isFreelancer ? 'Kinh nghiệm, dự án đã làm, thế mạnh…' : 'Về bạn / doanh nghiệp của bạn…'}
            value={form.bio}
            onChange={(e) => setForm({ ...form, bio: e.target.value })}
          />
        </div>
        {isFreelancer && (
          <>
            <div>
              <label className="label">Kỹ năng (phân tách bằng dấu phẩy)</label>
              <input
                className="input"
                placeholder="React, Spring Boot, Figma…"
                value={form.skills}
                onChange={(e) => setForm({ ...form, skills: e.target.value })}
              />
              <p className="mt-1 text-xs text-slate-400">AI dùng kỹ năng này để gợi ý job phù hợp cho bạn.</p>
            </div>
            <div>
              <label className="label">Giá theo giờ (VND)</label>
              <input className="input" type="number" min={0} value={form.hourlyRate} onChange={(e) => setForm({ ...form, hourlyRate: e.target.value })} />
            </div>
          </>
        )}
        <div>
          <label className="label">Ảnh đại diện (URL)</label>
          <input className="input" type="url" placeholder="https://…" value={form.avatarUrl} onChange={(e) => setForm({ ...form, avatarUrl: e.target.value })} />
        </div>

        {saved && <p className="rounded-xl bg-emerald-50 p-3 text-sm text-emerald-700">✅ Đã lưu hồ sơ.</p>}
        {error && <p className="text-sm text-rose-600">{error}</p>}
        <button className="btn-primary" disabled={busy}>{busy ? 'Đang lưu…' : 'Lưu thay đổi'}</button>
      </form>
    </div>
  );
}
