package com.grindandtrain.common.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the application {@link java.time.Clock}, so tests can control the current time.
 *
 * @author Dheeraj_Edupuganti
 */
@Configuration
public class ClockConfig {

    /** Injected wherever "now" matters, so tests can fix the time. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
