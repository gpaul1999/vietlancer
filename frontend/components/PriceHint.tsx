'use client';

import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import type { PriceSuggestion } from '@/lib/types';
import { formatVnd } from '@/lib/format';

/** AI gợi ý giá theo topic từ dữ liệu bid lịch sử — ẩn khi chưa đủ mẫu. */
export default function PriceHint({ topicSlug, label }: { topicSlug: string; label: string }) {
  const [suggestion, setSuggestion] = useState<PriceSuggestion | null>(null);

  useEffect(() => {
    if (!topicSlug) return;
    api.get<PriceSuggestion>(`/api/ai/price-suggestion?topic=${encodeURIComponent(topicSlug)}`)
      .then(setSuggestion)
      .catch(() => {});
  }, [topicSlug]);

  if (!suggestion || suggestion.median === null) return null;

  return (
    <p className="rounded-xl bg-emerald-50 p-3 text-xs text-emerald-800">
      💡 <b>{label}:</b> khoảng {formatVnd(suggestion.p25)} – {formatVnd(suggestion.p75)}, phổ biến nhất{' '}
      <b>{formatVnd(suggestion.median)}</b>{' '}
      <span className="text-emerald-600">
        (AI thống kê từ {suggestion.sampleSize} {suggestion.source} cùng lĩnh vực)
      </span>
    </p>
  );
}
