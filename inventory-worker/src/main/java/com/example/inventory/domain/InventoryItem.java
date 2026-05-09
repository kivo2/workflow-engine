package com.example.inventory.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "inventory_item")
@Getter
@Setter
@NoArgsConstructor
public class InventoryItem {

  @Id
  @Column(name = "product_id", nullable = false)
  private String productId;

  @Column(name = "product_name", nullable = false)
  private String productName;

  @Column(name = "available_qty", nullable = false)
  private int availableQty;

  @Column(name = "reserved_qty", nullable = false)
  private int reservedQty;
}
