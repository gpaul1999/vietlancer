'use client';

import { useCallback, useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { useAuth } from '@/lib/auth-context';
import type { Wallet, WalletTransaction } from '@/lib/types';
import { formatVnd, formatDateTime } from '@/lib/format';

const typeLabel: Record<string, string> = {
  DEPOSIT: 'Nạp tiền',
  ESCROW_HOLD: 'Giữ escrow',
  ESCROW_REFUND: 'Hoàn escrow',
  PAYOUT: 'Nhận thanh toán',
  PLATFORM_FEE: 'Phí nền tảng',
  SUBSCRIPTION: 'Gói Premium',
};

export default function WalletPage() {
  const { user } = useAuth();
  const [wallet, setWallet] = useState<Wallet | null>(null);
  const [transactions, setTransactions] = useState<WalletTransaction[]>([]);
  const [amount, setAmount] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setWallet(await api.get<Wallet>('/api/wallet'));
    setTransactions(await api.get<WalletTransaction[]>('/api/wallet/transactions'));
  }, []);

  useEffect(() => {
    if (user) load().catch(() => {});
  }, [user, load]);

  if (!user) return <p className="py-12 text-center text-slate-500">Hãy đăng nhập để xem ví.</p>;

  const deposit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      await api.post('/api/wallet/deposit', { amount: Number(amount) });
      setAmount('');
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Nạp tiền thất bại');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <h1 className="text-2xl font-bold">Ví của tôi</h1>

      <div className="grid gap-4 md:grid-cols-2">
        <div className="card">
          <div className="text-sm text-slate-500">Số dư khả dụng</div>
          <div className="mt-1 text-3xl font-bold text-brand-700">{formatVnd(wallet?.balance)}</div>
        </div>
        <div className="card">
          <div className="text-sm text-slate-500">Đang giữ trong escrow</div>
          <div className="mt-1 text-3xl font-bold">{formatVnd(wallet?.escrowBalance)}</div>
        </div>
      </div>

      <form onSubmit={deposit} className="card">
        <h2 className="font-semibold">Nạp tiền (mô phỏng)</h2>
        <p className="mt-1 text-xs text-slate-500">
          MVP mô phỏng nạp tiền. Bản chính thức sẽ tích hợp VNPay / MoMo / thẻ ngân hàng.
        </p>
        <div className="mt-3 flex gap-2">
          <input
            className="input"
            type="number"
            min={10000}
            step={10000}
            placeholder="Số tiền (VND)"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            required
          />
          <button className="btn-primary shrink-0" disabled={busy}>Nạp tiền</button>
        </div>
        {error && <p className="mt-2 text-sm text-rose-600">{error}</p>}
      </form>

      <div className="card !p-0">
        <h2 className="px-6 pt-5 font-semibold">Lịch sử giao dịch</h2>
        <div className="mt-2 divide-y divide-slate-100">
          {transactions.length === 0 && <p className="px-6 py-6 text-sm text-slate-500">Chưa có giao dịch.</p>}
          {transactions.map((t) => (
            <div key={t.id} className="flex items-center justify-between px-6 py-3">
              <div>
                <div className="text-sm font-medium">{typeLabel[t.type] || t.type}</div>
                <div className="text-xs text-slate-500">{t.note} · {formatDateTime(t.createdAt)}</div>
              </div>
              <div className={`font-semibold ${t.amount >= 0 ? 'text-emerald-600' : 'text-rose-600'}`}>
                {t.amount >= 0 ? '+' : ''}{formatVnd(t.amount)}
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
