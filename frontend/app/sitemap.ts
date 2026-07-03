import type { MetadataRoute } from 'next';
import type { JobSearchResult } from '@/lib/types';

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';
const SITE_URL = process.env.NEXT_PUBLIC_SITE_URL || 'http://localhost:3000';

export const dynamic = 'force-dynamic';

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const routes: MetadataRoute.Sitemap = ['', '/jobs', '/freelancers', '/pricing'].map((path) => ({
    url: `${SITE_URL}${path}`,
    changeFrequency: 'daily',
    priority: path === '' ? 1 : 0.8,
  }));

  try {
    const res = await fetch(`${API_URL}/api/jobs?size=50`, { cache: 'no-store' });
    if (res.ok) {
      const data = (await res.json()) as JobSearchResult;
      routes.push(
        ...data.jobs.map((job) => ({
          url: `${SITE_URL}/jobs/${job.id}`,
          lastModified: new Date(job.createdAt),
          changeFrequency: 'weekly' as const,
          priority: 0.6,
        })),
      );
    }
  } catch {
    // Backend chưa chạy → sitemap chỉ gồm trang tĩnh
  }
  return routes;
}
