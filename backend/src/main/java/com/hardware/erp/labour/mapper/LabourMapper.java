package com.hardware.erp.labour.mapper;

import com.hardware.erp.common.util.IndianCurrencyFormat;
import com.hardware.erp.labour.dto.WorkerAttendanceResponse;
import com.hardware.erp.labour.dto.WorkerPaymentResponse;
import com.hardware.erp.labour.dto.WorkerResponse;
import com.hardware.erp.labour.entity.Worker;
import com.hardware.erp.labour.entity.WorkerAttendance;
import com.hardware.erp.labour.entity.WorkerPayment;
import com.hardware.erp.project.entity.Project;
import org.springframework.stereotype.Component;

@Component
public class LabourMapper {

    public WorkerResponse toResponse(Worker worker) {
        return new WorkerResponse(
                worker.getId(),
                worker.getName(),
                worker.getMobileNo(),
                worker.getRoleTitle(),
                worker.getDailyRatePaise(),
                IndianCurrencyFormat.rupees(worker.getDailyRatePaise()),
                worker.getStatus(),
                worker.getCreatedAt());
    }

    public WorkerAttendanceResponse toResponse(WorkerAttendance attendance) {
        long wagePaise = attendance.getStatus().wagePaiseFor(attendance.getWorker().getDailyRatePaise());
        Project project = attendance.getProject();
        return new WorkerAttendanceResponse(
                attendance.getId(),
                attendance.getWorker().getId(),
                attendance.getWorker().getName(),
                attendance.getAttendanceDate(),
                attendance.getStatus(),
                project == null ? null : project.getId(),
                project == null ? null : project.getProjectName(),
                attendance.getNotes(),
                wagePaise,
                IndianCurrencyFormat.rupees(wagePaise));
    }

    public WorkerPaymentResponse toResponse(WorkerPayment payment) {
        return new WorkerPaymentResponse(
                payment.getId(),
                payment.getWorker().getId(),
                payment.getWorker().getName(),
                payment.getAmountPaise(),
                IndianCurrencyFormat.rupees(payment.getAmountPaise()),
                payment.getPaymentDate(),
                payment.getPaymentMethod(),
                payment.getNotes(),
                payment.getStatus(),
                payment.getCreatedAt());
    }
}
