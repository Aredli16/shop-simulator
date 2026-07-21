package com.shop.simulator.controller;

import com.shop.simulator.dto.SimulationStatus;
import com.shop.simulator.service.SimulationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SimulationController.class)
class SimulationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SimulationService simulationService;

    @Test
    void getStatus_shouldReturnStatus() throws Exception {
        SimulationStatus status = new SimulationStatus(true, 150.0, 1050.0, 5, 2);
        when(simulationService.getStatus()).thenReturn(status);

        mockMvc.perform(get("/api/v1/simulation/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.totalRevenue").value(150.0))
                .andExpect(jsonPath("$.currentCapital").value(1050.0))
                .andExpect(jsonPath("$.transactionCount").value(5))
                .andExpect(jsonPath("$.restockCount").value(2));
    }

    @Test
    void startSimulation_shouldInvokeAndReturnOk() throws Exception {
        doNothing().when(simulationService).startSimulation();

        mockMvc.perform(post("/api/v1/simulation/start"))
                .andExpect(status().isOk());

        verify(simulationService, times(1)).startSimulation();
    }

    @Test
    void stopSimulation_shouldInvokeAndReturnOk() throws Exception {
        doNothing().when(simulationService).stopSimulation();

        mockMvc.perform(post("/api/v1/simulation/stop"))
                .andExpect(status().isOk());

        verify(simulationService, times(1)).stopSimulation();
    }

    @Test
    void resetSimulation_shouldInvokeAndReturnOk() throws Exception {
        doNothing().when(simulationService).resetSimulation();

        mockMvc.perform(post("/api/v1/simulation/reset"))
                .andExpect(status().isOk());

        verify(simulationService, times(1)).resetSimulation();
    }

    @Test
    void tick_shouldInvokeAndReturnOk() throws Exception {
        doNothing().when(simulationService).tick();

        mockMvc.perform(post("/api/v1/simulation/tick"))
                .andExpect(status().isOk());

        verify(simulationService, times(1)).tick();
    }
}
