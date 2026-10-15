package com.openq.exception;

/** The slot cannot be booked for a reason other than being taken (it is in the past, or the doctor is inactive). */
public class SlotUnavailableException extends RuntimeException {

    public SlotUnavailableException(Long slotId) {
        super("Slot " + slotId + " can no longer be booked");
    }
}
