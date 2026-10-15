package com.openq.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.openq.dto.SlotDto;
import com.openq.service.SlotService;

/** Small read-only JSON endpoint: useful for scripts/smoke tests and to see live availability. */
@RestController
@RequestMapping("/api")
public class ApiController {

    private final SlotService slotService;

    public ApiController(SlotService slotService) {
        this.slotService = slotService;
    }

    @GetMapping("/doctors/{doctorId}/slots")
    public List<SlotDto> availableSlots(@PathVariable Long doctorId) {
        return slotService.listAvailableSlots(doctorId).stream().map(SlotDto::from).toList();
    }
}
