package com.shop.simulator.repository;

import com.shop.simulator.domain.RestockOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RestockOrderRepository extends JpaRepository<RestockOrder, Long> {
    List<RestockOrder> findByStatus(String status);
}
