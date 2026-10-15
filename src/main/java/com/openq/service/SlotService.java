package com.openq.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.openq.entity.Slot;
import com.openq.entity.SlotStatus;
import com.openq.exception.SlotAlreadyBookedException;
import com.openq.exception.SlotNotFoundException;
import com.openq.repository.SlotRepository;

@Service
public class SlotService {

    private final SlotRepository slotRepository;
    private final DoctorService doctorService;
    private final Clock clock;

    public SlotService(SlotRepository slotRepository, DoctorService doctorService, Clock clock) {
        this.slotRepository = slotRepository;
        this.doctorService = doctorService;
        this.clock = clock;
    }

    /** Upcoming AVAILABLE slots of an active doctor, earliest first. Throws if the doctor does not exist. */
    @Transactional(readOnly = true)
    public List<Slot> listAvailableSlots(Long doctorId) {
        doctorService.getActiveDoctor(doctorId);
        return slotRepository.findByDoctorIdAndStatusAndStartTimeAfterOrderByStartTimeAsc(
                doctorId, SlotStatus.AVAILABLE, LocalDateTime.now(clock));
    }

    /** Same list grouped by calendar day (insertion-ordered) for the slot page. */
    @Transactional(readOnly = true)
    public Map<LocalDate, List<Slot>> listAvailableSlotsByDay(Long doctorId) {
        Map<LocalDate, List<Slot>> byDay = new LinkedHashMap<>();
        for (Slot slot : listAvailableSlots(doctorId)) {
            byDay.computeIfAbsent(slot.getStartTime().toLocalDate(), d -> new java.util.ArrayList<>()).add(slot);
        }
        return byDay;
    }

    /**
     * Returns a slot that can still be offered to a patient (exists, AVAILABLE, in the future).
     * This is a convenience check for the booking form only; the authoritative check happens
     * under a row lock in AppointmentService.book().
     */
    @Transactional(readOnly = true)
    public Slot getBookableSlot(Long slotId) {
        Slot slot = slotRepository.findById(slotId).orElseThrow(() -> new SlotNotFoundException(slotId));
        if (!slot.isAvailable() || !slot.getStartTime().isAfter(LocalDateTime.now(clock))) {
            throw new SlotAlreadyBookedException(slotId);
        }
        // Touch the doctor inside the transaction so the view can render it (open-in-view is off).
        slot.getDoctor().getName();
        return slot;
    }
}
