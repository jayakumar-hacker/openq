package com.openq.exception;

public class AppointmentAlreadyCancelledException extends RuntimeException {

    public AppointmentAlreadyCancelledException(Long id) {
        super("Appointment " + id + " is already cancelled");
    }
}
