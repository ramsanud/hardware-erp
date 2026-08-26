package com.hardware.erp.labour.service;

import com.hardware.erp.labour.dto.AttendanceMarkRequest;
import com.hardware.erp.labour.dto.WorkerAttendanceResponse;

import java.time.LocalDate;
import java.util.List;

public interface AttendanceService {

    /** Marks (creates or corrects) attendance for one or more workers on one day, in a single transaction. */
    List<WorkerAttendanceResponse> mark(AttendanceMarkRequest request);

    List<WorkerAttendanceResponse> forDate(LocalDate date);

    List<WorkerAttendanceResponse> historyForWorker(Long workerId, LocalDate fromDate, LocalDate toDate);
}
