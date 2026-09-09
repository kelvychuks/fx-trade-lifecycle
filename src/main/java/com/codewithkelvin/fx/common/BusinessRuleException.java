package com.codewithkelvin.fx.common;

/**
 * The request was well formed but the desk's rules refuse it: inactive
 * counterparty, notional over the limit, illegal lifecycle transition. Maps to
 * 422, since retrying the same request will not help.
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
