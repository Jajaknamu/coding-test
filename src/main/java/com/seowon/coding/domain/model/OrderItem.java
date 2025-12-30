package com.seowon.coding.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;
    
    private int quantity;
    
    private BigDecimal price; // Price at the time of order
    
    // Business logic
    public BigDecimal getSubtotal() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }

    //주문 시 알맞게 주문 수량 체크했는지 확인 로직(-5 이런건 음수는 안됨)
    public int checkQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("잘못된 수량(1개 이상필수) " + quantity);
        }
        return quantity;
    }

    //OrderItem까지 만드는걸로
    public static OrderItem createOrderItem(Order order, Product product, int quantity) {

        OrderItem orderItem = new OrderItem();//영수증 객체 생성

        //1. 검사 먼저
        orderItem.checkQuantity(quantity);
        //2. 이상없으면 db에 재고 차감
        product.decreaseStock(quantity);
        //3.이제 객체에 저장해주기
        orderItem.setOrder(order);
        orderItem.setProduct(product);
        orderItem.setQuantity(quantity); // 체크 후 수량 저장
        orderItem.setPrice(product.getPrice()); //현재 가격도 추가

        return orderItem;
    }

}