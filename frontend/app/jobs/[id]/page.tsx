import type { Metadata } from 'next';
import JobDetailClient from '@/components/JobDetailClient';
import type { Job } from '@/lib/types';

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';
const SITE_URL = process.env.NEXT_PUBLIC_SITE_URL || 'http://localhost:3000';

export const dynamic = 'force-dynamic';

async function fetchJob(id: string): Promise<Job | null> {
  try {
    const res = await fetch(`${API_URL}/api/jobs/${id}`, { cache: 'no-store' });
    return res.ok ? res.json() : null;
  } catch {
    return null;
  }
}

/** SEO: title/description động theo job — Google index được từng job post. */
export async function generateMetadata({ params }: { params: Promise<{ id: string }> }): Promise<Metadata> {
  const { id } = await params;
  const job = await fetchJob(id);
  if (!job) return { title: 'Công việc — VietLancer' };
  return {
    title: `${job.title} — VietLancer`,
    description: job.description.slice(0, 160),
    openGraph: {
      title: job.title,
      description: job.description.slice(0, 160),
      type: 'website',
    },
  };
}

export default async function JobDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const job = await fetchJob(id);

  // Schema.org JobPosting cho rich results trên Google
  const jsonLd = job && {
    '@context': 'https://schema.org',
    '@type': 'JobPosting',
    title: job.title,
    description: job.description,
    datePosted: job.createdAt,
    validThrough: job.deadline,
    employmentType: 'CONTRACTOR',
    hiringOrganization: { '@type': 'Organization', name: 'VietLancer' },
    jobLocationType: 'TELECOMMUTE',
    applicantLocationRequirements: { '@type': 'Country', name: 'Việt Nam' },
    url: `${SITE_URL}/jobs/${job.id}`,
    ...(job.budgetMin || job.budgetMax
      ? {
          baseSalary: {
            '@type': 'MonetaryAmount',
            currency: 'VND',
            value: {
              '@type': 'QuantitativeValue',
              minValue: job.budgetMin,
              maxValue: job.budgetMax,
              unitText: 'PROJECT',
            },
          },
        }
      : {}),
  };

  return (
    <>
      {jsonLd && (
        <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }} />
      )}
      <JobDetailClient />
    </>
  );
}
