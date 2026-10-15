package com.openq.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.openq.dto.BookingConfirmation;
import com.openq.dto.BookingForm;
import com.openq.entity.Appointment;
import com.openq.entity.AppointmentStatus;
import com.openq.entity.Slot;
import com.openq.exception.AppointmentNotCancellableException;
import com.openq.exception.AppointmentNotFoundException;
import com.openq.exception.SlotAlreadyBookedException;
import com.openq.exception.SlotNotFoundException;
import com.openq.repository.AppointmentRepository;
import com.openq.repository.SlotRepository;

/**
 * Booking and cancellation.
 *
 * <p>Two layers prevent double-booking:
 * <ol>
 *   <li><b>Service layer</b>: the slot row is locked (SELECT ... FOR UPDATE) inside one transaction, so
 *       concurrent requests for the same slot run one after the other and the loser sees BOOKED and gets a
 *       friendly {@link SlotAlreadyBookedException}.</li>
 *   <li><b>Database</b>: the partial unique index ux_appointment_active_slot allows at most one BOOKED
 *       appointment per slot, so even a bug, a second application instance or a manual SQL insert cannot
 *       create a double booking. A violation surfaces as DataIntegrityViolationException, which the global
 *       exception handler turns into the same "slot already booked" response.</li>
 * </ol>
 */
@Service
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final SlotRepository slotRepository;
    private final Clock clock;

    public AppointmentService(AppointmentRepository appointmentRepository, SlotRepository slotRepository, Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.slotRepository = slotRepository;
        this.clock = clock;
    }

    @Transactional
    public BookingConfirmation book(Long slotId, BookingForm form) {
        LocalDateTime now = LocalDateTime.now(clock);

        // 1-3. Validate the slot and lock it for the rest of this transaction.
        Slot slot = slotRepository.findByIdForUpdate(slotId).orElseThrow(() -> new SlotNotFoundException(slotId));

        // 4. Authoritative availability check, performed while holding the lock.
        if (!slot.isAvailable() || !slot.getStartTime().isAfter(now)) {
            throw new SlotAlreadyBookedException(slotId);
        }

        // 5. Create the appointment. saveAndFlush sends the INSERT now, inside the transaction, so a
        //    unique-index violation is raised here (and rolls back) instead of at some later commit.
        slot.markBooked();
        Appointment appointment = new Appointment(slot, form.getPatientName().trim(),
                form.getPatientEmail().trim(), normalisePhone(form.getPatientPhone()), now);
        appointmentRepository.saveAndFlush(appointment);

        // 6-7. The transaction commits when this method returns; build the result from managed entities.
        return BookingConfirmation.from(appointment);
    }

    @Transactional
    public void cancel(Long appointmentId) {
        // Lock the appointment first: a second concurrent cancel waits here, then sees CANCELLED.
        Appointment appointment = appointmentRepository.findByIdForUpdate(appointmentId)
                .orElseThrow(() -> new AppointmentNotFoundException(appointmentId));

        if (!appointment.isBooked()) {
            throw new AppointmentNotCancellableException(appointmentId);
        }

        // Then lock the slot so cancel and a concurrent booking of the same slot are serialised too.
        Slot slot = slotRepository.findByIdForUpdate(appointment.getSlot().getId())
                .orElseThrow(() -> new SlotNotFoundException(appointment.getSlot().getId()));

        appointment.cancel(LocalDateTime.now(clock));
        slot.markAvailable();
        appointmentRepository.flush();
    }

    @Transactional(readOnly = true)
    public BookingConfirmation getConfirmation(Long appointmentId) {
        Appointment appointment = appointmentRepository.findDetailedById(appointmentId)
                .orElseThrow(() -> new AppointmentNotFoundException(appointmentId));
        return BookingConfirmation.from(appointment);
    }

    /** Admin list. Either filter may be null (= no filter). */
    @Transactional(readOnly = true)
    public List<Appointment> search(Long doctorId, AppointmentStatus status) {
        if (doctorId != null && status != null) {
            return appointmentRepository.findByDoctorAndStatusDetailed(doctorId, status);
        }
        if (doctorId != null) {
            return appointmentRepository.findByDoctorDetailed(doctorId);
        }
        if (status != null) {
            return appointmentRepository.findByStatusDetailed(status);
        }
        return appointmentRepository.findAllDetailed();
    }

    private static String normalisePhone(String phone) {
        return (phone == null || phone.isBlank()) ? null : phone.trim();
    }
}
