package com.cloudcart.order.controller;

import com.cloudcart.order.entity.Order;
import com.cloudcart.order.entity.OrderItem;
import com.cloudcart.order.repository.OrderRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderRepository orderRepository;
    private final RestClient restClient;

    @Value("${app.product-service-url}")
    private String productServiceUrl;

    @Value("${app.cart-service-url}")
    private String cartServiceUrl;

    public OrderController(OrderRepository orderRepository, RestClient restClient) {
        this.orderRepository = orderRepository;
        this.restClient = restClient;
    }

    public static class OrderRequest {
        private String type;
        private List<Map<String, Object>> items;
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public List<Map<String, Object>> getItems() { return items; }
        public void setItems(List<Map<String, Object>> items) { this.items = items; }
    }

    @PostMapping("/place")
    public ResponseEntity<?> placeOrder(@RequestBody OrderRequest orderRequest, Authentication authentication, HttpServletRequest request) {
        String username = authentication.getName();
        String authHeader = request.getHeader("Authorization");

        List<Map<String, Object>> itemsToOrder;

        if ("BUY_NOW".equalsIgnoreCase(orderRequest.getType())) {
            itemsToOrder = orderRequest.getItems();
            if (itemsToOrder == null || itemsToOrder.isEmpty()) {
                return ResponseEntity.badRequest().body("No items provided for BUY_NOW.");
            }
        } else {
            // Default to CART mode
            Map<String, Object> cart;
            try {
                cart = restClient.get()
                        .uri(cartServiceUrl + "/cart")
                        .header("Authorization", authHeader)
                        .retrieve()
                        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                return ResponseEntity.badRequest().body("Could not fetch cart.");
            }

            if (cart == null || !cart.containsKey("items")) {
                return ResponseEntity.badRequest().body("Cart is empty or could not be fetched.");
            }

            itemsToOrder = (List<Map<String, Object>>) cart.get("items");
        }

        if (itemsToOrder.isEmpty()) {
            return ResponseEntity.badRequest().body("No items to order.");
        }

        Order order = new Order();
        order.setUsername(username);
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (Map<String, Object> item : itemsToOrder) {
            Long productId = ((Number) item.get("productId")).longValue();
            Integer quantity = (Integer) item.get("quantity");

            // Fetch Product details to verify stock and price
            Map<String, Object> product;
            try {
                product = restClient.get()
                        .uri(productServiceUrl + "/products/" + productId)
                        .retrieve()
                        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                return ResponseEntity.badRequest().body("Product " + productId + " does not exist.");
            }
            
            if (product == null) {
                return ResponseEntity.badRequest().body("Product " + productId + " does not exist.");
            }

            Integer stock = (Integer) product.get("stock");
            if (stock < quantity) {
                // Return 409 Conflict if insufficient stock
                return ResponseEntity.status(409).body("Not enough stock for product " + productId);
            }

            BigDecimal price = new BigDecimal(product.get("price").toString());

            // Reduce stock synchronously
            try {
                restClient.patch()
                        .uri(productServiceUrl + "/products/" + productId + "/stock")
                        .header("Authorization", authHeader)
                        .body(Map.of("quantity", -quantity))
                        .retrieve()
                        .toBodilessEntity();
            } catch (Exception e) {
                 return ResponseEntity.status(409).body("Failed to update stock for product " + productId);
            }

            OrderItem orderItem = new OrderItem();
            orderItem.setProductId(productId);
            orderItem.setQuantity(quantity);
            orderItem.setPriceAtTime(price);
            orderItem.setOrder(order);

            order.getItems().add(orderItem);
            totalAmount = totalAmount.add(price.multiply(new BigDecimal(quantity)));
        }

        order.setTotalAmount(totalAmount);
        orderRepository.save(order);

        // Clear Cart ONLY if mode is CART
        if (!"BUY_NOW".equalsIgnoreCase(orderRequest.getType())) {
            try {
                restClient.delete()
                        .uri(cartServiceUrl + "/cart/clear")
                        .header("Authorization", authHeader)
                        .retrieve()
                        .toBodilessEntity();
            } catch (Exception e) {
                // Log or ignore failure to clear cart
            }
        }

        return ResponseEntity.ok(order);
    }

    @GetMapping
    public ResponseEntity<List<Order>> getOrders(Authentication authentication) {
        return ResponseEntity.ok(orderRepository.findByUsernameOrderByCreatedAtDesc(authentication.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrderById(@PathVariable Long id, Authentication authentication) {
        return orderRepository.findById(id)
                .filter(order -> order.getUsername().equals(authentication.getName()))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
