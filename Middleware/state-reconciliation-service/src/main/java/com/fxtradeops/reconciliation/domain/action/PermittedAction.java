package com.fxtradeops.reconciliation.domain.action;

/**
 * The ENTIRE fixed enumerated catalogue of permitted corrective actions.
 * Policy may only SELECT from these — never expand, never free-form.
 * This service never executes any action; it only reports which are permitted.
 */
public enum PermittedAction {
    REFRESH_CACHE,
    REPLAY_EVENT,
    RESYNC_DOCUMENT_STORE,
    RESYNC_RELATIONAL_STORE,
    OPEN_RECONCILIATION_CASE,
    NO_ACTION
}
