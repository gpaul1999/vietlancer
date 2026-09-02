/** @type {import('next').NextConfig} */
const nextConfig = {
  // Đóng gói gọn cho Docker: .next/standalone chỉ chứa file thật sự cần để chạy
  output: 'standalone',
  env: {
    NEXT_PUBLIC_API_URL: process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080',
    NEXT_PUBLIC_SITE_URL: process.env.NEXT_PUBLIC_SITE_URL || 'http://localhost:3000',
  },
};

export default nextConfig;
