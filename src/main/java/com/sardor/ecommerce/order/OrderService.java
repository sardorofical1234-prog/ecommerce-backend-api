package com.sardor.ecommerce.order;

import com.sardor.ecommerce.common.NotFoundException;
import com.sardor.ecommerce.common.UnauthorizedException;
import com.sardor.ecommerce.user.UserEntity;
import com.sardor.ecommerce.user.UserRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class OrderService {
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    public OrderService(UserRepository userRepository, OrderRepository orderRepository) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getMyOrders(String customerEmail, Pageable pageable) {
        UserEntity customer = userRepository.findByEmail(customerEmail)
                .orElseThrow(() -> new UnauthorizedException("Authenticated customer account was not found"));
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId(), pageable)
                .stream().map(OrderResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(String customerEmail, UUID orderId) {
        UserEntity customer = userRepository.findByEmail(customerEmail)
                .orElseThrow(() -> new UnauthorizedException("Authenticated customer account was not found"));
        return orderRepository.findByIdAndCustomerId(orderId, customer.getId())
                .map(OrderResponse::from)
                .orElseThrow(() -> new NotFoundException("Order not found"));
    }
}
