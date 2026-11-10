package com.cvp.maintenance.adapter.inbound.rest;

import com.cvp.maintenance.application.CommandService;
import com.cvp.maintenance.application.MaintenanceService;
import com.cvp.maintenance.application.ReminderQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReminderController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReminderControllerValidationTest {
    @Autowired MockMvc mvc;
    @MockBean MaintenanceService maintenance;
    @MockBean CommandService commands;
    @MockBean ReminderQueryService queries;

    @Test
    void legacyRequestRemainsAnArrayRequest() throws Exception {
        when(queries.list(anyString(), isNull(), isNull(), isNull(), isNull())).thenReturn(List.of());
        mvc.perform(get("/api/v1/vehicles/VIN-1/maintenance-reminders"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(queries).list("VIN-1", null, null, null, null);
    }

    @Test
    void invalidEnumAndPaginationParametersReturnProblemDetails() throws Exception {
        mvc.perform(get("/api/v1/vehicles/VIN-1/maintenance-reminders?status=UNKNOWN"))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mvc.perform(get("/api/v1/vehicles/VIN-1/maintenance-reminders?severity=UNKNOWN"))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mvc.perform(get("/api/v1/vehicles/VIN-1/maintenance-reminders?page=not-a-number"))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        when(queries.list(eq("VIN-1"), isNull(), isNull(), eq(0), eq(101))).thenThrow(new IllegalArgumentException("size must not exceed 100"));
        mvc.perform(get("/api/v1/vehicles/VIN-1/maintenance-reminders?page=0&size=101"))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }
}
