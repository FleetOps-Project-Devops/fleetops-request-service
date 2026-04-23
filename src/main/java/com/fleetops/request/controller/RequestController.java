package com.fleetops.request.controller;

import com.fleetops.request.entity.ServiceRequest;
import com.fleetops.request.entity.ServiceRequest.RequestStatus;
import com.fleetops.request.service.ServiceRequestService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/requests")
public class RequestController {

    private final ServiceRequestService requestService;

    public RequestController(ServiceRequestService requestService) {
        this.requestService = requestService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('DRIVER', 'MANAGER', 'ADMIN')")
    public ResponseEntity<List<ServiceRequest>> getRequests(Authentication authentication) {
        boolean isDriver = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_DRIVER"));

        if (isDriver) {
            return ResponseEntity.ok(requestService.getRequestsByRequestedBy(authentication.getName()));
        }

        return ResponseEntity.ok(requestService.getAllRequests());
    }

    @GetMapping("/vehicle/{vehicleId}")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<List<ServiceRequest>> getRequestsByVehicle(@PathVariable Long vehicleId) {
        return ResponseEntity.ok(requestService.getRequestsByVehicle(vehicleId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('DRIVER', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ServiceRequest> getRequest(@PathVariable Long id, Authentication authentication) {
        return requestService.getRequestById(id)
                .map(request -> {
                    boolean isDriver = authentication.getAuthorities().stream()
                            .anyMatch(a -> a.getAuthority().equals("ROLE_DRIVER"));
                    
                    if (isDriver && !request.getRequestedBy().equals(authentication.getName())) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).<ServiceRequest>build();
                    }
                    return ResponseEntity.ok(request);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN')")
    public ResponseEntity<?> createRequest(@RequestBody ServiceRequest request, Authentication authentication, HttpServletRequest httpRequest) {
        try {
            String token = httpRequest.getHeader("Authorization");
            boolean isDriver = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_DRIVER"));
            ServiceRequest created = requestService.createRequest(request, authentication.getName(), token, isDriver);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Vehicle already has an active service request.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(e.getMessage());
        }
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> payload, Authentication authentication, HttpServletRequest httpRequest) {
        if (!payload.containsKey("status")) {
            return ResponseEntity.badRequest().body("Missing 'status' field");
        }

        try {
            RequestStatus newStatus = RequestStatus.valueOf(payload.get("status").toUpperCase());
            String token = httpRequest.getHeader("Authorization");
            
            return requestService.updateRequestStatus(id, newStatus, authentication.getName(), token)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid status");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(e.getMessage());
        }
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<?> assignTechnician(@PathVariable Long id,
                                              @RequestBody Map<String, String> payload,
                                              HttpServletRequest httpRequest) {
        if (!payload.containsKey("technician")) {
            return ResponseEntity.badRequest().build();
        }
        try {
            String token = httpRequest.getHeader("Authorization");
            return requestService.assignTechnician(id, payload.get("technician"), token)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }
    
    @PatchMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<?> completeRequest(@PathVariable Long id, @RequestBody Map<String, Object> payload, HttpServletRequest httpRequest) {
        String token = httpRequest.getHeader("Authorization");
        String resolutionNotes = (String) payload.get("resolutionNotes");
        Double downtimeHours = payload.containsKey("downtimeHours") ? Double.valueOf(payload.get("downtimeHours").toString()) : null;
        
        try {
            return requestService.completeRequest(id, resolutionNotes, downtimeHours, token)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (RuntimeException e) {
             return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(e.getMessage());
        }
    }
}

