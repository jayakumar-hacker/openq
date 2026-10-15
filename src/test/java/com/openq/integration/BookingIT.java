package com.openq.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;

import com.openq.dto.BookingConfirmation;
import com.openq.dto.BookingForm;
import com.openq.entity.AppointmentStatus;
import com.openq.entity.Doctor;
import com.openq.entity.Slot;
import com.openq.entity.SlotStatus;
import com.openq.exception.AppointmentNotCancellableException;
import com.openq.exception.SlotAlreadyBookedException;
import com.openq.repository.AppointmentRepository;
import com.openq.repository.DoctorRepository;
import com.openq.repository.SlotRepository;
import com.openq.service.AppointmentService;

import javax.sql.DataSource;

/**
 * INTEGRATION tests against a REAL PostgreSQL (see README: "Running the integration tests").
 * They run the real schema.sql / data.sql, real transactions, real row locks and the real partial unique index.
 * Not transactional on purpose: concurrency tests need each thread's transaction to really commit.
 */
@SpringBootTest
@ActiveProfiles("test")
class BookingIT {

    @Autowired
    private AppointmentService appointmentService;
    @Autowired
    private DoctorRepository doctorRepository;
    @Autowired
    private SlotRepository slotRepository;
    @Autowired
    private AppointmentRepository appointmentRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DataSource dataSource;

    private Slot slot;

    @BeforeEach
    void createFreshSlot() {
        Doctor doctor = doctorRepository.save(new Doctor("IT Doctor", "Testing", null, "IT Clinic",
                "it-" + UUID.randomUUID() + "@openq.test"));
        LocalDateTime start = LocalDateTime.now().plusDays(30).withNano(0);
        slot = slotRepository.save(new Slot(doctor, start, start.plusMinutes(30)));
    }

    private static BookingForm form(String name) {
        BookingForm f = new BookingForm();
        f.setPatientName(name);
        f.setPatientEmail(name.toLowerCase().replace(' ', '.') + "@example.com");
        return f;
    }

    private long bookedCount() {
        return appointmentRepository.countBySlotIdAndStatus(slot.getId(), AppointmentStatus.BOOKED);
    }

    private SlotStatus slotStatus() {
        return slotRepository.findById(slot.getId()).orElseThrow().getStatus();
    }

    @Test
    void book_persistsAppointmentAndMarksSlotBooked() {
        BookingConfirmation c = appointmentService.book(slot.getId(), form("Jane Patient"));

        assertThat(c.getAppointmentId()).isNotNull();
        assertThat(c.getDoctorName()).isEqualTo("IT Doctor");
        assertThat(slotStatus()).isEqualTo(SlotStatus.BOOKED);
        assertThat(bookedCount()).isEqualTo(1);
    }

    @Test
    void book_sameSlotTwice_secondIsRejected() {
        appointmentService.book(slot.getId(), form("First Patient"));

        assertThatThrownBy(() -> appointmentService.book(slot.getId(), form("Second Patient")))
                .isInstanceOf(SlotAlreadyBookedException.class);
        assertThat(bookedCount()).isEqualTo(1);
    }

    @Test
    void cancel_freesSlot_andSlotCanBeBookedAgain() {
        BookingConfirmation first = appointmentService.book(slot.getId(), form("First Patient"));

        appointmentService.cancel(first.getAppointmentId());
        assertThat(slotStatus()).isEqualTo(SlotStatus.AVAILABLE);
        assertThat(bookedCount()).isZero();

        BookingConfirmation second = appointmentService.book(slot.getId(), form("Second Patient"));
        assertThat(second.getAppointmentId()).isNotEqualTo(first.getAppointmentId());
        assertThat(bookedCount()).isEqualTo(1);
        // History is kept: one CANCELLED and one BOOKED row for the slot.
        assertThat(appointmentRepository.countBySlotIdAndStatus(slot.getId(), AppointmentStatus.CANCELLED)).isEqualTo(1);
    }

    @Test
    void cancel_twice_secondIsRejected() {
        BookingConfirmation c = appointmentService.book(slot.getId(), form("Jane Patient"));
        appointmentService.cancel(c.getAppointmentId());

        assertThatThrownBy(() -> appointmentService.cancel(c.getAppointmentId()))
                .isInstanceOf(AppointmentNotCancellableException.class);
        assertThat(slotStatus()).isEqualTo(SlotStatus.AVAILABLE);
    }

    @Test
    void concurrentBookings_exactlyOneSucceeds() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                final String name = "Patient " + (char) ('A' + i);
                Callable<Boolean> task = () -> {
                    ready.countDown();
                    go.await();
                    try {
                        appointmentService.book(slot.getId(), form(name));
                        return true;
                    } catch (SlotAlreadyBookedException expectedLoser) {
                        return false;
                    }
                };
                results.add(pool.submit(task));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            int winners = 0;
            for (Future<Boolean> f : results) {
                if (f.get(30, TimeUnit.SECONDS)) {
                    winners++;
                }
            }
            assertThat(winners).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(bookedCount()).isEqualTo(1);
        assertThat(slotStatus()).isEqualTo(SlotStatus.BOOKED);
    }

    @Test
    void databaseIndex_blocksSecondBookedRow_evenWhenServiceIsBypassed() {
        String insert = "INSERT INTO appointment (slot_id, patient_name, patient_email, status, created_at) "
                + "VALUES (?, ?, ?, ?, now())";
        jdbc.update(insert, slot.getId(), "Raw One", "one@example.com", "BOOKED");

        assertThatThrownBy(() -> jdbc.update(insert, slot.getId(), "Raw Two", "two@example.com", "BOOKED"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_appointment_active_slot");

        // CANCELLED rows are outside the partial index: any number may coexist with one BOOKED row.
        jdbc.update("INSERT INTO appointment (slot_id, patient_name, patient_email, status, created_at, cancelled_at) "
                + "VALUES (?, 'Old', 'old@example.com', 'CANCELLED', now(), now())", slot.getId());
        assertThat(bookedCount()).isEqualTo(1);
    }

    @Test
    void seedScript_isIdempotent() {
        Long doctors = jdbc.queryForObject("SELECT count(*) FROM doctor", Long.class);
        Long slots = jdbc.queryForObject("SELECT count(*) FROM slot", Long.class);

        // Simulate an application restart: run both scripts again.
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql"), new ClassPathResource("data.sql"))
                .execute(dataSource);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM doctor", Long.class)).isEqualTo(doctors);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM slot", Long.class)).isEqualTo(slots);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM doctor WHERE email = 'asha.menon@openq.example'", Long.class)).isEqualTo(1L);
    }
}
