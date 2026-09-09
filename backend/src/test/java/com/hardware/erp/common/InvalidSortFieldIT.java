package com.hardware.erp.common;

import com.hardware.erp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression test for BUG-BE-004 - an unknown ?sort= field returned 500.
 *
 * The list endpoints that bind a Spring {@code Pageable} directly let the
 * client's sort property through to the JPQL order-by clause. A property the
 * entity does not have blew up when the query was built (Hibernate's
 * UnknownPathException, wrapped in InvalidDataAccessApiUsageException), fell
 * through to the catch-all handler and answered INTERNAL_ERROR. A typo in a
 * query string is a client error; nine list endpoints called it a server fault.
 *
 * <p>Found by sweeping every GET in the OpenAPI document with
 * {@code ?sort=nosuchfield,asc}. The endpoints that did NOT fail - products,
 * suppliers, categories, brands - are the ones that take sortBy/sortDir and map
 * them through an explicit whitelist instead of binding a Pageable, which is
 * why the defect was invisible from the modules most tests exercise.
 *
 * <p>The fix is in GlobalExceptionHandler rather than in nine controllers: the
 * fault is one unmapped exception, and converting those endpoints to the
 * whitelist pattern would be a refactor of nine modules, not a bug fix.
 */
class InvalidSortFieldIT extends AbstractIntegrationTest {

    /** Endpoints that bind a Pageable, so the raw sort reaches the query. */
    private static final String[] PAGEABLE_LISTS = {
            "/v1/customers", "/v1/invoices", "/v1/purchases", "/v1/quotations",
            "/v1/payments", "/v1/expenses", "/v1/stock", "/v1/projects", "/v1/coupons",
    };

    @Test
    @DisplayName("BUG-BE-004: an unknown sort field is 400 INVALID_SORT_FIELD, never 500")
    void unknownSortFieldIsBadRequest() throws Exception {
        String token = bearer(OWNER_MOBILE, OWNER_PASSWORD);

        for (String list : PAGEABLE_LISTS) {
            mockMvc.perform(get(list + "?sort=nosuchfield,asc").header("Authorization", token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_SORT_FIELD"));
        }
    }

    /**
     * The over-fix guard. Mapping InvalidDataAccessApiUsageException wholesale
     * to 400 would also silence genuinely broken queries, so the handler
     * inspects the cause - and a legitimate sort must still work.
     */
    @Test
    @DisplayName("BUG-BE-004: a valid sort field still sorts, and is not caught by the new handler")
    void validSortStillWorks() throws Exception {
        String token = bearer(OWNER_MOBILE, OWNER_PASSWORD);

        mockMvc.perform(get("/v1/customers?sort=customerName,asc").header("Authorization", token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v1/customers").header("Authorization", token))
                .andExpect(status().isOk());
    }

    /**
     * The whitelist endpoints ignore an unknown sort rather than rejecting it.
     * That difference is deliberate and predates this fix; asserting it here
     * stops a later "consistency" change from silently turning a working
     * request into a 400.
     */
    @Test
    @DisplayName("BUG-BE-004: whitelist-based lists still ignore an unknown sort and return 200")
    void whitelistListsAreUnaffected() throws Exception {
        String token = bearer(OWNER_MOBILE, OWNER_PASSWORD);

        for (String list : new String[] { "/v1/products", "/v1/suppliers" }) {
            mockMvc.perform(get(list + "?sort=nosuchfield,asc").header("Authorization", token))
                    .andExpect(status().isOk());
        }
    }
}
