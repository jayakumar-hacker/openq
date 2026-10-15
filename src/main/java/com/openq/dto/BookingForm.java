package com.openq.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Form-backing object for the patient details. Persistence entities are never bound to forms. */
public class BookingForm {

    @NotBlank(message = "Please enter your full name.")
    @Size(max = 100, message = "Name must be at most 100 characters.")
    private String patientName;

    @NotBlank(message = "Please enter your email address.")
    @Email(message = "Please enter a valid email address.")
    @Size(max = 160, message = "Email must be at most 160 characters.")
    private String patientEmail;

    /** Optional. Digits, spaces, dashes, parentheses and an optional leading +; 7-20 characters. */
    @Pattern(regexp = "^$|^\\+?[0-9 ()-]{7,20}$",
            message = "Phone may contain digits, spaces, dashes and parentheses (7-20 characters).")
    private String patientPhone;

    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }

    public String getPatientEmail() { return patientEmail; }
    public void setPatientEmail(String patientEmail) { this.patientEmail = patientEmail; }

    public String getPatientPhone() { return patientPhone; }
    public void setPatientPhone(String patientPhone) { this.patientPhone = patientPhone; }
}
