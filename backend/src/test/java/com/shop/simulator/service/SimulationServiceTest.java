package com.shop.simulator.service;

import com.shop.simulator.domain.Product;
import com.shop.simulator.domain.RestockOrder;
import com.shop.simulator.domain.Transaction;
import com.shop.simulator.dto.SimulationStatus;
import com.shop.simulator.repository.CustomerRepository;
import com.shop.simulator.repository.ProductRepository;
import com.shop.simulator.repository.RestockOrderRepository;
import com.shop.simulator.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SimulationServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private RestockOrderRepository restockOrderRepository;

    @InjectMocks
    private SimulationService simulationService;

    @Test
    void simulationControls_shouldModifyActiveState() {
        assertThat(simulationService.isActive()).isFalse();

        simulationService.startSimulation();
        assertThat(simulationService.isActive()).isTrue();

        simulationService.stopSimulation();
        assertThat(simulationService.isActive()).isFalse();
    }

    @Test
    void resetSimulation_shouldDeleteAllAndSeed() {
        doNothing().when(customerRepository).deleteAll();
        doNothing().when(transactionRepository).deleteAll();
        doNothing().when(restockOrderRepository).deleteAll();
        doNothing().when(productRepository).deleteAll();
        when(productRepository.save(any(Product.class))).thenReturn(new Product());

        simulationService.resetSimulation();

        assertThat(simulationService.isActive()).isFalse();
        verify(customerRepository, times(1)).deleteAll();
        verify(transactionRepository, times(1)).deleteAll();
        verify(restockOrderRepository, times(1)).deleteAll();
        verify(productRepository, times(1)).deleteAll();
        // 4 products seeded
        verify(productRepository, times(4)).save(any(Product.class));
    }

    @Test
    void getStatus_shouldCalculateKPIs() {
        Transaction tx1 = new Transaction(1L, null, List.of(), 50.0, "Alice");
        Transaction tx2 = new Transaction(2L, null, List.of(), 25.5, "Bob");
        when(transactionRepository.findAll()).thenReturn(List.of(tx1, tx2));

        Product p = new Product(1L, "Cola", "Drink", 0.5, 1.5, 10, 5);
        RestockOrder order = new RestockOrder(1L, null, p, 50, 25.0, "Livré");
        when(restockOrderRepository.findAll()).thenReturn(List.of(order));

        SimulationStatus status = simulationService.getStatus();

        assertThat(status.active()).isFalse();
        assertThat(status.totalRevenue()).isEqualTo(75.50); // 50.0 + 25.5
        // Capital = 1000 (initial) + 75.50 (revenue) - 25.00 (restock) = 1050.50
        assertThat(status.currentCapital()).isEqualTo(1050.50);
        assertThat(status.transactionCount()).isEqualTo(2);
        assertThat(status.restockCount()).isEqualTo(1);
    }

    @Test
    void tick_shouldDoNothingForNow() {
        // Just verify it runs without throwing exception
        simulationService.tick();
    }
}
