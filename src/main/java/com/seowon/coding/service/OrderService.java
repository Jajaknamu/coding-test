package com.seowon.coding.service;

import com.seowon.coding.domain.model.Order;
import com.seowon.coding.domain.model.OrderItem;
import com.seowon.coding.domain.model.ProcessingStatus;
import com.seowon.coding.domain.model.Product;
import com.seowon.coding.domain.repository.OrderRepository;
import com.seowon.coding.domain.repository.ProcessingStatusRepository;
import com.seowon.coding.domain.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ProcessingStatusRepository processingStatusRepository;
    
    @Transactional(readOnly = true)
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }
    
    @Transactional(readOnly = true)
    public Optional<Order> getOrderById(Long id) {
        return orderRepository.findById(id);
    }
    

    public Order updateOrder(Long id, Order order) {
        if (!orderRepository.existsById(id)) {
            throw new RuntimeException("Order not found with id: " + id);
        }
        order.setId(id);
        return orderRepository.save(order);
    }
    
    public void deleteOrder(Long id) {
        if (!orderRepository.existsById(id)) {
            throw new RuntimeException("Order not found with id: " + id);
        }
        orderRepository.deleteById(id);
    }



    @Transactional
    public Order placeOrder(String customerName, String customerEmail, List<Long> productIds, List<Integer> quantities) {
        // * 주어진 고객 정보로 새 Order를 생성
        // * 지정된 Product를 주문에 추가
        // * order 의 상태를 PENDING 으로 변경
        // * orderDate 를 현재시간으로 설정
        // * order 를 저장
        // * 각 Product 의 재고를 수정
        // * placeOrder 메소드의 시그니처는 변경하지 않은 채 구현하세요.

        //product의 id랑 수량을 저장할 list 필요
        List <OrderItem> orderItems = new ArrayList<>();

        for (int i=0;i<productIds.size();i++) {
            Long pid = productIds.get(i);
            Integer qty = quantities.get(i);

            //엔티티에 pid의 진짜 상품id를 꺼내옴.
            Product product = productRepository.findById(pid)
                    .orElseThrow(() -> new RuntimeException("맞는 상품id가 없음" + pid));

            //엔티티에 있는 재고랑 주문한 재고 차감해서 재고수량 맞추기
            product.setStockQuantity(product.getStockQuantity() - qty);

            //영수증에 넣을 상품id랑 수량임 -> 영수증 객체 1개 완성임
            OrderItem orderItem = new OrderItem();
            orderItem.setProduct(product);
            orderItem.setQuantity(qty);

            /* builder 사용하면 이런식 위에 set 코드랑 같은 결과임
            OrderItem item = OrderItem.builder()
                    .product(product)
                    .quantity(qty)
                    .build();*/

            //이 영수증들을 orderItems라는 list에 넣어줘서 여러개의 영수증을 저장하는거
            orderItems.add(orderItem);
        }
        Order order = Order.builder()
                .customerEmail(customerEmail)
                .customerName(customerName)
                .orderDate(LocalDateTime.now())
                .status(Order.OrderStatus.PENDING)
                .items(orderItems)
                .build();
        return orderRepository.save(order);
    }

    /**
     * TODO #4 (리펙토링): Service 에 몰린 도메인 로직을 도메인 객체 안으로 이동
     * - Repository 조회는 도메인 객체 밖에서 해결하여 의존 차단 합니다.
     * - #3 에서 추가한 도메인 메소드가 있을 경우 사용해도 됩니다.
     */
    public Order checkoutOrder(String customerName,
                               String customerEmail,
                               List<OrderProduct> orderProducts,
                               String couponCode) {
        if (customerName == null || customerEmail == null) {
            throw new IllegalArgumentException("customer info required");
        }
        if (orderProducts == null || orderProducts.isEmpty()) {
            throw new IllegalArgumentException("orderReqs invalid");
        }

        Order order = Order.builder()
                .customerName(customerName)
                .customerEmail(customerEmail)
                .status(Order.OrderStatus.PENDING)
                .orderDate(LocalDateTime.now())
                .items(new ArrayList<>())
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal subtotal = BigDecimal.ZERO;

        for (OrderProduct req : orderProducts) {
            Long pid = req.getProductId();
            int qty = req.getQuantity();


            //1. 상품 id로 db에 있는 상품을 찾아옴
            Product product = productRepository.findById(pid)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + pid));

            OrderItem item = OrderItem.createOrderItem(order, product, qty);

            order.addItem(item); //
            /* 위에 코드로 리팩토링함
            if (qty <= 0) {
                throw new IllegalArgumentException("quantity must be positive: " + qty);
            }

            if (product.getStockQuantity() < qty) {
                throw new IllegalStateException("insufficient stock for product " + pid);
            }


            OrderItem item = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .quantity(qty)
                    .price(product.getPrice())
                    .build();
            order.getItems().add(item);
            product.decreaseStock(qty); //db에 재고 차감
             subtotal = subtotal.add(product.getPrice().multiply(BigDecimal.valueOf(qty))); // 총 주믄 금액
            */
        }

        BigDecimal currentTotal = order.getTotalAmount();

        BigDecimal shipping = currentTotal.compareTo(new BigDecimal("100.00")) >= 0 ? BigDecimal.ZERO : new BigDecimal("5.00");
        BigDecimal discount = (couponCode != null && couponCode.startsWith("SALE")) ? new BigDecimal("10.00") : BigDecimal.ZERO;

        order.setTotalAmount(currentTotal.add(shipping).subtract(discount));
        order.setStatus(Order.OrderStatus.PROCESSING);
        return orderRepository.save(order);
    }

    /**
     * TODO #5: 코드 리뷰 - 장시간 작업과 진행률 저장의 트랜잭션 분리
     * - 시나리오: 일괄 배송 처리 중 진행률을 저장하여 다른 사용자가 조회 가능해야 함.
     * - 리뷰 포인트: proxy 및 transaction 분리, 예외 전파/롤백 범위, 가독성 등
     * - 상식적인 수준에서 요구사항(기획)을 가정하며 최대한 상세히 작성하세요.
     */
    @Transactional
    public void bulkShipOrdersParent(String jobId, List<Long> orderIds) {
        ///현재 작업중인 id를 불러와서 존재하다면 작업상태에 저장함. 만약 없다면 그 jobid값 저장해줌
        ProcessingStatus ps = processingStatusRepository.findByJobId(jobId)
                .orElseGet(() -> processingStatusRepository.save(ProcessingStatus.builder().jobId(jobId).build()));
        ps.markRunning(orderIds == null ? 0 : orderIds.size()); ///orderIds의 값이 null이면 0을 넣어주고, 아니라면 사이즈값 넣어주라는거임.
        processingStatusRepository.save(ps); ///마지막으로 작업상태 저장해줌.

        /// 이 중간 저장 로직은 따로 메서드로 빼는게 나을거같음. 하나의 작업으로 묶어버리면 중간 내용은 커밋될때까지 확인할수없음.
        int processed = 0;
        /// 이미 위에서 orderIds 검증해줬음 중복임.
        for (Long orderId : (orderIds == null ? List.<Long>of() : orderIds)) { ///주문수 만큼 작업된량을 저장해줌.상태도 바꿔가면서
            try {
                // 오래 걸리는 작업 이라는 가정 시뮬레이션 (예: 외부 시스템 연동, 대용량 계산 등)
                orderRepository.findById(orderId).ifPresent(o -> o.setStatus(Order.OrderStatus.PROCESSING)); ///N+1문제 발생 너무 많은 쿼리 생김.
                // 중간 진행률 저장
                this.updateProgressRequiresNew(jobId, ++processed, orderIds.size());///이부분은 중간 저장인 안됨. this로 이 무슨 같은 클래스내에 메서드를 부르면 일단 스프링 프록시가 작동을 안함.결국 중간 저장은 안된다는소리
            } catch (Exception e) {
                /// 로그 냠겨야 하는데 뭘 남겨야할지 모르겠음.
                log.error("주문 처리 실패. orderId={}, error={}", orderId,e.getMessage());
            }
        }
        ps = processingStatusRepository.findByJobId(jobId).orElse(ps); ///변경된 작업 저장 새로해줌
        ps.markCompleted(); ///완료된 작업으로 분류하고
        processingStatusRepository.save(ps); ///저장해줌
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateProgressRequiresNew(String jobId, int processed, int total) {
        ProcessingStatus ps = processingStatusRepository.findByJobId(jobId)
                .orElseGet(() -> ProcessingStatus.builder().jobId(jobId).build());
        ps.updateProgress(processed, total);
        processingStatusRepository.save(ps);
    }

}