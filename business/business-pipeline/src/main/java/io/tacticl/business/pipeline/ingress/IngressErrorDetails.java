package io.tacticl.business.pipeline.ingress;

import io.cidadel.framework.exception.ErrorDetails;
import org.springframework.http.HttpStatus;

/**
 * Error definitions for the pipeline-ingress front door (business-pipeline). Property keys resolve
 * against the {@code messages/pdlc-ingress-errors} bundle (registered in GlobalMessageSourceConfig).
 */
public enum IngressErrorDetails implements ErrorDetails {

    /** No EntryPoint matched the (channel, externalKey) probe and no channel default exists. */
    ENTRY_POINT_NOT_FOUND(HttpStatus.NOT_FOUND, "pdlc-ingress-entry-point-not-found"),

    /** Caller's tacticl user id is not in the resolved EntryPoint's admin set. */
    NOT_AUTHORIZED(HttpStatus.FORBIDDEN, "pdlc-ingress-not-authorized"),

    /** Channel identity did not resolve to a linked tacticl user (hard account-link precondition). */
    UNLINKED_IDENTITY(HttpStatus.FORBIDDEN, "pdlc-ingress-unlinked-identity"),

    /** Request kind requires a payload that was missing/malformed (e.g. CHECKPOINT_DECISION w/o decision). */
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "pdlc-ingress-invalid-request");

    private final HttpStatus httpStatus;
    private final String propertyKey;

    IngressErrorDetails(HttpStatus httpStatus, String propertyKey) {
        this.httpStatus = httpStatus;
        this.propertyKey = propertyKey;
    }

    @Override
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    @Override
    public String getPropertyKey() {
        return propertyKey;
    }
}
