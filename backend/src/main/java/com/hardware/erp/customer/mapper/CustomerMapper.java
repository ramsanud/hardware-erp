package com.hardware.erp.customer.mapper;

import com.hardware.erp.common.util.IndianCurrencyFormat;
import com.hardware.erp.customer.dto.CustomerFinancialSummaryResponse;
import com.hardware.erp.customer.dto.CustomerResponse;
import com.hardware.erp.customer.dto.CustomerSummaryResponse;
import com.hardware.erp.customer.entity.Customer;
import org.springframework.stereotype.Component;

@Component
public class CustomerMapper {

    public CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getCustomerCode(),
                customer.getCustomerName(),
                customer.getMobileNo(),
                customer.getEmail(),
                customer.getGstNo(),
                customer.getAddressLine1(),
                customer.getAddressLine2(),
                customer.getCity(),
                customer.getStateCode(),
                customer.getPincode(),
                IndianCurrencyFormat.rupees(customer.getCreditLimitPaise()),
                customer.getStatus(),
                customer.getCreatedAt());
    }

    public CustomerSummaryResponse toSummary(Customer customer) {
        return new CustomerSummaryResponse(
                customer.getId(),
                customer.getCustomerCode(),
                customer.getCustomerName(),
                customer.getMobileNo(),
                customer.getCity(),
                customer.getStatus());
    }

    public CustomerFinancialSummaryResponse toFinancialSummary(Object[] invoiceAggregate, long quotationCount) {
        long invoiceCount = ((Number) invoiceAggregate[0]).longValue();
        long totalInvoiced = ((Number) invoiceAggregate[1]).longValue();
        long totalPaid = ((Number) invoiceAggregate[2]).longValue();
        long outstanding = ((Number) invoiceAggregate[3]).longValue();
        return new CustomerFinancialSummaryResponse(
                invoiceCount, quotationCount,
                IndianCurrencyFormat.rupees(totalInvoiced),
                IndianCurrencyFormat.rupees(totalPaid),
                IndianCurrencyFormat.rupees(outstanding));
    }
}
