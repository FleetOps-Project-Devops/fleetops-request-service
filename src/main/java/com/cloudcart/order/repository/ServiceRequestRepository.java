package com.cloudcart.order.repository;

import com.cloudcart.order.entity.ServiceRequest;
import com.cloudcart.order.entity.ServiceRequest.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, Long> {

    List<ServiceRequest> findByRequestedByOrderByCreatedAtDesc(String requestedBy);

    List<ServiceRequest> findByVehicleIdOrderByCreatedAtDesc(Long vehicleId);

    List<ServiceRequest> findByStatusOrderByCreatedAtDesc(RequestStatus status);

    List<ServiceRequest> findAllByOrderByCreatedAtDesc();

    long countByStatus(RequestStatus status);

    boolean existsByVehicleIdAndStatusIn(Long vehicleId, List<RequestStatus> statuses);
}
