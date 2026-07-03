package com.vietlancer.dispute;

import com.vietlancer.common.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Chốt chặn dùng chung: khi job có khiếu nại đang mở, mọi thao tác tiền/trạng thái
 * (complete, cancel, fund/release milestone) đều bị đóng băng cho đến khi admin phân xử.
 */
@Component
@RequiredArgsConstructor
public class DisputeGuard {

    private final DisputeRepository disputeRepository;

    public void requireNoOpenDispute(Long jobId) {
        if (disputeRepository.existsByJobIdAndStatus(jobId, Dispute.Status.OPEN)) {
            throw ApiException.conflict(
                    "Job đang có khiếu nại chờ phân xử — mọi thao tác bị tạm khóa");
        }
    }
}
