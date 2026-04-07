package com.skillhub.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleResourceNotFound(ResourceNotFoundException ex, Model model) {
        model.addAttribute("error", ex.getMessage());
        return "error/404";
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<String> handleGeneralException(Throwable ex) {
        try {
            java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter("error_debug.txt", true));
            pw.println("--- Error at " + java.time.LocalDateTime.now() + " ---");
            ex.printStackTrace(pw);
            pw.println("--------------------------------------------------");
            pw.close();
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("An unexpected error occurred: " + ex.getMessage());
    }
}
