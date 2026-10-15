package com.openq.exception;

/** The appointment exists but is not in BOOKED state (e.g. already cancelled). Maps to HTTP 409. */
public class AppointmentNotCancellableException extends RuntimeException {

    public AppointmentNotCancellableException(Long appointmentId) {
        super("Appointment " + appointmentId + " is already cancelled.");
    }
}
