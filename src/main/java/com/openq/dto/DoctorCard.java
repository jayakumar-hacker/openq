package com.openq.dto;

import com.openq.entity.Doctor;

/** A doctor plus the number of upcoming open slots, for the directory page. */
public class DoctorCard {

    private final Doctor doctor;
    private final long availableSlots;

    public DoctorCard(Doctor doctor, long availableSlots) {
        this.doctor = doctor;
        this.availableSlots = availableSlots;
    }

    public Doctor getDoctor() { return doctor; }
    public long getAvailableSlots() { return availableSlots; }
}
