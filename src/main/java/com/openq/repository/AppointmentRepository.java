package com.openq.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.openq.entity.Appointment;
import com.openq.entity.AppointmentStatus;

import jakarta.persistence.LockModeType;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    // Join-fetching slot and doctor loads everything in ONE query (no N+1 on the admin dashboard).
    String DETAILED = "select a from Appointment a join fetch a.slot s join fetch s.doctor d ";
    String ORDER = " order by s.startTime asc, a.id asc";

    @Query(DETAILED + ORDER)
    List<Appointment> findAllDetailed();

    @Query(DETAILED + "where d.id = :doctorId" + ORDER)
    List<Appointment> findByDoctorDetailed(@Param("doctorId") Long doctorId);

    @Query(DETAILED + "where a.status = :status" + ORDER)
    List<Appointment> findByStatusDetailed(@Param("status") AppointmentStatus status);

    @Query(DETAILED + "where d.id = :doctorId and a.status = :status" + ORDER)
    List<Appointment> findByDoctorAndStatusDetailed(@Param("doctorId") Long doctorId,
                                                    @Param("status") AppointmentStatus status);

    @Query(DETAILED + "where a.id = :id")
    Optional<Appointment> findDetailedById(@Param("id") Long id);

    /** Row-locks the appointment so two simultaneous cancellations cannot both succeed. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Appointment a where a.id = :id")
    Optional<Appointment> findByIdForUpdate(@Param("id") Long id);

    long countBySlotIdAndStatus(Long slotId, AppointmentStatus status);
}
