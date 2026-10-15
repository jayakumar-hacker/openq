package com.openq.exception;

public class DoctorNotFoundException extends ResourceNotFoundException {

    public DoctorNotFoundException(Long id) {
        super("Doctor " + id + " was not found.");
    }
}
