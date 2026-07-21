package com.shop.simulator.service;

import com.shop.simulator.domain.Product;
import com.shop.simulator.domain.RestockOrder;
import com.shop.simulator.exception.ResourceNotFoundException;
import com.shop.simulator.repository.ProductRepository;
import com.shop.simulator.repository.RestockOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private RestockOrderRepository restockOrderRepository;

    @InjectMocks
    private ProductService productService;

    private Product product;

    @BeforeEach
    void setUp() {
        product = new Product(1L, "Cola", "Drink", 0.5, 1.5, 10, 5);
    }

    @Test
    void getAllProducts_shouldReturnList() {
        when(productRepository.findAll()).thenReturn(List.of(product));
        List<Product> products = productService.getAllProducts();
        assertThat(products).hasSize(1);
        assertThat(products.get(0).getName()).isEqualTo("Cola");
    }

    @Test
    void getProductById_whenExists_shouldReturnProduct() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        Product found = productService.getProductById(1L);
        assertThat(found.getName()).isEqualTo("Cola");
    }

    @Test
    void getProductById_whenDoesNotExist_shouldThrowException() {
        when(productRepository.findById(2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> productService.getProductById(2L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product with ID 2 not found");
    }

    @Test
    void createProduct_shouldSaveAndReturn() {
        when(productRepository.save(product)).thenReturn(product);
        Product saved = productService.createProduct(product);
        assertThat(saved).isNotNull();
        verify(productRepository, times(1)).save(product);
    }

    @Test
    void updateProduct_shouldModifyAndSave() {
        Product details = new Product(null, "New Cola", "Soda", 0.6, 1.8, 15, 6);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Product updated = productService.updateProduct(1L, details);

        assertThat(updated.getName()).isEqualTo("New Cola");
        assertThat(updated.getCategory()).isEqualTo("Soda");
        assertThat(updated.getPurchasePrice()).isEqualTo(0.6);
        assertThat(updated.getSellingPrice()).isEqualTo(1.8);
        assertThat(updated.getStockQuantity()).isEqualTo(15);
        assertThat(updated.getRestockThreshold()).isEqualTo(6);
    }

    @Test
    void deleteProduct_shouldInvokeRepositoryDelete() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        doNothing().when(productRepository).delete(product);

        productService.deleteProduct(1L);

        verify(productRepository, times(1)).delete(product);
    }

    @Test
    void triggerRestock_shouldCreateAndSaveOrder() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(restockOrderRepository.save(any(RestockOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        RestockOrder order = productService.triggerRestock(1L, 20);

        assertThat(order).isNotNull();
        assertThat(order.getProduct().getName()).isEqualTo("Cola");
        assertThat(order.getQuantityOrdered()).isEqualTo(20);
        assertThat(order.getTotalCost()).isEqualTo(10.0); // 0.5 * 20
        assertThat(order.getStatus()).isEqualTo("En cours");
    }

    @Test
    void deliverRestock_whenPending_shouldUpdateStockAndStatus() {
        RestockOrder order = new RestockOrder(10L, null, product, 30, 15.0, "En cours");
        when(restockOrderRepository.findById(10L)).thenReturn(Optional.of(order));
        when(productRepository.save(product)).thenReturn(product);
        when(restockOrderRepository.save(order)).thenReturn(order);

        RestockOrder delivered = productService.deliverRestock(10L);

        assertThat(delivered.getStatus()).isEqualTo("Livré");
        assertThat(product.getStockQuantity()).isEqualTo(40); // 10 initial + 30 restocked
        verify(productRepository, times(1)).save(product);
    }

    @Test
    void deliverRestock_whenAlreadyDelivered_shouldDoNothing() {
        RestockOrder order = new RestockOrder(10L, null, product, 30, 15.0, "Livré");
        when(restockOrderRepository.findById(10L)).thenReturn(Optional.of(order));

        RestockOrder delivered = productService.deliverRestock(10L);

        assertThat(delivered.getStatus()).isEqualTo("Livré");
        assertThat(product.getStockQuantity()).isEqualTo(10); // unchanged
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void getAllRestockOrders_shouldReturnList() {
        when(restockOrderRepository.findAll()).thenReturn(Collections.emptyList());
        List<RestockOrder> orders = productService.getAllRestockOrders();
        assertThat(orders).isEmpty();
    }

    @Test
    void checkAndTriggerAutoRestock_whenStockIsAboveThreshold_shouldNotTrigger() {
        product.setStockQuantity(6); // threshold is 5
        productService.checkAndTriggerAutoRestock(product);
        verify(restockOrderRepository, never()).save(any(RestockOrder.class));
    }

    @Test
    void checkAndTriggerAutoRestock_whenStockIsBelowThresholdAndNoPendingOrder_shouldTrigger() {
        product.setStockQuantity(4); // threshold is 5
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(restockOrderRepository.findByStatus("En cours")).thenReturn(new ArrayList<>());
        when(restockOrderRepository.save(any(RestockOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        productService.checkAndTriggerAutoRestock(product);

        verify(restockOrderRepository, times(1)).save(any(RestockOrder.class));
    }

    @Test
    void checkAndTriggerAutoRestock_whenStockIsBelowThresholdAndAlreadyPending_shouldNotTrigger() {
        product.setStockQuantity(4); // threshold is 5
        RestockOrder pending = new RestockOrder(100L, null, product, 50, 25.0, "En cours");
        
        when(restockOrderRepository.findByStatus("En cours")).thenReturn(List.of(pending));

        productService.checkAndTriggerAutoRestock(product);

        verify(restockOrderRepository, never()).save(any(RestockOrder.class));
    }
}
