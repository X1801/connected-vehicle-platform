package com.cvp.maintenance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MaintenanceReminderApplication {

    public static void main(String[] args) {
        SpringApplication.run(MaintenanceReminderApplication.class, args);
    }
}
