package com.shop.simulator.config;

import com.shop.simulator.repository.ProductRepository;
import com.shop.simulator.service.SimulationService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DatabaseSeeder implements CommandLineRunner {

    private final SimulationService simulationService;
    private final ProductRepository productRepository;

    public DatabaseSeeder(SimulationService simulationService, ProductRepository productRepository) {
        this.simulationService = simulationService;
        this.productRepository = productRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        if (productRepository.count() == 0) {
            simulationService.seedDefaultProducts();
        }
    }
}
