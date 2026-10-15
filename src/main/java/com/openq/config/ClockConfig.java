package com.openq.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the clinic-local clock. All appointment times are clinic-local wall-clock
 * ({@code LocalDateTime}); this bean defines which zone "now" is read in.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clinicClock(@Value("${openq.clinic-zone}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
