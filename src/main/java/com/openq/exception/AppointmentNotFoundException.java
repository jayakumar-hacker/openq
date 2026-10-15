package com.openq.exception;

public class AppointmentNotFoundException extends ResourceNotFoundException {

    public AppointmentNotFoundException(Long id) {
        super("Appointment " + id + " was not found.");
    }
}
