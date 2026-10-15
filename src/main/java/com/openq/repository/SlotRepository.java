package com.openq.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.openq.dto.DoctorSlotCount;
import com.openq.entity.Slot;
import com.openq.entity.SlotStatus;

import jakarta.persistence.LockModeType;

public interface SlotRepository extends JpaRepository<Slot, Long> {

    /** Upcoming slots of one doctor with the given status, earliest first. */
    List<Slot> findByDoctorIdAndStatusAndStartTimeAfterOrderByStartTimeAsc(
            Long doctorId, SlotStatus status, LocalDateTime after);

    /** Number of upcoming slots with the given status per doctor (one query for the whole directory). */
    @Query("select new com.openq.dto.DoctorSlotCount(s.doctor.id, count(s)) "
            + "from Slot s where s.status = :status and s.startTime > :after group by s.doctor.id")
    List<DoctorSlotCount> countByDoctor(@Param("status") SlotStatus status, @Param("after") LocalDateTime after);

    /**
     * Loads a slot and takes a row lock (SELECT ... FOR UPDATE). Concurrent bookings/cancellations
     * of the same slot queue up here; only one transaction at a time can change the slot.
     * The doctor is intentionally NOT join-fetched so the lock covers only the slot row.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Slot s where s.id = :id")
    Optional<Slot> findByIdForUpdate(@Param("id") Long id);
}
