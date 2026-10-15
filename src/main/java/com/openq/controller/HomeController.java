package com.openq.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.openq.service.DoctorService;
import com.openq.service.SlotService;

/** Patient-facing browse pages: home, doctor directory, a doctor's open slots. */
@Controller
public class HomeController {

    private final DoctorService doctorService;
    private final SlotService slotService;

    public HomeController(DoctorService doctorService, SlotService slotService) {
        this.doctorService = doctorService;
        this.slotService = slotService;
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/doctors")
    public String doctors(Model model) {
        model.addAttribute("cards", doctorService.listDirectory());
        return "doctors";
    }

    @GetMapping("/doctors/{doctorId}/slots")
    public String doctorSlots(@PathVariable Long doctorId, Model model) {
        model.addAttribute("doctor", doctorService.getActiveDoctor(doctorId));
        model.addAttribute("slotsByDay", slotService.listAvailableSlotsByDay(doctorId));
        return "doctor-slots";
    }
}
