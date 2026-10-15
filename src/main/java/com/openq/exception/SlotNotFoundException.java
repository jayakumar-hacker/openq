package com.openq.exception;

public class SlotNotFoundException extends ResourceNotFoundException {

    public SlotNotFoundException(Long id) {
        super("Slot " + id + " was not found.");
    }
}
