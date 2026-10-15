package com.openq.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.openq.dto.BookingConfirmation;
import com.openq.dto.BookingForm;
import com.openq.dto.DoctorCard;
import com.openq.entity.Appointment;
import com.openq.entity.Doctor;
import com.openq.entity.Slot;
import com.openq.exception.AppointmentNotFoundException;
import com.openq.exception.DoctorNotFoundException;
import com.openq.exception.SlotAlreadyBookedException;
import com.openq.service.AppointmentService;
import com.openq.service.DoctorService;
import com.openq.service.SlotService;

/** Web-layer slice test: real controllers, advice and Thymeleaf templates; mocked services; no database. */
@WebMvcTest({HomeController.class, BookingController.class, AdminController.class, ApiController.class})
class WebLayerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DoctorService doctorService;
    @MockitoBean
    private SlotService slotService;
    @MockitoBean
    private AppointmentService appointmentService;

    private Doctor doctor;
    private Slot slot;

    @BeforeEach
    void setUp() {
        doctor = new Doctor("Dr. Test", "General", "MBBS", "Test Clinic", "t@example.com");
        ReflectionTestUtils.setField(doctor, "id", 1L);
        slot = new Slot(doctor, LocalDateTime.of(2030, 1, 2, 9, 0), LocalDateTime.of(2030, 1, 2, 9, 30));
        ReflectionTestUtils.setField(slot, "id", 10L);
    }

    private BookingConfirmation confirmation() {
        Appointment a = new Appointment(slot, "Jane", "jane@example.com", null, LocalDateTime.of(2030, 1, 1, 8, 0));
        ReflectionTestUtils.setField(a, "id", 5L);
        return BookingConfirmation.from(a);
    }

    @Test
    void home_rendersBrand() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(view().name("index"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Find a doctor")));
    }

    @Test
    void doctorDirectory_rendersCards() throws Exception {
        when(doctorService.listDirectory()).thenReturn(List.of(new DoctorCard(doctor, 3)));

        mvc.perform(get("/doctors")).andExpect(status().isOk()).andExpect(view().name("doctors"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Dr. Test")));
    }

    @Test
    void doctorSlots_rendersGroupedSlots() throws Exception {
        when(doctorService.getActiveDoctor(1L)).thenReturn(doctor);
        when(slotService.listAvailableSlotsByDay(1L)).thenReturn(Map.of(slot.getStartTime().toLocalDate(), List.of(slot)));

        mvc.perform(get("/doctors/1/slots")).andExpect(status().isOk()).andExpect(view().name("doctor-slots"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/slots/10/book")));
    }

    @Test
    void bookingForm_isShownForBookableSlot() throws Exception {
        when(slotService.getBookableSlot(10L)).thenReturn(slot);

        mvc.perform(get("/slots/10/book")).andExpect(status().isOk()).andExpect(view().name("booking-form"));
    }

    @Test
    void book_withInvalidInput_redisplaysFormWithFieldErrors() throws Exception {
        when(slotService.getBookableSlot(10L)).thenReturn(slot);

        mvc.perform(post("/slots/10/book").param("patientName", " ").param("patientEmail", "nope"))
                .andExpect(status().isOk())
                .andExpect(view().name("booking-form"))
                .andExpect(model().attributeHasFieldErrors("bookingForm", "patientName", "patientEmail"));
        verify(appointmentService, never()).book(any(), any());
    }

    @Test
    void book_withValidInput_redirectsToConfirmation() throws Exception {
        when(appointmentService.book(eq(10L), any(BookingForm.class))).thenReturn(confirmation());

        mvc.perform(post("/slots/10/book").param("patientName", "Jane").param("patientEmail", "jane@example.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/appointments/5/confirmation"));
    }

    @Test
    void book_whenSlotJustTaken_returns409ErrorPage() throws Exception {
        when(appointmentService.book(eq(10L), any(BookingForm.class))).thenThrow(new SlotAlreadyBookedException(10L));

        mvc.perform(post("/slots/10/book").param("patientName", "Jane").param("patientEmail", "jane@example.com"))
                .andExpect(status().isConflict())
                .andExpect(view().name("error"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("just been taken")));
    }

    @Test
    void confirmation_rendersAppointmentDetails() throws Exception {
        when(appointmentService.getConfirmation(5L)).thenReturn(confirmation());

        mvc.perform(get("/appointments/5/confirmation")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("#5")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Dr. Test")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Jane")));
    }

    @Test
    void confirmation_forUnknownAppointment_returns404() throws Exception {
        when(appointmentService.getConfirmation(404L)).thenThrow(new AppointmentNotFoundException(404L));

        mvc.perform(get("/appointments/404/confirmation")).andExpect(status().isNotFound()).andExpect(view().name("error"));
    }

    @Test
    void adminDashboard_rendersWithAppointmentRow() throws Exception {
        Appointment a = new Appointment(slot, "Jane", "jane@example.com", "12345678", LocalDateTime.of(2030, 1, 1, 8, 0));
        ReflectionTestUtils.setField(a, "id", 5L);
        when(appointmentService.search(null, null)).thenReturn(List.of(a));
        when(doctorService.listActive()).thenReturn(List.of(doctor));

        mvc.perform(get("/admin")).andExpect(status().isOk()).andExpect(view().name("admin-dashboard"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/admin/appointments/5/cancel")));
    }

    @Test
    void adminCancel_redirectsBackToDashboard() throws Exception {
        mvc.perform(post("/admin/appointments/5/cancel")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));
        verify(appointmentService).cancel(5L);
    }

    @Test
    void adminCancel_unknownAppointment_returns404() throws Exception {
        doThrow(new AppointmentNotFoundException(5L)).when(appointmentService).cancel(5L);

        mvc.perform(post("/admin/appointments/5/cancel")).andExpect(status().isNotFound());
    }

    @Test
    void cancelViaGet_isNotAllowed() throws Exception {
        mvc.perform(get("/admin/appointments/5/cancel")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void unknownPage_returnsFriendly404() throws Exception {
        mvc.perform(get("/no-such-page")).andExpect(status().isNotFound()).andExpect(view().name("error"));
    }

    @Test
    void invalidPathVariable_returns400() throws Exception {
        mvc.perform(get("/doctors/abc/slots")).andExpect(status().isBadRequest()).andExpect(view().name("error"));
    }

    @Test
    void api_returnsJsonSlots() throws Exception {
        when(slotService.listAvailableSlots(1L)).thenReturn(List.of(slot));

        mvc.perform(get("/api/doctors/1/slots").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"slotId\":10")));
    }

    @Test
    void api_unknownDoctor_returns404ProblemJson() throws Exception {
        when(slotService.listAvailableSlots(7L)).thenThrow(new DoctorNotFoundException(7L));

        mvc.perform(get("/api/doctors/7/slots").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
