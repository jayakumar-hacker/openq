package com.openq.dto;

import java.time.LocalDateTime;

import com.openq.entity.Slot;

/** JSON shape of a slot for the read-only REST endpoint (entities are never serialised directly). */
public record SlotDto(Long slotId, LocalDateTime startTime, LocalDateTime endTime) {

    public static SlotDto from(Slot slot) {
        return new SlotDto(slot.getId(), slot.getStartTime(), slot.getEndTime());
    }
}
