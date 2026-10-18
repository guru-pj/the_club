package com.theclub.userservice.controller;

import com.theclub.userservice.dto.RoleRequest;
import com.theclub.userservice.dto.UserCredentialsResponse;
import com.theclub.userservice.dto.UserIdsRequest;
import com.theclub.userservice.dto.UserSummary;
import com.theclub.userservice.model.Role;
import com.theclub.userservice.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Service-to-service API (requires X-Internal-Key; never routed by the gateway).
 */
@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

    private final UserService userService;

    public InternalUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/by-email/{email}")
    public UserCredentialsResponse byEmail(@PathVariable String email) {
        return UserCredentialsResponse.from(userService.getByEmail(email));
    }

    @GetMapping("/{id}")
    public UserCredentialsResponse byId(@PathVariable UUID id) {
        return UserCredentialsResponse.from(userService.get(id));
    }

    @PostMapping("/summaries")
    public List<UserSummary> summaries(@Valid @RequestBody UserIdsRequest request) {
        return userService.getAll(request.ids()).stream().map(UserSummary::from).toList();
    }

    @PostMapping("/{id}/roles")
    public UserCredentialsResponse addRole(@PathVariable UUID id, @Valid @RequestBody RoleRequest request) {
        return UserCredentialsResponse.from(userService.addRole(id, request.role()));
    }

    @DeleteMapping("/{id}/roles/{role}")
    public UserCredentialsResponse removeRole(@PathVariable UUID id, @PathVariable Role role) {
        return UserCredentialsResponse.from(userService.removeRole(id, role));
    }
}
