package com.hardware.erp.report.export;

import java.util.ArrayList;
import java.util.List;

/**
 * CR-086. The one shape every report is flattened into before it becomes a
 * PDF or a workbook: a title, a few caption lines, then one or more tables
 * of already-formatted strings with a totals row. Keeping the exporter
 * ignorant of the five report types means a sixth report costs one mapping
 * method, not a new PDF and a new spreadsheet.
 *
 * Cells are strings on purpose - the display strings the screen shows
 * (Indian digit grouping included), so the file matches the page. The
 * Excel sheet keeps a numeric copy for money columns so the owner can still
 * sum a column; {@link Column#numeric} marks those.
 */
public record ReportDocument(String title, List<String> captions, List<Table> tables) {

    public record Column(String header, boolean numeric) {
        public static Column text(String header) { return new Column(header, false); }
        public static Column money(String header) { return new Column(header, true); }
    }

    /** {@code totals} may be null for a table without a footer. */
    public record Table(String heading, List<Column> columns, List<List<String>> rows, List<String> totals) {}

    public static Builder builder(String title) {
        return new Builder(title);
    }

    public static final class Builder {
        private final String title;
        private final List<String> captions = new ArrayList<>();
        private final List<Table> tables = new ArrayList<>();

        private Builder(String title) {
            this.title = title;
        }

        public Builder caption(String line) {
            if (line != null && !line.isBlank()) captions.add(line);
            return this;
        }

        public Builder table(String heading, List<Column> columns, List<List<String>> rows, List<String> totals) {
            tables.add(new Table(heading, columns, rows, totals));
            return this;
        }

        public ReportDocument build() {
            return new ReportDocument(title, List.copyOf(captions), List.copyOf(tables));
        }
    }
}
