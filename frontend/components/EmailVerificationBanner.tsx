'use client';

import { useState } from 'react';
import { api } from '@/lib/api';
import { useAuth } from '@/lib/auth-context';

/** Nhắc người dùng xác thực email + cho gửi lại liên kết. Tự ẩn khi đã xác thực. */
export default function EmailVerificationBanner() {
  const { user } = useAuth();
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  if (!user || user.emailVerified !== false) return null;

  const resend = async () => {
    setBusy(true);
    setError('');
    try {
      const res = await api.post<{ message: string }>('/api/auth/resend-verification');
      setMessage(res.message);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không gửi lại được');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="rounded-2xl border border-amber-300 bg-amber-50 p-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="font-semibold text-amber-800">📧 Email chưa được xác thực</p>
          <p className="mt-1 text-sm text-amber-700">
            Kiểm tra hộp thư <b>{user.email}</b> và nhấn liên kết xác thực để kích hoạt đầy đủ tài khoản.
          </p>
        </div>
        {!message && (
          <button className="btn-secondary shrink-0 !py-1.5 text-sm" disabled={busy} onClick={resend}>
            {busy ? 'Đang gửi…' : 'Gửi lại email'}
          </button>
        )}
      </div>
      {message && <p className="mt-2 text-sm font-medium text-emerald-700">✓ {message}</p>}
      {error && <p className="mt-2 text-sm text-rose-600">{error}</p>}
    </div>
  );
}
