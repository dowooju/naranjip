package com.naranjip.web;
import com.naranjip.application.AuthException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class AuthErrorHandler {
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<?> auth(AuthException e) {
        return ResponseEntity.status(e.status).body(Map.of("message",e.getMessage(),"fields",e.fields));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> validation(MethodArgumentNotValidException e) {
        Map<String,String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(error -> fields.putIfAbsent(error.getField(),error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(Map.of("message","입력한 내용을 확인해 주세요.","fields",fields));
    }
}
