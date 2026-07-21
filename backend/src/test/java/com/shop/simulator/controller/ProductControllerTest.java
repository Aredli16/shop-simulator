package com.shop.simulator.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.simulator.domain.Product;
import com.shop.simulator.domain.RestockOrder;
import com.shop.simulator.dto.RestockRequest;
import com.shop.simulator.exception.ResourceNotFoundException;
import com.shop.simulator.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    @Autowired
    private ObjectMapper objectMapper;

    private Product product;

    @BeforeEach
    void setUp() {
        product = new Product(1L, "Cola", "Drink", 0.5, 1.5, 10, 5);
    }

    @Test
    void getAllProducts_shouldReturnList() throws Exception {
        when(productService.getAllProducts()).thenReturn(List.of(product));

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Cola"));
    }

    @Test
    void getProductById_whenExists_shouldReturnProduct() throws Exception {
        when(productService.getProductById(1L)).thenReturn(product);

        mockMvc.perform(get("/api/v1/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Cola"));
    }

    @Test
    void getProductById_whenNotFound_shouldReturn404() throws Exception {
        when(productService.getProductById(2L)).thenThrow(new ResourceNotFoundException("Not found"));

        mockMvc.perform(get("/api/v1/products/2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Not found"));
    }

    @Test
    void createProduct_shouldReturnCreated() throws Exception {
        when(productService.createProduct(any(Product.class))).thenReturn(product);

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(product)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Cola"));
    }

    @Test
    void updateProduct_shouldReturnUpdated() throws Exception {
        when(productService.updateProduct(eq(1L), any(Product.class))).thenReturn(product);

        mockMvc.perform(put("/api/v1/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(product)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void deleteProduct_shouldReturnNoContent() throws Exception {
        doNothing().when(productService).deleteProduct(1L);

        mockMvc.perform(delete("/api/v1/products/1"))
                .andExpect(status().isNoContent());

        verify(productService, times(1)).deleteProduct(1L);
    }

    @Test
    void triggerRestock_shouldReturnCreated() throws Exception {
        RestockRequest request = new RestockRequest(1L, 20);
        RestockOrder order = new RestockOrder(100L, null, product, 20, 10.0, "En cours");
        when(productService.triggerRestock(1L, 20)).thenReturn(order);

        mockMvc.perform(post("/api/v1/products/restock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.status").value("En cours"));
    }

    @Test
    void deliverRestock_shouldReturnOk() throws Exception {
        RestockOrder order = new RestockOrder(100L, null, product, 20, 10.0, "Livré");
        when(productService.deliverRestock(100L)).thenReturn(order);

        mockMvc.perform(post("/api/v1/products/restock/100/deliver"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.status").value("Livré"));
    }

    @Test
    void getAllRestockOrders_shouldReturnList() throws Exception {
        when(productService.getAllRestockOrders()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/products/restock/orders"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }
}
