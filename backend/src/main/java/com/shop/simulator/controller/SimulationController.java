package com.shop.simulator.controller;

import com.shop.simulator.dto.SimulationStatus;
import com.shop.simulator.service.SimulationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/simulation")
@CrossOrigin(origins = "*")
public class SimulationController {

    private final SimulationService simulationService;

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @GetMapping("/status")
    public ResponseEntity<SimulationStatus> getStatus() {
        return ResponseEntity.ok(simulationService.getStatus());
    }

    @PostMapping("/start")
    public ResponseEntity<Void> startSimulation() {
        simulationService.startSimulation();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/stop")
    public ResponseEntity<Void> stopSimulation() {
        simulationService.stopSimulation();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset")
    public ResponseEntity<Void> resetSimulation() {
        simulationService.resetSimulation();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/tick")
    public ResponseEntity<Void> tick() {
        simulationService.tick();
        return ResponseEntity.ok().build();
    }
}
