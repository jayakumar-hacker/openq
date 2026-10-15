package com.openq.dto;

/** Result row of the "available slots per doctor" aggregate query. */
public record DoctorSlotCount(Long doctorId, long slotCount) {
}
