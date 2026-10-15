package com.openq.exception;

/** The slot was taken (or is no longer bookable). Maps to HTTP 409. */
public class SlotAlreadyBookedException extends RuntimeException {

    public SlotAlreadyBookedException(Long slotId) {
        super("Slot " + slotId + " is no longer available.");
    }
}
