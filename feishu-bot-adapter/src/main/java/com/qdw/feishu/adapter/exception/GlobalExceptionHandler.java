package com.qdw.feishu.adapter.exception;

import com.qdw.feishu.domain.exception.ConnectionLostException;
import com.qdw.feishu.domain.exception.MessageInvalidException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ConnectionLostException.class)
    public ResponseEntity<String> handleConnectionLost(ConnectionLostException e) {
        log.warn("Connection lost, auto reconnecting: {}", e.getMessage());
        return ResponseEntity.status(503).body("Service temporarily unavailable - reconnecting");
    }

    @ExceptionHandler(MessageInvalidException.class)
    public void handleInvalidMessage(MessageInvalidException e) {
        log.error("Invalid message received: {}", e.getMessage());
    }
}
