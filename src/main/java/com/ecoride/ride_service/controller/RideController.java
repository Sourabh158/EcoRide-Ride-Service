package com.ecoride.ride_service.controller;

import com.razorpay.Utils;
import com.ecoride.ride_service.client.NotificationClient;
import com.ecoride.ride_service.client.UserClient;
import com.ecoride.ride_service.dto.UserDTO;
import com.ecoride.ride_service.model.Ride;
import com.ecoride.ride_service.repository.RideRepository;
import com.ecoride.ride_service.service.DistanceService;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import jakarta.transaction.Transactional;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/rides")
public class RideController {

    @Autowired
    private RazorpayClient razorpayClient;

    @Autowired
    private UserClient userClient;

    @Autowired
    private RideRepository rideRepository;

    @Autowired
    private NotificationClient notificationClient;

    @Autowired
    private DistanceService distanceService;

    @Value("${razorpay.key-secret}")
    private String keySecret;


    @Transactional
    @PostMapping("/book")
    public String bookRide(@RequestHeader("loggedInUser") String email, @RequestBody Ride ride) {
        // Step 1: Normalize email to avoid 404 mismatch
        String normalizedEmail = email.trim().toLowerCase();
        System.out.println("DEBUG 1: Booking request for email: " + normalizedEmail);

        try {
            // Step 2: Check for active rides
            List<Ride> activeRides = rideRepository.findByRiderEmail(normalizedEmail);
            boolean hasActiveRide = activeRides.stream()
                    .anyMatch(r -> "REQUESTED".equals(r.getStatus()) || "ACCEPTED".equals(r.getStatus()));

            if (hasActiveRide) {
                return "Error: You already have an active ride request!";
            }

            // Step 3: Call User Service to get ID
            System.out.println("DEBUG 2: Calling User Service for: " + normalizedEmail);
            Long actualRiderId = userClient.getUserIdByEmail(normalizedEmail);

            if (actualRiderId == null) {
                System.out.println("DEBUG 3: Rider ID NOT FOUND in User Service");
                return "Error: User ID not found. Make sure you are registered!";
            }
            System.out.println("DEBUG 4: Rider ID found: " + actualRiderId);

            // Step 4: Distance calculation
            double distance = distanceService.calculateDistance(
                    ride.getSourceLat(), ride.getSourceLng(),
                    ride.getDestinationLat(), ride.getDestinationLng()
            );

            // Step 5: Final ride setup
            ride.setRiderId(actualRiderId);
            ride.setRiderEmail(normalizedEmail);
            ride.setDistanceInKm(distance);
            ride.setFare(distance * 12.0); // Simple fare logic
            ride.setStatus("REQUESTED");

            rideRepository.save(ride);
            System.out.println("DEBUG 5: Ride saved in DB with status REQUESTED");

            // Step 6: Async notification
            try {
                notificationClient.sendUpdate(normalizedEmail, "Ride request sent! Waiting for driver.");
            } catch (Exception e) {
                System.out.println("WARN: Email notification failed, but ride is booked.");
            }

            return "Ride Booked! Fare: ₹" + ride.getFare();

        } catch (Exception e) {
            System.out.println("CRITICAL ERROR: " + e.getMessage());
            e.printStackTrace();
            return "Server Error: " + e.getMessage();
        }
    }

    @GetMapping("/driver-history/{driverId}")
    public List<Ride> getDriverHistory(@PathVariable Long driverId) {
        return rideRepository.findByDriverId(driverId);
    }

    @GetMapping("/my-rides")
    public List<Ride> getMyRides(@RequestHeader("loggedInUser") String email) {
        return rideRepository.findByRiderEmail(email);
    }

    @PutMapping("/{id}/cancel")
    public String cancelRide(@PathVariable Long id, @RequestHeader("loggedInUser") String email) {
        return rideRepository.findById(id).map(ride -> {
            if (!ride.getRiderEmail().equals(email)) {
                return "Error: You are not authorized to cancel this ride!";
            }
            if (!"REQUESTED".equalsIgnoreCase(ride.getStatus())) {
                return "Error: Ride cannot be cancelled now. Status: " + ride.getStatus();
            }
            ride.setStatus("CANCELLED");
            rideRepository.save(ride);

            notificationClient.sendUpdate(email, "Your ride (ID: " + id + ") has been successfully cancelled.");
            return "Ride cancelled successfully!";
        }).orElse("Error: Ride ID " + id + " not found!");
    }

    @Transactional
    @PutMapping("/{id}/complete")
    public String completeRide(@PathVariable Long id) {
        return rideRepository.findById(id).map(ride -> {
            try {
                if ("COMPLETED".equalsIgnoreCase(ride.getStatus())) {
                    return "Ride already completed!";
                }

                // 1. Razorpay Order logic (Same as before)
                JSONObject orderRequest = new JSONObject();
                orderRequest.put("amount", (int)(ride.getFare() * 100));
                orderRequest.put("currency", "INR");
                orderRequest.put("receipt", "ride_rcpt_" + id);

                Order order = razorpayClient.orders.create(orderRequest);
                String orderId = order.get("id");

                ride.setRazorpayOrderId(orderId);
                ride.setStatus("COMPLETED");
                rideRepository.save(ride);

                // --- ZARURI CHANGE: Notification call ko safe banao ---
                try {
                    String paymentMsg = "Your ride is complete! Total: ₹" + ride.getFare() +
                            ". Please pay using Order ID: " + orderId;
                    notificationClient.sendUpdate(ride.getRiderEmail(), paymentMsg);
                } catch (Exception e) {
                    // Agar notification service down hai toh sirf log karo, error mat throw karo
                    System.out.println("WARN: Notification failed but ride marked COMPLETED: " + e.getMessage());
                }

                return "Ride Completed! Razorpay Order Created: " + orderId;
            } catch (Exception e) {
                return "Error while completing ride: " + e.getMessage();
            }
        }).orElse("Error: Ride not found!");
    }

    // RideController.java mein add karein
    @GetMapping("/admin/stats")
    public Map<String, Object> getAdminStats() {
        List<Ride> allRides = rideRepository.findAll();
        double totalRevenue = allRides.stream()
                .filter(r -> "COMPLETED_AND_PAID".equals(r.getStatus()))
                .mapToDouble(Ride::getFare)
                .sum();

        return Map.of(
                "totalRides", allRides.size(),
                "totalRevenue", totalRevenue,
                "recentTrips", rideRepository.findAll().stream().limit(10).toList()
        );
    }

    // RideController.java mein acceptRide ko modify karein
    @PutMapping("/{id}/accept")
    public String acceptRide(@PathVariable Long id, @RequestParam Long driverId) {
        return rideRepository.findById(id).map(ride -> {
            try {
                // 1. Check karo ki ride abhi bhi available hai ya nahi
                if (!"REQUESTED".equals(ride.getStatus())) {
                    return "Error: Ride is already accepted or no longer available!";
                }

                // 2. Feign call ko safe rakho (User Service connectivity check)
                UserDTO driver;
                try {
                    driver = userClient.getUserById(driverId);
                } catch (Exception feignException) {
                    System.out.println("CRITICAL: User-Service call failed: " + feignException.getMessage());
                    return "Error: Could not verify driver profile. Please try again later.";
                }

                // 3. Driver validation
                if (driver == null || !Boolean.TRUE.equals(driver.isApproved())) {
                    return "Error: Driver profile not found or your account is not approved by admin!";
                }

                // 4. Update and Save (Atomic operation)
                ride.setDriverId(driverId);
                ride.setStatus("ACCEPTED");
                rideRepository.save(ride);

                // 5. Notification (Optional error handling)
                try {
                    notificationClient.sendUpdate(ride.getRiderEmail(),
                            "Good news! Your ride has been accepted by " + driver.getName());
                } catch (Exception e) {
                    System.out.println("WARN: Could not send rider notification, but ride is accepted.");
                }

                return "Ride Accepted Successfully! Start moving towards pickup.";

            } catch (Exception e) {
                return "Server Error: Unexpected error occurred. " + e.getMessage();
            }
        }).orElse("Error: Ride ID " + id + " not found!");
    }

    @GetMapping("/available")
    public List<Ride> getAvailableRides() {
        return rideRepository.findByStatus("REQUESTED");
    }

    @PostMapping("/payment/verify")
    public String verifyPayment(@RequestBody Map<String, String> data) {
        String orderId = data.get("razorpay_order_id");
        String paymentId = data.get("razorpay_payment_id");
        String signature = data.get("razorpay_signature");

        try {
            // 1. Signature Verification active kar di hai production ke liye
            JSONObject options = new JSONObject();
            options.put("razorpay_order_id", orderId);
            options.put("razorpay_payment_id", paymentId);
            options.put("razorpay_signature", signature);

            // Ab ye keySecret application.yml se dynamic uthayega
            boolean isValid = Utils.verifyPaymentSignature(options, keySecret);

            if (isValid) {
                // 2. Database Update logic (Same as before)
                Ride ride = rideRepository.findByRazorpayOrderId(orderId);

                if (ride != null) {
                    ride.setPaymentId(paymentId);
                    ride.setPaymentStatus("PAID");
                    Double driverShare = ride.getFare() * 0.8; // 80% Driver ko
                    userClient.updateEarnings(ride.getDriverId(), driverShare);
                    ride.setStatus("COMPLETED_AND_PAID");
                    rideRepository.save(ride);

                    return "Payment Verified Successfully! Ride Status Updated to PAID.";
                } else {
                    return "Error: Ride not found for Order ID: " + orderId;
                }
            } else {
                return "Error: Invalid Payment Signature!";
            }
        } catch (Exception e) {
            return "Verification Failed: " + e.getMessage();
        }
    }

    @GetMapping("/history/{email}")
    public List<Ride> getRideHistory(@PathVariable String email) {
        return rideRepository.findByRiderEmailAndStatus(email, "COMPLETED_AND_PAID");
    }


}