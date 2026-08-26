package com.hardware.erp.project.mapper;

import com.hardware.erp.common.util.IndianCurrencyFormat;
import com.hardware.erp.project.dto.*;
import com.hardware.erp.project.entity.Project;
import com.hardware.erp.project.entity.ProjectExpense;
import com.hardware.erp.project.entity.ProjectMaterial;
import com.hardware.erp.project.entity.ProjectPayment;
import com.hardware.erp.project.entity.WorkType;
import org.springframework.stereotype.Component;

@Component
public class ProjectMapper {

    public WorkTypeResponse toResponse(WorkType workType) {
        return new WorkTypeResponse(workType.getId(), workType.getName(), workType.getDescription());
    }

    /**
     * Profit math lives here, fed by figures ProjectServiceImpl already
     * computed server-side (material cost, expense cost, received) - this
     * method only formats, it never recomputes or trusts a client value.
     */
    public ProjectResponse toResponse(Project project, long materialCostPaise, long expenseCostPaise,
                                      long receivedPaise, long labourCostPaise) {
        long totalCostPaise = materialCostPaise + expenseCostPaise;
        long netProfitPaise = project.getProjectValuePaise() - totalCostPaise;
        long balanceReceivablePaise = Math.max(project.getProjectValuePaise() - receivedPaise, 0);
        String marginDisplay = project.getProjectValuePaise() > 0
                ? String.format("%.2f", (netProfitPaise * 100.0) / project.getProjectValuePaise())
                : "0.00";

        return new ProjectResponse(
                project.getId(),
                project.getProjectNumber(),
                project.getProjectName(),
                project.getCustomer().getId(),
                project.getCustomer().getCustomerName(),
                project.getWorkType().getId(),
                project.getWorkType().getName(),
                project.getDescription(),
                project.getSiteAddress(),
                project.getStartDate(),
                project.getExpectedCompletionDate(),
                project.getActualCompletionDate(),
                project.getCustomerDeadline(),
                project.getStatus(),
                project.getOutcome(),
                project.isOverdue(),
                rupees(project.getProjectValuePaise()),
                rupees(materialCostPaise),
                rupees(expenseCostPaise),
                rupees(totalCostPaise),
                rupees(Math.abs(netProfitPaise)),
                netProfitPaise >= 0,
                marginDisplay,
                rupees(receivedPaise),
                rupees(balanceReceivablePaise),
                rupees(labourCostPaise),
                project.getManagerUser() != null ? project.getManagerUser().getId() : null,
                project.getManagerUser() != null ? project.getManagerUser().getFullName() : null,
                project.getNotes(),
                project.getCreatedAt());
    }

    public ProjectSummaryResponse toSummary(Project project, long materialCostPaise, long expenseCostPaise) {
        long netProfitPaise = project.getProjectValuePaise() - (materialCostPaise + expenseCostPaise);
        return new ProjectSummaryResponse(
                project.getId(),
                project.getProjectNumber(),
                project.getProjectName(),
                project.getCustomer().getCustomerName(),
                project.getWorkType().getName(),
                project.getStatus(),
                project.getOutcome(),
                project.isOverdue(),
                rupees(project.getProjectValuePaise()),
                rupees(Math.abs(netProfitPaise)),
                netProfitPaise >= 0);
    }

    public ProjectMaterialResponse toResponse(ProjectMaterial material) {
        return new ProjectMaterialResponse(
                material.getId(),
                material.getProduct().getId(),
                material.getProduct().getProductName(),
                material.getProduct().getProductCode(),
                material.getSupplier() != null ? material.getSupplier().getId() : null,
                material.getSupplier() != null ? material.getSupplier().getSupplierName() : null,
                material.getQuantityRequired(),
                material.getQuantityEstimated(),
                material.getQuantityActual(),
                material.getQuantityWastage(),
                material.getUnit(),
                rupees(material.getUnitPricePaise()),
                rupees(material.getTotalCostPaise()),
                material.getNotes(),
                material.getCreatedAt());
    }

    public ProjectExpenseResponse toResponse(ProjectExpense expense) {
        return new ProjectExpenseResponse(
                expense.getId(),
                expense.getCategory(),
                rupees(expense.getAmountPaise()),
                expense.getExpenseDate(),
                expense.getPaidTo(),
                expense.getDescription());
    }

    public ProjectPaymentResponse toResponse(ProjectPayment payment) {
        return new ProjectPaymentResponse(
                payment.getId(),
                rupees(payment.getAmountPaise()),
                payment.getPaymentMethod(),
                payment.getPaymentDate(),
                payment.getNotes());
    }

    private String rupees(Long paise) {
        return IndianCurrencyFormat.rupees(paise);
    }
}
