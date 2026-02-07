package com.ecoride.ride_service.client;

import com.ecoride.ride_service.dto.UserDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

// "user-service" wahi naam hai jo humne User-Service ki application.yml mein diya tha
@FeignClient(name = "user-service", url = "https://ecoride-deploy-user.onrender.com")
public interface UserClient {

    @GetMapping("/users/get-id")
    Long getUserIdByEmail(@RequestParam("email") String email);

    @GetMapping("/users/{id}")
    UserDTO getUserById(@PathVariable("id") Long id);

    @PutMapping("/users/{id}/update-earnings")
    void updateEarnings(@PathVariable("id") Long id, @RequestParam("amount") Double amount);
}