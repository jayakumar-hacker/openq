package com.openq.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "doctor")
public class Doctor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 80)
    private String specialization;

    @Column(length = 120)
    private String qualification;

    @Column(name = "clinic_name", nullable = false, length = 120)
    private String clinicName;

    @Column(nullable = false, length = 160)
    private String email;

    @Column(nullable = false)
    private boolean active = true;

    protected Doctor() {
        // required by JPA
    }

    public Doctor(String name, String specialization, String qualification, String clinicName, String email) {
        this.name = name;
        this.specialization = specialization;
        this.qualification = qualification;
        this.clinicName = clinicName;
        this.email = email;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getSpecialization() { return specialization; }
    public String getQualification() { return qualification; }
    public String getClinicName() { return clinicName; }
    public String getEmail() { return email; }
    public boolean isActive() { return active; }
}
