'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, ApiError } from '@/lib/api';
import type { Dispute } from '@/lib/types';
import { formatVnd, formatDateTime } from '@/lib/format';

export default function DisputePanel({
  jobId,
  isParticipant,
  jobInProgress,
  currentUserId,
  onChanged,
}: {
  jobId: number;
  isParticipant: boolean;
  jobInProgress: boolean;
  currentUserId?: number;
  onChanged: (hasOpenDispute: boolean) => void;
}) {
  const [disputes, setDisputes] = useState<Dispute[]>([]);
  const [showForm, setShowForm] = useState(false);
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    if (!isParticipant) return;
    try {
      const list = await api.get<Dispute[]>(`/api/jobs/${jobId}/disputes`);
      setDisputes(list);
      onChanged(list.some((d) => d.status === 'OPEN'));
    } catch {
      /* ignore */
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [jobId, isParticipant]);

  useEffect(() => {
    load();
  }, [load]);

  if (!isParticipant) return null;

  const open = disputes.find((d) => d.status === 'OPEN');

  const submit = async () => {
    setBusy(true);
    setError('');
    try {
      await api.post(`/api/jobs/${jobId}/disputes`, { reason });
      setReason('');
      setShowForm(false);
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Không mở được khiếu nại');
    } finally {
      setBusy(false);
    }
  };

  const withdraw = async (id: number) => {
    setBusy(true);
    setError('');
    try {
      await api.post(`/api/disputes/${id}/withdraw`);
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Không rút được khiếu nại');
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      {open && (
        <div className="rounded-2xl border border-amber-300 bg-amber-50 p-5">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <p className="font-semibold text-amber-800">⚠️ Job đang bị tạm khóa do khiếu nại</p>
            {open.raisedById === currentUserId && (
              <button className="btn-secondary !py-1.5 text-xs" disabled={busy} onClick={() => withdraw(open.id)}>
                Rút khiếu nại
              </button>
            )}
          </div>
          <p className="mt-2 text-sm text-amber-800">
            <b>{open.raisedByName}</b> mở lúc {formatDateTime(open.createdAt)} — escrow bị đóng băng:{' '}
            <b>{formatVnd(open.heldAmount)}</b>
          </p>
          <p className="mt-1 text-sm text-amber-700">Lý do: {open.reason}</p>
          <p className="mt-2 text-xs text-amber-600">
            Đội ngũ VietLancer sẽ xem xét và phân xử. Mọi thao tác hoàn thành/hủy/giải ngân tạm dừng.
          </p>
        </div>
      )}

      {disputes.filter((d) => d.status === 'RESOLVED').map((d) => (
        <div key={d.id} className="rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600">
          ⚖️ Khiếu nại đã phân xử ({formatDateTime(d.resolvedAt!)}): freelancer nhận{' '}
          <b>{formatVnd(d.amountToFreelancer)}</b> / escrow {formatVnd(d.heldAmount)}.
          {d.resolutionNote && <> Ghi chú: {d.resolutionNote}</>}
        </div>
      ))}

      {!open && jobInProgress && (
        <div>
          {!showForm ? (
            <button className="text-sm font-medium text-rose-500 hover:underline" onClick={() => setShowForm(true)}>
              ⚠️ Gặp vấn đề? Mở khiếu nại
            </button>
          ) : (
            <div className="card border-rose-200">
              <h3 className="font-semibold text-rose-600">Mở khiếu nại</h3>
              <p className="mt-1 text-xs text-slate-500">
                Job sẽ bị tạm khóa (không hoàn thành/hủy/giải ngân được) cho đến khi admin phân xử.
                Hãy trao đổi qua chat trước khi dùng đến bước này.
              </p>
              <textarea className="input mt-3 min-h-[90px]" placeholder="Mô tả vấn đề càng chi tiết càng tốt…"
                value={reason} onChange={(e) => setReason(e.target.value)} />
              <div className="mt-3 flex gap-2">
                <button className="btn-primary !bg-rose-600 hover:!bg-rose-700" disabled={busy || reason.length < 10} onClick={submit}>
                  Gửi khiếu nại
                </button>
                <button className="btn-secondary" onClick={() => setShowForm(false)}>Bỏ qua</button>
              </div>
            </div>
          )}
        </div>
      )}
      {error && <p className="text-sm text-rose-600">{error}</p>}
    </>
  );
}
