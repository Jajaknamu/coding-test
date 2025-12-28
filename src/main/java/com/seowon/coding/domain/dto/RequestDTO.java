package com.seowon.coding.domain.dto;

import com.seowon.coding.domain.model.Product;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor
public class RequestDTO {

    private String customerName;
    private String customerEmail;
    private List<ProductDTO> products;

    /**
     * 중첩되서 만든 이유
     * 따로 클래스 파일로 빼기엔 order생성 할때만 쓸것같아서 가독성때문
     */
    @Getter
    @NoArgsConstructor
    public static class ProductDTO {
        private Long productId;
        private int quantity;
    }
}
