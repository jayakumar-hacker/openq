package com.openq.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.openq.entity.Doctor;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    List<Doctor> findByActiveTrueOrderByNameAsc();

    Optional<Doctor> findByIdAndActiveTrue(Long id);
}
