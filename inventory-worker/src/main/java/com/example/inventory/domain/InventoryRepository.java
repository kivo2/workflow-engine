package com.example.inventory.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

@Repository
public interface InventoryRepository extends JpaRepository<InventoryItem, String> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT i FROM InventoryItem i WHERE i.productId = :productId")
  Optional<InventoryItem> findByIdForUpdate(String productId);
}
