export const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

/** URL WebSocket STOMP tương ứng với API. */
export const WS_URL = API_URL.replace(/^http/, 'ws') + '/ws';

export function getToken(): string | null {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem('vl_token');
}

export function setToken(token: string | null) {
  if (typeof window === 'undefined') return;
  if (token) localStorage.setItem('vl_token', token);
  else localStorage.removeItem('vl_token');
}

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string>),
  };
  const token = getToken();
  if (token) headers['Authorization'] = `Bearer ${token}`;

  const res = await fetch(`${API_URL}${path}`, { ...options, headers });
  if (!res.ok) {
    let message = `Lỗi ${res.status}`;
    try {
      const body = await res.json();
      if (body?.message) message = body.message;
    } catch {
      /* ignore */
    }
    throw new ApiError(res.status, message);
  }
  if (res.status === 204) return undefined as T;
  return res.json();
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) }),
  put: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'PUT', body: JSON.stringify(body) }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
};

export interface UploadedFile {
  url: string;
  originalName: string;
  size: number;
}

/** Upload file multipart (tối đa 5MB, whitelist extension ở backend). */
export async function uploadFile(file: File): Promise<UploadedFile> {
  const fd = new FormData();
  fd.append('file', file);
  const headers: Record<string, string> = {};
  const token = getToken();
  if (token) headers['Authorization'] = `Bearer ${token}`;
  const res = await fetch(`${API_URL}/api/files`, { method: 'POST', body: fd, headers });
  if (!res.ok) {
    let message = `Lỗi ${res.status}`;
    try {
      const body = await res.json();
      if (body?.message) message = body.message;
    } catch {
      /* ignore */
    }
    throw new ApiError(res.status, message);
  }
  return res.json();
}
