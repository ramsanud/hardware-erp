package com.hardware.erp.common.util;

import com.hardware.erp.common.util.GstSplit.Split;
import com.hardware.erp.common.util.GstSplit.SupplyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** CR-091 Phase 1. The brief's own examples, plus the edges a paisa can fall on. */
class GstSplitTest {

    @Test
    @DisplayName("intra-state: ₹1,000 at 18% is CGST ₹90 + SGST ₹90, total ₹1,180")
    void intraStateSplitsInHalf() {
        Split split = GstSplit.split(18_000L, SupplyType.INTRA);
        assertThat(split.cgstPaise()).isEqualTo(9_000L);
        assertThat(split.sgstPaise()).isEqualTo(9_000L);
        assertThat(split.igstPaise()).isZero();
        assertThat(split.total()).isEqualTo(18_000L);
    }

    @Test
    @DisplayName("inter-state: the whole ₹180 is IGST, CGST and SGST are zero")
    void interStateIsAllIgst() {
        Split split = GstSplit.split(18_000L, SupplyType.INTER);
        assertThat(split.cgstPaise()).isZero();
        assertThat(split.sgstPaise()).isZero();
        assertThat(split.igstPaise()).isEqualTo(18_000L);
    }

    @Test
    @DisplayName("an odd paisa lands on SGST, never lost - the halves always sum to the input")
    void oddPaisaGoesToSgst() {
        Split split = GstSplit.split(1_235L, SupplyType.INTRA);
        assertThat(split.cgstPaise()).isEqualTo(617L);
        assertThat(split.sgstPaise()).isEqualTo(618L);
        assertThat(split.total()).isEqualTo(1_235L);
    }

    @Test
    @DisplayName("zero GST splits to zero everywhere")
    void zeroGst() {
        assertThat(GstSplit.split(0L, SupplyType.INTRA).total()).isZero();
        assertThat(GstSplit.split(0L, SupplyType.INTER).total()).isZero();
    }

    @Test
    @DisplayName("place of supply: customer state, else GSTIN prefix, else shop state")
    void placeOfSupplyPrecedence() {
        assertThat(GstSplit.placeOfSupply("29", "33AABCS1429B1Z1", "33")).isEqualTo("29");
        assertThat(GstSplit.placeOfSupply(null, "29AABCS1429B1ZQ", "33")).isEqualTo("29");
        assertThat(GstSplit.placeOfSupply("", "  ", "33")).isEqualTo("33");
        assertThat(GstSplit.placeOfSupply(null, null, null)).isNull();
    }

    @Test
    @DisplayName("supply type: differing states are INTER; a missing state on either side is INTRA, never IGST by accident")
    void supplyTypeRule() {
        assertThat(GstSplit.supplyType("33", "29")).isEqualTo(SupplyType.INTER);
        assertThat(GstSplit.supplyType("33", "33")).isEqualTo(SupplyType.INTRA);
        assertThat(GstSplit.supplyType(null, "29")).isEqualTo(SupplyType.INTRA);
        assertThat(GstSplit.supplyType("33", null)).isEqualTo(SupplyType.INTRA);
        assertThat(GstSplit.supplyType(" 33 ", "33")).isEqualTo(SupplyType.INTRA);
    }
}
