'use client';

import { Suspense, useCallback, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { api } from '@/lib/api';
import { useAuth } from '@/lib/auth-context';

function VerifyEmailContent() {
  const params = useSearchParams();
  const { refresh } = useAuth();
  const token = params.get('token') || '';
  const [state, setState] = useState<'verifying' | 'ok' | 'error'>('verifying');
  const [message, setMessage] = useState('');
  const started = useRef(false);

  const verify = useCallback(async () => {
    if (!token) {
      setState('error');
      setMessage('Liên kết thiếu mã xác thực.');
      return;
    }
    try {
      const res = await api.post<{ message: string }>('/api/auth/verify-email', { token });
      setMessage(res.message);
      setState('ok');
      await refresh(); // cập nhật trạng thái emailVerified nếu đang đăng nhập
    } catch (err) {
      setMessage(err instanceof Error ? err.message : 'Xác thực thất bại');
      setState('error');
    }
  }, [token, refresh]);

  useEffect(() => {
    // Token dùng một lần → chỉ gọi đúng một lần dù React StrictMode render đôi
    if (started.current) return;
    started.current = true;
    verify();
  }, [verify]);

  return (
    <div className="card mx-auto max-w-md text-center">
      {state === 'verifying' && <p className="text-slate-500">Đang xác thực email…</p>}

      {state === 'ok' && (
        <>
          <div className="text-4xl">✅</div>
          <h1 className="mt-3 text-xl font-bold">Xác thực thành công</h1>
          <p className="mt-2 text-sm text-slate-600">{message}</p>
          <Link href="/dashboard" className="btn-primary mt-6 inline-flex">
            Vào bảng điều khiển
          </Link>
        </>
      )}

      {state === 'error' && (
        <>
          <div className="text-4xl">⚠️</div>
          <h1 className="mt-3 text-xl font-bold">Không xác thực được</h1>
          <p className="mt-2 text-sm text-slate-600">{message}</p>
          <p className="mt-4 text-sm text-slate-500">
            Đăng nhập rồi bấm <b>&quot;Gửi lại email xác thực&quot;</b> trong hồ sơ để nhận liên kết mới.
          </p>
          <Link href="/settings" className="btn-secondary mt-6 inline-flex">
            Tới hồ sơ
          </Link>
        </>
      )}
    </div>
  );
}

export default function VerifyEmailPage() {
  return (
    <Suspense fallback={<p className="py-12 text-center text-slate-500">Đang tải…</p>}>
      <VerifyEmailContent />
    </Suspense>
  );
}
