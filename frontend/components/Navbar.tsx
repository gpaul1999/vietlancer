'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useAuth } from '@/lib/auth-context';
import NotificationBell from './NotificationBell';

const navLinks = [
  { href: '/jobs', label: 'Tìm việc' },
  { href: '/freelancers', label: 'Freelancer' },
  { href: '/post-job', label: 'Đăng việc' },
  { href: '/pricing', label: 'Premium' },
];

export default function Navbar() {
  const { user, logout, loading } = useAuth();
  const pathname = usePathname();

  return (
    <header className="sticky top-0 z-40 border-b border-slate-200 bg-white/90 backdrop-blur">
      <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-3">
        <Link href="/" className="text-xl font-extrabold tracking-tight text-brand-600">
          Viet<span className="text-slate-900">Lancer</span>
        </Link>

        <nav className="hidden items-center gap-1 md:flex">
          {navLinks.map((l) => (
            <Link
              key={l.href}
              href={l.href}
              className={`rounded-lg px-3 py-2 text-sm font-medium transition ${
                pathname === l.href ? 'bg-brand-50 text-brand-700' : 'text-slate-600 hover:bg-slate-100'
              }`}
            >
              {l.label}
            </Link>
          ))}
        </nav>

        <div className="flex items-center gap-2">
          {loading ? null : user ? (
            <>
              {user.role === 'ADMIN' && (
                <Link href="/admin/disputes" className="rounded-lg px-3 py-2 text-sm font-medium text-rose-600 hover:bg-rose-50">
                  ⚖️ Phân xử
                </Link>
              )}
              <NotificationBell />
              <Link href="/messages" className="rounded-lg px-3 py-2 text-sm font-medium text-slate-600 hover:bg-slate-100">
                Tin nhắn
              </Link>
              <Link href="/wallet" className="rounded-lg px-3 py-2 text-sm font-medium text-slate-600 hover:bg-slate-100">
                Ví
              </Link>
              <Link href="/dashboard" className="rounded-lg px-3 py-2 text-sm font-medium text-slate-600 hover:bg-slate-100">
                {user.fullName.split(' ').slice(-1)[0]}
              </Link>
              <button onClick={logout} className="rounded-lg px-3 py-2 text-sm text-slate-500 hover:bg-slate-100">
                Thoát
              </button>
            </>
          ) : (
            <>
              <Link href="/login" className="rounded-lg px-3 py-2 text-sm font-medium text-slate-600 hover:bg-slate-100">
                Đăng nhập
              </Link>
              <Link href="/register" className="btn-primary !px-4 !py-2 text-sm">
                Đăng ký
              </Link>
            </>
          )}
        </div>
      </div>
    </header>
  );
}
