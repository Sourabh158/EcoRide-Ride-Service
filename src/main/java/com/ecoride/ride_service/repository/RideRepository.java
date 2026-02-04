package com.ecoride.ride_service.repository;

import com.ecoride.ride_service.model.Ride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RideRepository extends JpaRepository<Ride, Long> {
    List<Ride> findByRiderEmail(String riderEmail);
    List<Ride> findByDriverId(Long driverId);
    Ride findByRazorpayOrderId(String razorpayOrderId);
    List<Ride> findByStatus(String status);
    List<Ride> findByRiderEmailAndStatus(String email, String status);
    List<Ride> findByDriverIdAndStatus(Long driverId, String status);
}