package com.shop.simulator.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.simulator.domain.Transaction;
import com.shop.simulator.dto.CheckoutRequest;
import com.shop.simulator.exception.SoldeInsuffisantException;
import com.shop.simulator.exception.StockInsuffisantException;
import com.shop.simulator.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransactionService transactionService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getAllTransactions_shouldReturnList() throws Exception {
        Transaction tx = new Transaction(1L, null, List.of(), 10.0, "Bob PNJ");
        when(transactionService.getAllTransactions()).thenReturn(List.of(tx));

        mockMvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].total").value(10.0));
    }

    @Test
    void checkout_whenNominal_shouldReturnCreated() throws Exception {
        CheckoutRequest request = new CheckoutRequest(1L);
        Transaction tx = new Transaction(100L, null, List.of(), 10.0, "Bob PNJ");
        when(transactionService.checkout(1L)).thenReturn(tx);

        mockMvc.perform(post("/api/v1/transactions/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.total").value(10.0));
    }

    @Test
    void checkout_whenSoldeInsuffisant_shouldReturn400() throws Exception {
        CheckoutRequest request = new CheckoutRequest(1L);
        when(transactionService.checkout(1L)).thenThrow(new SoldeInsuffisantException("Insufficient budget"));

        mockMvc.perform(post("/api/v1/transactions/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Insufficient budget"));
    }

    @Test
    void checkout_whenStockInsuffisant_shouldReturn400() throws Exception {
        CheckoutRequest request = new CheckoutRequest(1L);
        when(transactionService.checkout(1L)).thenThrow(new StockInsuffisantException("Insufficient stock"));

        mockMvc.perform(post("/api/v1/transactions/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Insufficient stock"));
    }
}
