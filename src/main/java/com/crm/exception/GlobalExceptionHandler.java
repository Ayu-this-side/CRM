package com.crm.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.io.IOException;

/**
 * Global exception handler — catches unhandled exceptions from any controller
 * and renders a friendly error page with the correct HTTP status.
 *
 * AccessDeniedException raised inside the MVC layer is redirected to the
 * dedicated /access-denied page; denials raised in the filter chain are
 * handled by Spring Security's AccessDeniedHandler (see SecurityConfig).
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public String handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied for [{}]: {}", request.getRequestURI(), ex.getMessage());
        return "redirect:/access-denied";
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public String handleNotFound(ResourceNotFoundException ex,
                                 HttpServletRequest request,
                                 HttpServletResponse response,
                                 Model model) {
        log.warn("Resource not found for [{}]: {}", request.getRequestURI(), ex.getMessage());
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        model.addAttribute("errorTitle", "Not Found");
        model.addAttribute("errorMessage", ex.getMessage());
        model.addAttribute("statusCode", 404);
        return "error/error";
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class,
            MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public String handleBadRequest(Exception ex,
                                   HttpServletRequest request,
                                   HttpServletResponse response,
                                   Model model) {
        log.warn("Invalid request [{}]: {}", request.getRequestURI(), ex.getMessage());
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        model.addAttribute("errorTitle", "Invalid Request");
        model.addAttribute("errorMessage", friendlyMessage(ex));
        model.addAttribute("statusCode", 400);
        return "error/error";
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public String handleDataConflict(DataIntegrityViolationException ex,
                                     HttpServletRequest request,
                                     HttpServletResponse response,
                                     Model model) {
        log.warn("Data conflict for [{}]: {}", request.getRequestURI(), ex.getMessage());
        response.setStatus(HttpServletResponse.SC_CONFLICT);
        model.addAttribute("errorTitle", "Conflicting Data");
        model.addAttribute("errorMessage", "The submitted data conflicts with an existing record. Please review and try again.");
        model.addAttribute("statusCode", 409);
        return "error/error";
    }

    @ExceptionHandler(Exception.class)
    public String handleGeneral(Exception ex,
                                HttpServletRequest request,
                                HttpServletResponse response,
                                Model model) throws IOException {
        log.error("Unhandled exception for [{}]: {}", request.getRequestURI(), ex.getMessage(), ex);
        if (!response.isCommitted()) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
        model.addAttribute("errorTitle", "Something Went Wrong");
        model.addAttribute("errorMessage", "An unexpected error occurred. Please try again or contact support.");
        model.addAttribute("statusCode", 500);
        return "error/error";
    }

    /** Enum conversion failures expose raw class names — keep those details server-side. */
    private String friendlyMessage(Exception ex) {
        if (ex instanceof MethodArgumentTypeMismatchException || ex instanceof MissingServletRequestParameterException) {
            return "One or more filter parameters are invalid. Please reset the filters and try again.";
        }
        return ex.getMessage() != null ? ex.getMessage() : "The request could not be processed.";
    }
}
