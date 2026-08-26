package com.hardware.erp.project.entity;

/**
 * Manual today - LABOUR/EMPLOYEE entries are typed in by hand until the
 * Labour/Team/Attendance module exists to derive them from real attendance
 * records instead. See V18 migration comment.
 */
public enum ProjectExpenseCategory {
    LABOUR,
    EMPLOYEE,
    FOOD,
    STAY,
    PETROL,
    OTHER
}
