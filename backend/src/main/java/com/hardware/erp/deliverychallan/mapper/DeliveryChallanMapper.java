package com.hardware.erp.deliverychallan.mapper;

import com.hardware.erp.common.util.IndianCurrencyFormat;
import com.hardware.erp.deliverychallan.dto.DeliveryChallanItemResponse;
import com.hardware.erp.deliverychallan.dto.DeliveryChallanResponse;
import com.hardware.erp.deliverychallan.dto.DeliveryChallanSummaryResponse;
import com.hardware.erp.deliverychallan.entity.DeliveryChallan;
import com.hardware.erp.deliverychallan.entity.DeliveryChallanItem;
import org.springframework.stereotype.Component;

import java.util.Comparator;

@Component
public class DeliveryChallanMapper {

    public DeliveryChallanResponse toResponse(DeliveryChallan challan) {
        return new DeliveryChallanResponse(
                challan.getId(),
                challan.getDeliveryChallanNumber(),
                challan.getCustomer().getId(),
                challan.getCustomer().getCustomerName(),
                challan.getCustomer().getMobileNo(),
                challan.getChallanDate(),
                challan.getTransportMode(),
                challan.getVehicleNumber(),
                challan.getDeliveryAddress(),
                rupees(challan.getTotalValuePaise()),
                challan.getStatus(),
                challan.getRemarks(),
                challan.getSourceSalesOrderId(),
                challan.getConvertedInvoiceId(),
                challan.getItems().stream()
                        .sorted(Comparator.comparing(DeliveryChallanItem::getId))
                        .map(this::toResponse)
                        .toList(),
                challan.getCreatedAt());
    }

    public DeliveryChallanSummaryResponse toSummary(DeliveryChallan challan) {
        return new DeliveryChallanSummaryResponse(
                challan.getId(),
                challan.getDeliveryChallanNumber(),
                challan.getCustomer().getCustomerName(),
                challan.getCustomer().getMobileNo(),
                challan.getChallanDate(),
                rupees(challan.getTotalValuePaise()),
                challan.getStatus());
    }

    public DeliveryChallanItemResponse toResponse(DeliveryChallanItem item) {
        return new DeliveryChallanItemResponse(
                item.getId(),
                item.getProduct().getId(),
                item.getProductNameSnapshot(),
                item.getQuantity(),
                item.getUnit(),
                rupees(item.getUnitPricePaise()),
                rupees(item.getValuePaise()));
    }

    private String rupees(Long paise) {
        return IndianCurrencyFormat.rupees(paise);
    }
}
