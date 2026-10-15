package com.openq.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.openq.entity.AppointmentStatus;
import com.openq.service.AppointmentService;
import com.openq.service.DoctorService;

/**
 * Demo admin dashboard. WARNING: there is NO authentication or authorization. The /admin URL is
 * only a UI simulation of a second role and must never be exposed with real patient data.
 */
@Controller
public class AdminController {

    private final AppointmentService appointmentService;
    private final DoctorService doctorService;

    public AdminController(AppointmentService appointmentService, DoctorService doctorService) {
        this.appointmentService = appointmentService;
        this.doctorService = doctorService;
    }

    @GetMapping("/admin")
    public String dashboard(@RequestParam(required = false) Long doctorId,
                            @RequestParam(required = false) AppointmentStatus status,
                            Model model) {
        model.addAttribute("appointments", appointmentService.search(doctorId, status));
        model.addAttribute("doctors", doctorService.listActive());
        model.addAttribute("statuses", AppointmentStatus.values());
        model.addAttribute("selectedDoctorId", doctorId);
        model.addAttribute("selectedStatus", status);
        return "admin-dashboard";
    }

    @PostMapping("/admin/appointments/{appointmentId}/cancel")
    public String cancel(@PathVariable Long appointmentId,
                         @RequestParam(required = false) Long doctorId,
                         @RequestParam(required = false) AppointmentStatus status,
                         RedirectAttributes redirect) {
        appointmentService.cancel(appointmentId);
        redirect.addFlashAttribute("message", "Appointment #" + appointmentId + " was cancelled and its slot is available again.");
        // Keep the admin's current filters after the redirect.
        if (doctorId != null) {
            redirect.addAttribute("doctorId", doctorId);
        }
        if (status != null) {
            redirect.addAttribute("status", status.name());
        }
        return "redirect:/admin";
    }
}
