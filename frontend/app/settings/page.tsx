'use client';

import { useEffect, useRef, useState } from 'react';
import { api, uploadFile } from '@/lib/api';
import { useAuth } from '@/lib/auth-context';
import type { User } from '@/lib/types';

export default function SettingsPage() {
  const { user, loading, refresh } = useAuth();
  const [form, setForm] = useState({ fullName: '', bio: '', skills: '', hourlyRate: '', avatarUrl: '' });
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [uploading, setUploading] = useState(false);
  const avatarInputRef = useRef<HTMLInputElement>(null);

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
        avatarUrl: form.avatarUrl,
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
          <label className="label">Ảnh đại diện</label>
          <div className="flex items-center gap-4">
            {form.avatarUrl ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img src={form.avatarUrl} alt="Avatar" className="h-16 w-16 rounded-full object-cover" />
            ) : (
              <div className="flex h-16 w-16 items-center justify-center rounded-full bg-brand-100 text-xl font-bold text-brand-700">
                {form.fullName.charAt(0) || '?'}
              </div>
            )}
            <input
              ref={avatarInputRef}
              type="file"
              className="hidden"
              accept=".png,.jpg,.jpeg,.webp"
              onChange={async (e) => {
                const file = e.target.files?.[0];
                if (!file) return;
                setUploading(true);
                setError('');
                try {
                  const uploaded = await uploadFile(file);
                  setForm((f) => ({ ...f, avatarUrl: uploaded.url }));
                } catch (err) {
                  setError(err instanceof Error ? err.message : 'Upload thất bại');
                } finally {
                  setUploading(false);
                  if (avatarInputRef.current) avatarInputRef.current.value = '';
                }
              }}
            />
            <button type="button" className="btn-secondary" disabled={uploading}
              onClick={() => avatarInputRef.current?.click()}>
              {uploading ? 'Đang tải lên…' : '📷 Tải ảnh lên'}
            </button>
            {form.avatarUrl && (
              <button type="button" className="text-sm text-rose-500 hover:underline"
                onClick={() => setForm({ ...form, avatarUrl: '' })}>
                Gỡ ảnh
              </button>
            )}
          </div>
          <p className="mt-1 text-xs text-slate-400">PNG/JPG/WebP, tối đa 5MB.</p>
        </div>

        {saved && <p className="rounded-xl bg-emerald-50 p-3 text-sm text-emerald-700">✅ Đã lưu hồ sơ.</p>}
        {error && <p className="text-sm text-rose-600">{error}</p>}
        <button className="btn-primary" disabled={busy}>{busy ? 'Đang lưu…' : 'Lưu thay đổi'}</button>
      </form>

      <KycSection />
    </div>
  );
}

/** Xác minh danh tính — huy hiệu "Đã xác minh" tăng độ tin cậy. */
function KycSection() {
  const { user, refresh } = useAuth();
  const [idNumber, setIdNumber] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  if (!user) return null;

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      await api.post('/api/users/me/kyc', { idNumber });
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Nộp hồ sơ thất bại');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="card mt-6">
      <h2 className="font-semibold">🪪 Xác minh danh tính (KYC)</h2>
      {user.kycStatus === 'VERIFIED' && (
        <p className="mt-2 rounded-xl bg-emerald-50 p-3 text-sm text-emerald-700">
          ✅ Tài khoản đã được xác minh — huy hiệu hiển thị trên hồ sơ của bạn.
        </p>
      )}
      {user.kycStatus === 'PENDING' && (
        <p className="mt-2 rounded-xl bg-amber-50 p-3 text-sm text-amber-700">
          ⏳ Hồ sơ đang chờ quản trị viên duyệt.
        </p>
      )}
      {(user.kycStatus === 'NONE' || user.kycStatus === 'REJECTED') && (
        <>
          {user.kycStatus === 'REJECTED' && (
            <p className="mt-2 rounded-xl bg-rose-50 p-3 text-sm text-rose-600">
              Hồ sơ trước bị từ chối{user.kycNote ? `: ${user.kycNote}` : ''}. Bạn có thể nộp lại.
            </p>
          )}
          <p className="mt-1 text-xs text-slate-500">
            Huy hiệu &quot;Đã xác minh&quot; giúp tăng đáng kể tỉ lệ được chọn. Nhập số CCCD/CMND (9-12 số).
          </p>
          <form onSubmit={submit} className="mt-3 flex gap-2">
            <input className="input" pattern="\d{9,12}" placeholder="Số CCCD/CMND"
              value={idNumber} onChange={(e) => setIdNumber(e.target.value)} required />
            <button className="btn-primary shrink-0" disabled={busy}>
              {busy ? 'Đang nộp…' : 'Nộp hồ sơ'}
            </button>
          </form>
          {error && <p className="mt-2 text-sm text-rose-600">{error}</p>}
        </>
      )}
    </div>
  );
}
