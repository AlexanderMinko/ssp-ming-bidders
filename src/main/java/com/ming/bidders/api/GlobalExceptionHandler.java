package com.ming.bidders.api;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;

/** Malformed JSON is thrown by the message converter (not avoidable); every other outcome is a return value. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Void> unreadable(HttpMessageNotReadableException exception) {
        log.debug("400 malformed body: {}", exception.getMessage());
        return ResponseEntity.badRequest().build();
    }
}
