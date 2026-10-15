package com.openq.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.openq.dto.BookingConfirmation;
import com.openq.dto.BookingForm;
import com.openq.entity.Appointment;
import com.openq.entity.AppointmentStatus;
import com.openq.entity.Doctor;
import com.openq.entity.Slot;
import com.openq.entity.SlotStatus;
import com.openq.exception.AppointmentNotCancellableException;
import com.openq.exception.AppointmentNotFoundException;
import com.openq.exception.SlotAlreadyBookedException;
import com.openq.exception.SlotNotFoundException;
import com.openq.repository.AppointmentRepository;
import com.openq.repository.SlotRepository;

/**
 * UNIT tests with mocked repositories: they verify the service's decision logic only.
 * They do NOT prove concurrency safety; that is covered by the PostgreSQL integration tests (*IT).
 */
@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-01-01T04:00:00Z"), ZoneId.of("UTC"));

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private SlotRepository slotRepository;

    private AppointmentService service;
    private Slot slot;

    @BeforeEach
    void setUp() {
        service = new AppointmentService(appointmentRepository, slotRepository, CLOCK);
        Doctor doctor = new Doctor("Dr. Test", "General", "MBBS", "Test Clinic", "t@example.com");
        ReflectionTestUtils.setField(doctor, "id", 1L);
        slot = new Slot(doctor, LocalDateTime.of(2030, 1, 2, 9, 0), LocalDateTime.of(2030, 1, 2, 9, 30));
        ReflectionTestUtils.setField(slot, "id", 10L);
    }

    private static BookingForm form(String phone) {
        BookingForm f = new BookingForm();
        f.setPatientName("  Jane Patient ");
        f.setPatientEmail("jane@example.com");
        f.setPatientPhone(phone);
        return f;
    }

    @Test
    void book_succeeds_marksSlotBookedAndReturnsConfirmation() {
        when(slotRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(slot));
        when(appointmentRepository.saveAndFlush(any(Appointment.class))).thenAnswer(inv -> {
            Appointment a = inv.getArgument(0);
            ReflectionTestUtils.setField(a, "id", 99L);
            return a;
        });

        BookingConfirmation result = service.book(10L, form("  "));

        assertThat(result.getAppointmentId()).isEqualTo(99L);
        assertThat(result.getPatientName()).isEqualTo("Jane Patient");
        assertThat(result.getDoctorName()).isEqualTo("Dr. Test");
        assertThat(result.getStartTime()).isEqualTo(LocalDateTime.of(2030, 1, 2, 9, 0));
        assertThat(slot.getStatus()).isEqualTo(SlotStatus.BOOKED);
    }

    @Test
    void book_blankPhoneIsStoredAsNull() {
        when(slotRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(slot));
        when(appointmentRepository.saveAndFlush(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        service.book(10L, form(""));

        org.mockito.ArgumentCaptor<Appointment> captor = org.mockito.ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getPatientPhone()).isNull();
    }

    @Test
    void book_alreadyBookedSlot_throwsAndCreatesNothing() {
        slot.markBooked();
        when(slotRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> service.book(10L, form(null))).isInstanceOf(SlotAlreadyBookedException.class);
        verify(appointmentRepository, never()).saveAndFlush(any());
    }

    @Test
    void book_slotInThePast_isRejected() {
        Slot past = new Slot(slot.getDoctor(), LocalDateTime.of(2029, 12, 31, 9, 0), LocalDateTime.of(2029, 12, 31, 9, 30));
        ReflectionTestUtils.setField(past, "id", 11L);
        when(slotRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(past));

        assertThatThrownBy(() -> service.book(11L, form(null))).isInstanceOf(SlotAlreadyBookedException.class);
    }

    @Test
    void book_unknownSlot_throwsNotFound() {
        when(slotRepository.findByIdForUpdate(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.book(404L, form(null))).isInstanceOf(SlotNotFoundException.class);
    }

    @Test
    void cancel_freesTheSlot() {
        slot.markBooked();
        Appointment appointment = new Appointment(slot, "Jane", "jane@example.com", null, LocalDateTime.of(2030, 1, 1, 9, 0));
        ReflectionTestUtils.setField(appointment, "id", 99L);
        when(appointmentRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(appointment));
        when(slotRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(slot));

        service.cancel(99L);

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(appointment.getCancelledAt()).isNotNull();
        assertThat(slot.getStatus()).isEqualTo(SlotStatus.AVAILABLE);
    }

    @Test
    void cancel_secondTime_isRejectedWithoutTouchingTheSlot() {
        Appointment appointment = new Appointment(slot, "Jane", "jane@example.com", null, LocalDateTime.of(2030, 1, 1, 9, 0));
        appointment.cancel(LocalDateTime.of(2030, 1, 1, 10, 0));
        ReflectionTestUtils.setField(appointment, "id", 99L);
        when(appointmentRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> service.cancel(99L)).isInstanceOf(AppointmentNotCancellableException.class);
        verifyNoInteractions(slotRepository);
    }

    @Test
    void cancel_unknownAppointment_throwsNotFound() {
        when(appointmentRepository.findByIdForUpdate(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(5L)).isInstanceOf(AppointmentNotFoundException.class);
    }
}
