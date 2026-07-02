import type { Metadata } from 'next';
import './globals.css';
import { AuthProvider } from '@/lib/auth-context';
import Navbar from '@/components/Navbar';

export const metadata: Metadata = {
  title: 'VietLancer — Nền tảng freelance thông minh',
  description:
    'Thuê freelancer Việt Nam dễ dàng: chỉ cần mô tả công việc, AI tự phân loại và kết nối đúng chuyên gia.',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="vi">
      <body>
        <AuthProvider>
          <Navbar />
          <main className="mx-auto max-w-6xl px-4 py-8">{children}</main>
          <footer className="mt-16 border-t border-slate-200 bg-white py-8 text-center text-sm text-slate-500">
            © {new Date().getFullYear()} VietLancer — Nền tảng freelance với AI phân loại công việc.
          </footer>
        </AuthProvider>
      </body>
    </html>
  );
}
