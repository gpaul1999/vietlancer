'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import type { Topic } from '@/lib/types';

export default function HomePage() {
  const [topics, setTopics] = useState<Topic[]>([]);

  useEffect(() => {
    api.get<Topic[]>('/api/topics').then(setTopics).catch(() => {});
  }, []);

  return (
    <div className="space-y-16">
      {/* Hero */}
      <section className="rounded-3xl bg-gradient-to-br from-brand-600 to-brand-700 px-8 py-16 text-white">
        <div className="max-w-2xl">
          <p className="mb-3 inline-block rounded-full bg-white/15 px-3 py-1 text-xs font-semibold uppercase tracking-wide">
            🤖 AI phân loại công việc tự động
          </p>
          <h1 className="text-4xl font-extrabold leading-tight md:text-5xl">
            Chỉ cần mô tả công việc.
            <br />
            AI lo phần còn lại.
          </h1>
          <p className="mt-4 text-lg text-brand-100">
            Không cần chọn danh mục thủ công — VietLancer đọc mô tả của bạn, tự gán đúng lĩnh vực
            (một việc có thể thuộc nhiều lĩnh vực) và đưa việc đến đúng freelancer.
          </p>
          <div className="mt-8 flex flex-wrap gap-3">
            <Link href="/post-job" className="rounded-xl bg-white px-6 py-3 font-semibold text-brand-700 hover:bg-brand-50">
              Đăng việc miễn phí
            </Link>
            <Link href="/jobs" className="rounded-xl border border-white/40 px-6 py-3 font-semibold text-white hover:bg-white/10">
              Tìm việc freelance
            </Link>
          </div>
        </div>
      </section>

      {/* How it works */}
      <section>
        <h2 className="mb-6 text-2xl font-bold">Cách hoạt động</h2>
        <div className="grid gap-4 md:grid-cols-3">
          {[
            ['📝', 'Mô tả công việc', 'Viết mô tả bằng tiếng Việt tự nhiên. AI phân tích và gán topic phù hợp — có thể nhiều topic cùng lúc.'],
            ['💬', 'Nhận chào giá', 'Freelancer đúng chuyên môn thấy việc của bạn ngay và gửi chào giá kèm kế hoạch thực hiện.'],
            ['🔒', 'Escrow an toàn', 'Tiền được giữ trong escrow khi bắt đầu và chỉ giải ngân khi bạn xác nhận hoàn thành.'],
          ].map(([icon, title, desc]) => (
            <div key={title} className="card">
              <div className="text-3xl">{icon}</div>
              <h3 className="mt-3 font-semibold">{title}</h3>
              <p className="mt-1 text-sm text-slate-600">{desc}</p>
            </div>
          ))}
        </div>
      </section>

      {/* Topics */}
      <section>
        <h2 className="mb-6 text-2xl font-bold">Khám phá theo lĩnh vực</h2>
        <div className="grid grid-cols-2 gap-3 md:grid-cols-4 lg:grid-cols-6">
          {topics.map((t) => (
            <Link
              key={t.slug}
              href={`/jobs?topic=${t.slug}`}
              className="card !p-4 text-center transition hover:border-brand-500 hover:shadow-md"
            >
              <div className="text-2xl">{t.icon}</div>
              <div className="mt-1 text-sm font-medium">{t.name}</div>
            </Link>
          ))}
        </div>
      </section>
    </div>
  );
}
