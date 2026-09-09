package com.codewithkelvin.fx.common;

/** Something the caller named does not exist. Maps to 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException of(String what, Object key) {
        return new NotFoundException(what + " '" + key + "' does not exist");
    }
}
