package com.openq.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.servlet.ModelAndView;

/**
 * Central error handling for the browser (HTML) routes. Users only ever see a friendly message;
 * SQL, stack traces and internal details are logged server-side, never rendered.
 */
@ControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Name of the partial unique index in schema.sql. */
    static final String ACTIVE_SLOT_INDEX = "ux_appointment_active_slot";

    @ExceptionHandler(ResourceNotFoundException.class)
    public ModelAndView notFound(ResourceNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "Not found", "We could not find what you were looking for.");
    }

    @ExceptionHandler(SlotAlreadyBookedException.class)
    public ModelAndView slotTaken(SlotAlreadyBookedException ex) {
        return error(HttpStatus.CONFLICT, "Slot no longer available",
                "Sorry, that time slot has just been taken. Please choose another slot.");
    }

    @ExceptionHandler(AppointmentNotCancellableException.class)
    public ModelAndView notCancellable(AppointmentNotCancellableException ex) {
        return error(HttpStatus.CONFLICT, "Already cancelled", "That appointment has already been cancelled.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ModelAndView integrity(DataIntegrityViolationException ex) {
        String root = String.valueOf(NestedExceptionUtils.getMostSpecificCause(ex).getMessage());
        if (root.contains(ACTIVE_SLOT_INDEX)) {
            // The database guard fired: two requests raced past the application check.
            return slotTaken(new SlotAlreadyBookedException(null));
        }
        log.error("Unexpected data integrity violation", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong",
                "An unexpected error occurred. Please try again later.");
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ModelAndView lockTimeout(PessimisticLockingFailureException ex) {
        log.warn("Row lock could not be acquired in time");
        return error(HttpStatus.SERVICE_UNAVAILABLE, "Please try again",
                "The system is busy with another booking for this slot. Please try again in a moment.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ModelAndView badArgument(MethodArgumentTypeMismatchException ex) {
        return error(HttpStatus.BAD_REQUEST, "Bad request", "The request contained an invalid value.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ModelAndView noSuchPage(NoResourceFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "Page not found", "We could not find the page you were looking for.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ModelAndView wrongMethod(HttpRequestMethodNotSupportedException ex) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed", "That action is not supported for this page.");
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView fallback(Exception ex) throws Exception {
        // Let Spring's own status-carrying exceptions (404 no handler, 405, ...) keep their codes.
        if (ex instanceof org.springframework.web.ErrorResponse) {
            throw ex;
        }
        log.error("Unhandled exception", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong",
                "An unexpected error occurred. Please try again later.");
    }

    private ModelAndView error(HttpStatus status, String title, String message) {
        ModelAndView mav = new ModelAndView("error");
        mav.setStatus(status);
        mav.addObject("status", status.value());
        mav.addObject("title", title);
        mav.addObject("message", message);
        return mav;
    }
}
