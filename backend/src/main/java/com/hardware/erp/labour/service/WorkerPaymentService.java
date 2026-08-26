package com.hardware.erp.labour.service;

import com.hardware.erp.labour.dto.WorkerPaymentRequest;
import com.hardware.erp.labour.dto.WorkerPaymentResponse;
import com.hardware.erp.labour.dto.WorkerWageSummaryResponse;

import java.time.LocalDate;
import java.util.List;

public interface WorkerPaymentService {

    WorkerPaymentResponse create(WorkerPaymentRequest request);

    List<WorkerPaymentResponse> listForWorker(Long workerId);

    /** Soft cancel - the row stays in history, marked CANCELLED, and stops counting towards the worker's paid total. */
    void cancel(Long id);

    /** Wages earned (live-computed from attendance x current daily rate) vs paid, for one worker over a date range. */
    WorkerWageSummaryResponse wageSummary(Long workerId, LocalDate fromDate, LocalDate toDate);
}
