package com.cloudcart.order.service;

import com.cloudcart.order.entity.ServiceRequest;
import com.cloudcart.order.entity.ServiceRequest.RequestStatus;
import com.cloudcart.order.repository.ServiceRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ServiceRequestService {

    private static final Logger log = LoggerFactory.getLogger(ServiceRequestService.class);

    private final ServiceRequestRepository repository;
    private final RestTemplate restTemplate;
    
    @Value("${app.vehicle-service-url}")
    private String vehicleServiceUrl;

    public ServiceRequestService(ServiceRequestRepository repository, RestTemplateBuilder restTemplateBuilder) {
        this.repository = repository;
        // Configure resilience: 3s connect, 5s read timeouts
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
    }

    public List<ServiceRequest> getAllRequests() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    public List<ServiceRequest> getRequestsByRequestedBy(String username) {
        return repository.findByRequestedByOrderByCreatedAtDesc(username);
    }

    public List<ServiceRequest> getRequestsByVehicle(Long vehicleId) {
        return repository.findByVehicleIdOrderByCreatedAtDesc(vehicleId);
    }

    public Optional<ServiceRequest> getRequestById(Long id) {
        return repository.findById(id);
    }

    public ServiceRequest createRequest(ServiceRequest request) {
        // Duplicate check: avoid spam if vehicle is already in service
        if (repository.existsByVehicleIdAndStatusIn(request.getVehicleId(), 
                List.of(RequestStatus.OPEN, RequestStatus.PENDING_APPROVAL, RequestStatus.APPROVED, RequestStatus.ASSIGNED, RequestStatus.IN_PROGRESS))) {
            throw new IllegalStateException("Vehicle already has an active service request.");
        }

        // If it's a breakdown, automatically start as OPEN and set vehicle to BREAKDOWN
        if (request.getRequestType() == ServiceRequest.RequestType.BREAKDOWN) {
             request.setStatus(RequestStatus.OPEN);
             updateVehicleStatus(request.getVehicleId(), "BREAKDOWN", getAuthToken());
        }

        return repository.save(request);
    }

    public Optional<ServiceRequest> updateRequestStatus(Long id, RequestStatus newStatus, String updatedBy, String token) {
        return repository.findById(id).map(request -> {
            RequestStatus oldStatus = request.getStatus();
            request.setStatus(newStatus);
            
            if (newStatus == RequestStatus.APPROVED) {
                request.setApprovedBy(updatedBy);
            }

            // Sync with Vehicle Service
            try {
                if (newStatus == RequestStatus.IN_PROGRESS && oldStatus != RequestStatus.IN_PROGRESS) {
                    updateVehicleStatus(request.getVehicleId(), "IN_SERVICE", token);
                } else if (newStatus == RequestStatus.COMPLETED && oldStatus != RequestStatus.COMPLETED) {
                    updateVehicleStatus(request.getVehicleId(), "ACTIVE", token);
                }
            } catch (Exception e) {
                log.error("Failed to sync vehicle status with Vehicle Service. Request ID: {}. Error: {}", id, e.getMessage());
                throw new RuntimeException("Service unavailable. Failed to sync with Vehicle Service.");
            }

            return repository.save(request);
        });
    }

    public Optional<ServiceRequest> assignTechnician(Long id, String technician) {
        return repository.findById(id).map(request -> {
            request.setAssignedTechnician(technician);
            if(request.getStatus() == RequestStatus.APPROVED) {
                 request.setStatus(RequestStatus.ASSIGNED);
            }
            return repository.save(request);
        });
    }

    public Optional<ServiceRequest> completeRequest(Long id, String resolutionNotes, Double downtimeHours, String token) {
        return repository.findById(id).map(request -> {
            request.setResolutionNotes(resolutionNotes);
            request.setDowntimeHours(downtimeHours);
            return updateRequestStatus(id, RequestStatus.COMPLETED, null, token).get();
        });
    }


    private void updateVehicleStatus(Long vehicleId, String status, String token) {
        String url = vehicleServiceUrl + "/api/vehicles/" + vehicleId + "/status";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.set("Authorization", token);
        }

        HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of("status", status), headers);

        // Simple retry logic (1 retry)
        int maxRetries = 1;
        for (int i = 0; i <= maxRetries; i++) {
            try {
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PATCH, request, String.class);
                if (response.getStatusCode().is2xxSuccessful()) {
                    log.info("Successfully updated vehicle {} status to {}", vehicleId, status);
                    return;
                }
            } catch (RestClientException e) {
                log.warn("Attempt {} failed to update vehicle status: {}", i + 1, e.getMessage());
                if (i == maxRetries) {
                    throw e;
                }
            }
        }
    }
    
    // Helper to get token (in a real app, this would be properly extracted from context, but we pass it down for simplicity here)
    private String getAuthToken() {
        return null;
    }
}
