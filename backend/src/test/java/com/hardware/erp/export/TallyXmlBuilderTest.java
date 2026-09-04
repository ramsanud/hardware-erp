package com.hardware.erp.export;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real XML parsing, not string matching (CR-022's own precedent for
 * InvoicePdfServiceTest) - the risk is a malformed envelope Tally would
 * refuse to import, which a substring assertion cannot catch.
 */
class TallyXmlBuilderTest {

    private Document parse(String xml) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        var builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("a full envelope with masters and vouchers is well-formed XML")
    void wellFormedEnvelope() throws Exception {
        StringBuilder xml = new StringBuilder();
        xml.append(TallyXmlBuilder.envelopeStart("Sara Hardware & Electricals"));
        xml.append(TallyXmlBuilder.messageStart());
        xml.append(TallyXmlBuilder.systemLedger("Sales Account", "Sales Accounts"));
        xml.append(TallyXmlBuilder.partyLedger("Suresh & Co (#2)", "Sundry Debtors", "29BBBBB1111B1Z5", "Karnataka"));
        xml.append(TallyXmlBuilder.stockItemMaster("Lock \"Deluxe\" 60mm", "PCS", "8301", new BigDecimal("18.00")));
        xml.append(TallyXmlBuilder.salesVoucher(LocalDate.of(2026, 8, 20), "INV-000042", "Suresh & Co (#2)",
                "Sales invoice INV-000042", new BigDecimal("1000.00"), new BigDecimal("90.00"),
                new BigDecimal("90.00"), BigDecimal.ZERO, new BigDecimal("1180.00")));
        xml.append(TallyXmlBuilder.messageEnd());
        xml.append(TallyXmlBuilder.envelopeEnd());

        Document document = parse(xml.toString());
        assertThat(document.getDocumentElement().getTagName()).isEqualTo("ENVELOPE");
        assertThat(document.getElementsByTagName("VOUCHER").getLength()).isEqualTo(1);
        assertThat(document.getElementsByTagName("LEDGER").getLength()).isEqualTo(2);
        assertThat(document.getElementsByTagName("STOCKITEM").getLength()).isEqualTo(1);
    }

    @Test
    @DisplayName("a sales voucher's ledger entries sum to zero, intra-state")
    void salesVoucherBalancesIntraState() throws Exception {
        String voucher = TallyXmlBuilder.salesVoucher(LocalDate.of(2026, 8, 20), "INV-000042", "Suresh & Co (#2)",
                "Sales invoice INV-000042", new BigDecimal("1000.00"), new BigDecimal("90.00"),
                new BigDecimal("90.00"), BigDecimal.ZERO, new BigDecimal("1180.00"));

        assertThat(sumLedgerEntries(wrap(voucher))).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("a sales voucher's ledger entries sum to zero, inter-state (IGST)")
    void salesVoucherBalancesInterState() throws Exception {
        String voucher = TallyXmlBuilder.salesVoucher(LocalDate.of(2026, 8, 20), "INV-000043", "Suresh & Co (#2)",
                "Sales invoice INV-000043", new BigDecimal("1000.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("180.00"), new BigDecimal("1180.00"));

        assertThat(sumLedgerEntries(wrap(voucher))).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("a purchase voucher's ledger entries sum to zero")
    void purchaseVoucherBalances() throws Exception {
        String voucher = TallyXmlBuilder.purchaseVoucher(LocalDate.of(2026, 8, 20), "PUR-000010", "ABC Traders (#3)",
                "Purchase bill PUR-000010", new BigDecimal("2000.00"), new BigDecimal("180.00"),
                new BigDecimal("180.00"), BigDecimal.ZERO, new BigDecimal("2360.00"));

        assertThat(sumLedgerEntries(wrap(voucher))).isEqualByComparingTo(BigDecimal.ZERO);
    }

    /** Wraps a bare VOUCHER fragment in a root element so the standalone snippet parses. */
    private String wrap(String fragment) {
        return "<ROOT>" + fragment + "</ROOT>";
    }

    /** AMOUNT is already signed (negative for a deemed-positive-side entry per the convention) - a real Tally import validates this sums to zero per voucher. */
    private BigDecimal sumLedgerEntries(String xml) throws Exception {
        Document document = parse(xml);
        NodeList amounts = document.getElementsByTagName("AMOUNT");
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < amounts.getLength(); i++) {
            Element element = (Element) amounts.item(i);
            sum = sum.add(new BigDecimal(element.getTextContent()));
        }
        return sum;
    }
}
