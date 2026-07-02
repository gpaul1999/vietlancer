'use client';

import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { useAuth } from '@/lib/auth-context';
import type { SubscriptionStatus } from '@/lib/types';
import { formatVnd, formatDate } from '@/lib/format';

const plans = [
  {
    role: 'CLIENT',
    name: 'Client Premium',
    price: 99000,
    perks: [
      '💬 Nhắn tin với freelancer Premium trước khi chọn bid',
      '⭐ Job được ưu tiên hiển thị đầu danh sách',
      '🏅 Huy hiệu Premium tăng độ tin cậy',
    ],
  },
  {
    role: 'FREELANCER',
    name: 'Freelancer Premium',
    price: 79000,
    perks: [
      '💬 Nhắn tin với client Premium trước khi được chọn',
      '♾️ Chào giá không giới hạn (miễn phí: 15 lượt/tháng)',
      '🏅 Huy hiệu Premium nổi bật trong danh sách bid',
    ],
  },
];

export default function PricingPage() {
  const { user } = useAuth();
  const [sub, setSub] = useState<SubscriptionStatus | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    if (user) api.get<SubscriptionStatus>('/api/subscriptions/me').then(setSub).catch(() => {});
  }, [user]);

  const subscribe = async () => {
    setBusy(true);
    setError('');
    setMessage('');
    try {
      const res = await api.post<SubscriptionStatus>('/api/subscriptions/subscribe');
      setSub(res);
      setMessage(`Đã kích hoạt ${res.plan} đến ${formatDate(res.expiresAt)}. Cảm ơn bạn!`);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không mua được gói');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="mx-auto max-w-4xl">
      <div className="text-center">
        <h1 className="text-3xl font-bold">Nâng cấp Premium</h1>
        <p className="mt-2 text-slate-600">
          Mỗi bên có một gói duy nhất, đơn giản và minh bạch. Thanh toán bằng số dư ví, chu kỳ 30 ngày.
        </p>
      </div>

      {message && <p className="mt-6 rounded-xl bg-emerald-50 p-3 text-center text-sm text-emerald-700">{message}</p>}
      {error && <p className="mt-6 rounded-xl bg-rose-50 p-3 text-center text-sm text-rose-600">{error}</p>}

      <div className="mt-10 grid gap-6 md:grid-cols-2">
        {plans.map((plan) => {
          const isMine = user?.role === plan.role;
          return (
            <div key={plan.role} className={`card ${isMine ? 'border-brand-500 ring-2 ring-brand-100' : ''}`}>
              <h2 className="text-xl font-bold">{plan.name}</h2>
              <div className="mt-2 text-3xl font-extrabold text-brand-700">
                {formatVnd(plan.price)}<span className="text-base font-medium text-slate-500">/tháng</span>
              </div>
              <ul className="mt-4 space-y-2 text-sm text-slate-700">
                {plan.perks.map((p) => <li key={p}>{p}</li>)}
              </ul>
              {isMine && (
                sub?.premium ? (
                  <div className="mt-6 rounded-xl bg-emerald-50 p-3 text-center text-sm font-semibold text-emerald-700">
                    ✅ Đang hoạt động đến {formatDate(sub.expiresAt)}
                    <button className="btn-secondary mt-3 w-full" disabled={busy} onClick={subscribe}>
                      Gia hạn thêm 30 ngày
                    </button>
                  </div>
                ) : (
                  <button className="btn-primary mt-6 w-full" disabled={busy || !user} onClick={subscribe}>
                    {busy ? 'Đang xử lý…' : 'Mua bằng số dư ví'}
                  </button>
                )
              )}
              {!user && (
                <p className="mt-6 text-center text-sm text-slate-500">Đăng nhập để mua gói này.</p>
              )}
              {user && !isMine && (
                <p className="mt-6 text-center text-sm text-slate-400">Dành cho tài khoản {plan.role === 'CLIENT' ? 'người thuê' : 'freelancer'}.</p>
              )}
            </div>
          );
        })}
      </div>

      <div className="card mt-8 bg-slate-50">
        <h3 className="font-semibold">Quy tắc nhắn tin của VietLancer</h3>
        <p className="mt-2 text-sm text-slate-600">
          Để tránh spam và bảo vệ cả hai bên, client và freelancer chỉ nhắn tin trực tiếp được khi:
          (1) freelancer đã được chọn qua bid, <b>hoặc</b> (2) cả hai bên đều đang có gói Premium.
        </p>
      </div>
    </div>
  );
}
