package com.openq.dto;

import java.time.LocalDateTime;

import com.openq.entity.Appointment;

/** Read-only view of a booked appointment, built inside a transaction so no lazy loading happens in views. */
public class BookingConfirmation {

    private final Long appointmentId;
    private final String status;
    private final String doctorName;
    private final String specialization;
    private final String clinicName;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final String patientName;
    private final String patientEmail;

    private BookingConfirmation(Appointment a) {
        this.appointmentId = a.getId();
        this.status = a.getStatus().name();
        this.doctorName = a.getSlot().getDoctor().getName();
        this.specialization = a.getSlot().getDoctor().getSpecialization();
        this.clinicName = a.getSlot().getDoctor().getClinicName();
        this.startTime = a.getSlot().getStartTime();
        this.endTime = a.getSlot().getEndTime();
        this.patientName = a.getPatientName();
        this.patientEmail = a.getPatientEmail();
    }

    public static BookingConfirmation from(Appointment appointment) {
        return new BookingConfirmation(appointment);
    }

    public Long getAppointmentId() { return appointmentId; }
    public String getStatus() { return status; }
    public String getDoctorName() { return doctorName; }
    public String getSpecialization() { return specialization; }
    public String getClinicName() { return clinicName; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public String getPatientName() { return patientName; }
    public String getPatientEmail() { return patientEmail; }
}
