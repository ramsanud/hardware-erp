package com.hardware.erp.expense.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** An optional receipt photo for one expense (CR-036 phase 3) - own table, same pattern as ProductImage/UserAvatar. */
@Entity
@Table(name = "expense_receipt")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseReceipt {

    @Id
    @Column(name = "business_expense_id")
    private Long businessExpenseId;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private Integer fileSize;

    @Column(name = "image_data", nullable = false)
    private byte[] imageData;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
