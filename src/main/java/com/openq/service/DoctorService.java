package com.openq.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.openq.dto.DoctorCard;
import com.openq.dto.DoctorSlotCount;
import com.openq.entity.Doctor;
import com.openq.entity.SlotStatus;
import com.openq.exception.DoctorNotFoundException;
import com.openq.repository.DoctorRepository;
import com.openq.repository.SlotRepository;

@Service
public class DoctorService {

    private final DoctorRepository doctorRepository;
    private final SlotRepository slotRepository;
    private final Clock clock;

    public DoctorService(DoctorRepository doctorRepository, SlotRepository slotRepository, Clock clock) {
        this.doctorRepository = doctorRepository;
        this.slotRepository = slotRepository;
        this.clock = clock;
    }

    /** Active doctors with their number of upcoming open slots (2 queries total, regardless of doctor count). */
    @Transactional(readOnly = true)
    public List<DoctorCard> listDirectory() {
        Map<Long, Long> counts = new HashMap<>();
        for (DoctorSlotCount c : slotRepository.countByDoctor(SlotStatus.AVAILABLE, LocalDateTime.now(clock))) {
            counts.put(c.doctorId(), c.slotCount());
        }
        return doctorRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(d -> new DoctorCard(d, counts.getOrDefault(d.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Doctor> listActive() {
        return doctorRepository.findByActiveTrueOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Doctor getActiveDoctor(Long id) {
        return doctorRepository.findByIdAndActiveTrue(id).orElseThrow(() -> new DoctorNotFoundException(id));
    }
}
