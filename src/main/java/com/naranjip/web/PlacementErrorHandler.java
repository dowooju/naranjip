package com.naranjip.web;

import com.naranjip.application.PlacementException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestControllerAdvice
public class PlacementErrorHandler {
    @ExceptionHandler(PlacementException.class)
    public ResponseEntity<Map<String, String>> placement(PlacementException error) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", error.getMessage()));
    }
}
