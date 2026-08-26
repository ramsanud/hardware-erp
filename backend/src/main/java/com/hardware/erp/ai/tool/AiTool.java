package com.hardware.erp.ai.tool;

import java.util.Map;

/**
 * One safe, narrow, already-tenant-scoped query the AI assistant may call
 * (CR-027). Deliberately NOT a free-form SQL/JPQL executor - letting an LLM
 * generate a query against a multi-tenant database is exactly the kind of
 * cross-tenant data leak CR-016's tenant_id discipline exists to prevent.
 * Every implementation delegates to an existing, already permission- and
 * tenant-scoped service (SecurityUtils.requireCurrentTenantId() inside that
 * service, never here) - this interface only adds a permission label so
 * AiToolRegistry can hide a tool from a caller who lacks it, the same way
 * the sidebar hides a nav item.
 */
public interface AiTool {

    /** Tool name as given to the LLM - stable, snake_case, never renamed once shipped (the model may have learned it). */
    String name();

    /** Plain-language description shown to the LLM so it knows when to call this. */
    String description();

    /** Parameter name -> plain-language description. Empty map for a no-argument tool. Every parameter is a plain string - keeps the JSON schema trivial. */
    Map<String, String> parameters();

    /** Permission required to use this tool, or null if any authenticated user may. */
    String requiredPermission();

    /** Runs the query and returns a plain-text result for the LLM to read - never raw entities, never a stack trace. */
    String execute(Map<String, String> args);
}
