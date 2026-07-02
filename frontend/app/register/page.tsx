'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { useAuth } from '@/lib/auth-context';

export default function RegisterPage() {
  const { register } = useAuth();
  const router = useRouter();
  const [form, setForm] = useState({ email: '', password: '', fullName: '', role: 'CLIENT' });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      await register(form);
      router.push('/dashboard');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Đăng ký thất bại');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="mx-auto max-w-md">
      <div className="card">
        <h1 className="text-2xl font-bold">Tạo tài khoản</h1>
        <form onSubmit={submit} className="mt-6 space-y-4">
          <div>
            <label className="label">Bạn là</label>
            <div className="grid grid-cols-2 gap-2">
              {[
                ['CLIENT', '🧑‍💼 Người thuê', 'Tôi cần thuê freelancer'],
                ['FREELANCER', '🧑‍💻 Freelancer', 'Tôi muốn nhận việc'],
              ].map(([value, title, desc]) => (
                <button
                  type="button"
                  key={value}
                  onClick={() => setForm({ ...form, role: value })}
                  className={`rounded-xl border p-3 text-left transition ${
                    form.role === value ? 'border-brand-500 bg-brand-50' : 'border-slate-300 hover:bg-slate-50'
                  }`}
                >
                  <div className="font-semibold">{title}</div>
                  <div className="text-xs text-slate-500">{desc}</div>
                </button>
              ))}
            </div>
          </div>
          <div>
            <label className="label">Họ và tên</label>
            <input className="input" value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} required />
          </div>
          <div>
            <label className="label">Email</label>
            <input className="input" type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} required />
          </div>
          <div>
            <label className="label">Mật khẩu (tối thiểu 8 ký tự)</label>
            <input className="input" type="password" minLength={8} value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} required />
          </div>
          {error && <p className="text-sm text-rose-600">{error}</p>}
          <button className="btn-primary w-full" disabled={busy}>
            {busy ? 'Đang tạo…' : 'Đăng ký'}
          </button>
        </form>
        <p className="mt-4 text-center text-sm text-slate-500">
          Đã có tài khoản?{' '}
          <Link href="/login" className="font-medium text-brand-600 hover:underline">
            Đăng nhập
          </Link>
        </p>
      </div>
    </div>
  );
}
