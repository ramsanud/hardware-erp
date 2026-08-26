package com.hardware.erp.labour.service.impl;

import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.common.util.IndianCurrencyFormat;
import com.hardware.erp.labour.dto.WorkerPaymentRequest;
import com.hardware.erp.labour.dto.WorkerPaymentResponse;
import com.hardware.erp.labour.dto.WorkerWageSummaryResponse;
import com.hardware.erp.labour.entity.Worker;
import com.hardware.erp.labour.entity.WorkerPayment;
import com.hardware.erp.labour.entity.WorkerPaymentStatus;
import com.hardware.erp.labour.mapper.LabourMapper;
import com.hardware.erp.labour.repository.WorkerAttendanceRepository;
import com.hardware.erp.labour.repository.WorkerPaymentRepository;
import com.hardware.erp.labour.repository.WorkerRepository;
import com.hardware.erp.labour.service.WorkerPaymentService;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WorkerPaymentServiceImpl implements WorkerPaymentService {

    private static final String MODULE = "LABOUR";
    private static final String ENTITY = "WORKER_PAYMENT";

    private final WorkerPaymentRepository paymentRepository;
    private final WorkerAttendanceRepository attendanceRepository;
    private final WorkerRepository workerRepository;
    private final TenantRepository tenantRepository;
    private final LabourMapper mapper;
    private final ActivityLogService activityLog;

    @Override
    @Transactional
    public WorkerPaymentResponse create(WorkerPaymentRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Worker worker = workerRepository.findByIdAndTenantId(request.workerId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Worker", request.workerId()));

        WorkerPayment payment = WorkerPayment.builder()
                .tenant(tenantRepository.getReferenceById(tenantId))
                .worker(worker)
                .amountPaise(request.amountPaise())
                .paymentDate(request.paymentDate())
                .paymentMethod(request.paymentMethod())
                .notes(blankToNull(request.notes()))
                .build();

        WorkerPayment saved = paymentRepository.save(payment);
        activityLog.created(MODULE, ENTITY, saved.getId(), worker.getName(), snapshot(saved));
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkerPaymentResponse> listForWorker(Long workerId) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        workerRepository.findByIdAndTenantId(workerId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Worker", workerId));
        return paymentRepository.findByTenantIdAndWorkerIdOrderByPaymentDateDesc(tenantId, workerId)
                .stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional
    public void cancel(Long id) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        WorkerPayment payment = paymentRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Worker payment", id));

        if (payment.getStatus() == WorkerPaymentStatus.CANCELLED) {
            throw new BusinessException("This payment is already cancelled");
        }

        payment.setStatus(WorkerPaymentStatus.CANCELLED);
        paymentRepository.save(payment);
        activityLog.deleted(MODULE, ENTITY, id, payment.getWorker().getName(),
                "Payment of %s cancelled".formatted(IndianCurrencyFormat.rupees(payment.getAmountPaise())));
    }

    @Override
    @Transactional(readOnly = true)
    public WorkerWageSummaryResponse wageSummary(Long workerId, LocalDate fromDate, LocalDate toDate) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Worker worker = workerRepository.findByIdAndTenantId(workerId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Worker", workerId));

        long earned = attendanceRepository.sumWagePaiseByWorker(tenantId, workerId, fromDate, toDate);
        long paid = paymentRepository.sumAmountByWorker(tenantId, workerId, fromDate, toDate);
        long balance = earned - paid;

        return new WorkerWageSummaryResponse(
                workerId, worker.getName(), fromDate, toDate,
                earned, IndianCurrencyFormat.rupees(earned),
                paid, IndianCurrencyFormat.rupees(paid),
                balance, IndianCurrencyFormat.rupees(balance));
    }

    private Map<String, Object> snapshot(WorkerPayment payment) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("amountPaise", payment.getAmountPaise());
        values.put("paymentDate", payment.getPaymentDate());
        values.put("paymentMethod", payment.getPaymentMethod());
        return values;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
