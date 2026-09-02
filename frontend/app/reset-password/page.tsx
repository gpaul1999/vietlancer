'use client';

import { Suspense, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import Link from 'next/link';
import { api, setToken } from '@/lib/api';
import { useAuth } from '@/lib/auth-context';
import type { User } from '@/lib/types';

function ResetPasswordContent() {
  const params = useSearchParams();
  const router = useRouter();
  const { refresh } = useAuth();
  const token = params.get('token') || '';
  const [form, setForm] = useState({ password: '', confirm: '' });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const mismatch = form.confirm.length > 0 && form.password !== form.confirm;

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (mismatch) return;
    setBusy(true);
    setError('');
    try {
      const res = await api.post<{ token: string; user: User }>('/api/auth/reset-password', {
        token,
        newPassword: form.password,
      });
      // Đặt lại thành công → đăng nhập luôn bằng token mới
      setToken(res.token);
      await refresh();
      router.push('/dashboard');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không đặt lại được mật khẩu');
      setBusy(false);
    }
  };

  if (!token) {
    return (
      <div className="card mx-auto max-w-md text-center">
        <p className="font-semibold">Liên kết không hợp lệ</p>
        <p className="mt-2 text-sm text-slate-500">
          Thiếu mã đặt lại mật khẩu. Hãy mở đúng liên kết trong email, hoặc{' '}
          <Link href="/forgot-password" className="font-medium text-brand-600 hover:underline">
            yêu cầu liên kết mới
          </Link>
          .
        </p>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-md">
      <div className="card">
        <h1 className="text-2xl font-bold">Đặt mật khẩu mới</h1>
        <p className="mt-1 text-sm text-slate-500">
          Sau khi đổi, mọi thiết bị đang đăng nhập bằng mật khẩu cũ sẽ bị đăng xuất.
        </p>
        <form onSubmit={submit} className="mt-6 space-y-4">
          <div>
            <label className="label">Mật khẩu mới (tối thiểu 8 ký tự)</label>
            <input
              className="input"
              type="password"
              minLength={8}
              value={form.password}
              onChange={(e) => setForm({ ...form, password: e.target.value })}
              required
              autoFocus
            />
          </div>
          <div>
            <label className="label">Nhập lại mật khẩu mới</label>
            <input
              className="input"
              type="password"
              minLength={8}
              value={form.confirm}
              onChange={(e) => setForm({ ...form, confirm: e.target.value })}
              required
            />
            {mismatch && <p className="mt-1 text-sm text-rose-600">Hai mật khẩu chưa khớp.</p>}
          </div>
          {error && <p className="text-sm text-rose-600">{error}</p>}
          <button className="btn-primary w-full" disabled={busy || mismatch}>
            {busy ? 'Đang xử lý…' : 'Đặt mật khẩu mới'}
          </button>
        </form>
      </div>
    </div>
  );
}

export default function ResetPasswordPage() {
  return (
    <Suspense fallback={<p className="py-12 text-center text-slate-500">Đang tải…</p>}>
      <ResetPasswordContent />
    </Suspense>
  );
}
