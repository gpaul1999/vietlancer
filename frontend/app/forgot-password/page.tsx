'use client';

import { useState } from 'react';
import Link from 'next/link';
import { api } from '@/lib/api';

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      const res = await api.post<{ message: string }>('/api/auth/forgot-password', { email });
      setSent(res.message);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không gửi được yêu cầu');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="mx-auto max-w-md">
      <div className="card">
        <h1 className="text-2xl font-bold">Quên mật khẩu</h1>

        {sent ? (
          <>
            <p className="mt-4 rounded-xl bg-emerald-50 p-4 text-sm text-emerald-700">📧 {sent}</p>
            <p className="mt-4 text-center text-sm text-slate-500">
              <Link href="/login" className="font-medium text-brand-600 hover:underline">
                ← Quay lại đăng nhập
              </Link>
            </p>
          </>
        ) : (
          <>
            <p className="mt-1 text-sm text-slate-500">
              Nhập email đã đăng ký, chúng tôi sẽ gửi liên kết đặt lại mật khẩu (hiệu lực 1 giờ).
            </p>
            <form onSubmit={submit} className="mt-6 space-y-4">
              <div>
                <label className="label">Email</label>
                <input
                  className="input"
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                  autoFocus
                />
              </div>
              {error && <p className="text-sm text-rose-600">{error}</p>}
              <button className="btn-primary w-full" disabled={busy}>
                {busy ? 'Đang gửi…' : 'Gửi liên kết đặt lại'}
              </button>
            </form>
            <p className="mt-4 text-center text-sm text-slate-500">
              Nhớ ra mật khẩu rồi?{' '}
              <Link href="/login" className="font-medium text-brand-600 hover:underline">
                Đăng nhập
              </Link>
            </p>
          </>
        )}
      </div>
    </div>
  );
}
