package com.naranjip.application;
import java.util.Map;
public class AuthException extends RuntimeException {
    public final int status;
    public final Map<String,String> fields;
    public AuthException(int status, String message, Map<String,String> fields) {
        super(message); this.status = status; this.fields = fields;
    }
}
