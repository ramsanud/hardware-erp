package com.hardware.erp.branch;

import com.fasterxml.jackson.databind.JsonNode;
import com.hardware.erp.branch.dto.BranchDtos.BranchRequest;
import com.hardware.erp.branch.dto.BranchDtos.StockTransferItemRequest;
import com.hardware.erp.branch.dto.BranchDtos.StockTransferRequest;
import com.hardware.erp.invoice.dto.InvoiceItemRequest;
import com.hardware.erp.invoice.dto.InvoiceRequest;
import com.hardware.erp.support.AbstractIntegrationTest;
import com.hardware.erp.tenant.dto.TenantRegistrationRequest;
import com.hardware.erp.tenant.entity.SubscriptionTier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CR-092 multi-branch, against real PostgreSQL. The invariant under test:
 * the per-branch breakdown always sums to the shop's own stock row, a
 * transfer moves goods without changing the total, and a sale is stamped
 * with - and deducted from - the branch of the user who made it.
 */
class BranchStockTransferIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;

    private record Shop(String bearer, Long tenantId, Long mainBranchId) {}

    private Shop registerShop(SubscriptionTier tier) throws Exception {
        String mobile = "9" + (100000000 + ThreadLocalRandom.current().nextInt(899999999));
        String body = mockMvc.perform(post("/v1/tenants/register").contentType(APPLICATION_JSON)
                        .content(json(new TenantRegistrationRequest("Branch Test Shop " + mobile.substring(5), "Owner",
                                mobile, "owner" + mobile + "@branchtest.example", "Branch@2026",
                                tier, true, "1.0", "1.0", false, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long tenantId = tree(body).path("data").path("tenantId").asLong();
        Long mainId = jdbc.queryForObject("SELECT branch_id FROM branch WHERE tenant_id = ? AND is_main", Long.class, tenantId);
        return new Shop(bearer(mobile, "Branch@2026"), tenantId, mainId);
    }

    private Long insertProduct(Long tenantId, String code, String stock) {
        Long productId = jdbc.queryForObject("""
                INSERT INTO product (tenant_id, product_code, product_name, unit, selling_price_paise,
                                     purchase_price_paise, mrp_paise, gst_rate_percent, status, created_at)
                VALUES (?, ?, 'Branch Test Item', 'PCS', 50000, 30000, 60000, 0, 'ACTIVE', now())
                RETURNING product_id""", Long.class, tenantId, code);
        // Stock only - no branch_stock row, exactly like seed data and every pre-CR-092 IT.
        jdbc.update("INSERT INTO stock (tenant_id, product_id, quantity_on_hand, created_at) VALUES (?, ?, CAST(? AS DECIMAL), now())",
                tenantId, productId, stock);
        return productId;
    }

    private Long createBranch(Shop shop, String code, String name) throws Exception {
        String body = mockMvc.perform(post("/v1/branches").header("Authorization", shop.bearer())
                        .contentType(APPLICATION_JSON)
                        .content(json(new BranchRequest(code, name, null, null, null, null, null, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return tree(body).path("data").path("id").asLong();
    }

    private BigDecimal branchQty(Long branchId, Long productId) {
        return jdbc.query("SELECT quantity_on_hand FROM branch_stock WHERE branch_id = ? AND product_id = ?",
                rs -> rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO, branchId, productId);
    }

    private BigDecimal shopQty(Long tenantId, Long productId) {
        return jdbc.queryForObject("SELECT quantity_on_hand FROM stock WHERE tenant_id = ? AND product_id = ?",
                BigDecimal.class, tenantId, productId);
    }

    @Test
    @DisplayName("every shop has exactly one MAIN branch from registration, and a Basic shop cannot add a second")
    void mainBranchExistsAndSecondIsPremium() throws Exception {
        Shop basic = registerShop(SubscriptionTier.FREE);
        mockMvc.perform(get("/v1/branches").header("Authorization", basic.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].main").value(true))
                .andExpect(jsonPath("$.data[0].branchCode").value("MAIN"));

        mockMvc.perform(post("/v1/branches").header("Authorization", basic.bearer())
                        .contentType(APPLICATION_JSON)
                        .content(json(new BranchRequest("GODOWN", "Godown", null, null, null, null, null, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FEATURE_NOT_AVAILABLE"));
    }

    @Test
    @DisplayName("a transfer moves goods between branches, refuses more than the source holds, and never changes the shop total")
    void transferMovesStockWithoutChangingTheTotal() throws Exception {
        Shop shop = registerShop(SubscriptionTier.MAX);
        Long productId = insertProduct(shop.tenantId(), "BR-1", "20");
        Long godown = createBranch(shop, "GODOWN", "Godown");

        // Creating the second branch snapshotted MAIN from the shop stock:
        // the 20 units that existed before the godown are all at MAIN.
        assertThat(branchQty(shop.mainBranchId(), productId)).isEqualByComparingTo("20");

        String body = mockMvc.perform(post("/v1/branches/transfers").header("Authorization", shop.bearer())
                        .contentType(APPLICATION_JSON)
                        .content(json(new StockTransferRequest(shop.mainBranchId(), godown,
                                List.of(new StockTransferItemRequest(productId, new BigDecimal("12"))), "Restock godown"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode transfer = tree(body).path("data");
        assertThat(transfer.path("transferNumber").asText()).startsWith("ST-");
        assertThat(transfer.path("fromBranchName").asText()).isNotBlank();

        assertThat(shopQty(shop.tenantId(), productId)).isEqualByComparingTo("20");
        assertThat(branchQty(shop.mainBranchId(), productId)).isEqualByComparingTo("8");
        assertThat(branchQty(godown, productId)).isEqualByComparingTo("12");

        Integer movements = jdbc.queryForObject(
                "SELECT COUNT(*) FROM stock_movement WHERE tenant_id = ? AND reference_type = 'STOCK_TRANSFER' AND reference_id = ?",
                Integer.class, shop.tenantId(), transfer.path("id").asLong());
        assertThat(movements).isEqualTo(2);

        // More than the source now holds is refused - and nothing moves.
        mockMvc.perform(post("/v1/branches/transfers").header("Authorization", shop.bearer())
                        .contentType(APPLICATION_JSON)
                        .content(json(new StockTransferRequest(shop.mainBranchId(), godown,
                                List.of(new StockTransferItemRequest(productId, new BigDecimal("9"))), null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_BRANCH_STOCK"));
        assertThat(branchQty(shop.mainBranchId(), productId)).isEqualByComparingTo("8");
        assertThat(branchQty(godown, productId)).isEqualByComparingTo("12");

        // A purchase received by the owner (acting branch MAIN) lands at MAIN:
        // +20 at MAIN, shop 40, godown untouched - the breakdown still sums.
        Long supplierId = jdbc.queryForObject("""
                INSERT INTO supplier (tenant_id, supplier_code, supplier_name, mobile_no, status, created_at)
                VALUES (?, 'SUP-BR1', 'Branch Supplier', '9811100002', 'ACTIVE', now()) RETURNING supplier_id""",
                Long.class, shop.tenantId());
        mockMvc.perform(post("/v1/purchases").header("Authorization", shop.bearer())
                        .contentType(APPLICATION_JSON)
                        .content(json(new com.hardware.erp.purchase.dto.PurchaseRequest(supplierId, "BILL-BR-1",
                                java.time.LocalDate.now(),
                                List.of(new com.hardware.erp.purchase.dto.PurchaseItemRequest(productId, new BigDecimal("20"), 30000L, BigDecimal.ZERO)),
                                false, null, null, null))))
                .andExpect(status().isCreated());
        assertThat(shopQty(shop.tenantId(), productId)).isEqualByComparingTo("40");
        assertThat(branchQty(shop.mainBranchId(), productId)).isEqualByComparingTo("28");
        assertThat(branchQty(godown, productId)).isEqualByComparingTo("12");
    }

    @Test
    @DisplayName("a sale is stamped with the seller's branch and deducted from it; in a single-branch shop MAIN mirrors the shop")
    void saleIsAttributedToTheActingBranch() throws Exception {
        Shop shop = registerShop(SubscriptionTier.MAX);
        Long productId = insertProduct(shop.tenantId(), "BR-2", "10");

        String body = mockMvc.perform(post("/v1/invoices").header("Authorization", shop.bearer())
                        .contentType(APPLICATION_JSON)
                        .content(json(new InvoiceRequest("Branch Customer", "9800004001", null, null, null,
                                List.of(new InvoiceItemRequest(productId, new BigDecimal("3"))), null, null, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long invoiceId = tree(body).path("data").path("id").asLong();

        Long invoiceBranch = jdbc.queryForObject("SELECT branch_id FROM invoice WHERE invoice_id = ?", Long.class, invoiceId);
        assertThat(invoiceBranch).isEqualTo(shop.mainBranchId());
        // Single-branch shop: MAIN's first row was seeded from the shop's 10, then -3.
        assertThat(shopQty(shop.tenantId(), productId)).isEqualByComparingTo("7");
        assertThat(branchQty(shop.mainBranchId(), productId)).isEqualByComparingTo("7");

        mockMvc.perform(get("/v1/branches/summary").param("from", "2020-01-01").param("to", "2030-01-01")
                        .header("Authorization", shop.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].main").value(true))
                .andExpect(jsonPath("$.data[0].invoiceCount").value(1))
                .andExpect(jsonPath("$.data[0].salesDisplay").value("1,500.00"));
    }
}
