package com.openq.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import com.openq.dto.BookingConfirmation;
import com.openq.dto.BookingForm;
import com.openq.entity.Slot;
import com.openq.service.AppointmentService;
import com.openq.service.SlotService;

import jakarta.validation.Valid;

/** Patient booking flow: form -> POST -> redirect -> confirmation (Post/Redirect/Get). */
@Controller
public class BookingController {

    private final SlotService slotService;
    private final AppointmentService appointmentService;

    public BookingController(SlotService slotService, AppointmentService appointmentService) {
        this.slotService = slotService;
        this.appointmentService = appointmentService;
    }

    @GetMapping("/slots/{slotId}/book")
    public String bookingForm(@PathVariable Long slotId, Model model) {
        Slot slot = slotService.getBookableSlot(slotId);
        model.addAttribute("slot", slot);
        model.addAttribute("bookingForm", new BookingForm());
        return "booking-form";
    }

    @PostMapping("/slots/{slotId}/book")
    public String book(@PathVariable Long slotId,
                       @Valid @ModelAttribute("bookingForm") BookingForm form,
                       BindingResult bindingResult,
                       Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("slot", slotService.getBookableSlot(slotId));
            return "booking-form";
        }
        BookingConfirmation confirmation = appointmentService.book(slotId, form);
        return "redirect:/appointments/" + confirmation.getAppointmentId() + "/confirmation";
    }

    @GetMapping("/appointments/{appointmentId}/confirmation")
    public String confirmation(@PathVariable Long appointmentId, Model model) {
        model.addAttribute("confirmation", appointmentService.getConfirmation(appointmentId));
        return "confirmation";
    }
}
