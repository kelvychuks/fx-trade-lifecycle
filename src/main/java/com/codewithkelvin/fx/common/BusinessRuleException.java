package com.codewithkelvin.fx.common;

/**
 * The request was well formed but the desk's rules refuse it — an inactive
 * counterparty, a notional over the limit, a lifecycle transition that is not
 * allowed. Maps to 422, because retrying the same request will not help.
 */
public class BusinessRuleException extends RuntimeException {

    private final String code;

    public BusinessRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
