package com.theclub.userservice.controller;

import com.theclub.common.security.AuthenticatedUser;
import com.theclub.common.web.ApiException;
import com.theclub.userservice.dto.RegisterUserRequest;
import com.theclub.userservice.dto.UpdateUserRequest;
import com.theclub.userservice.dto.UserResponse;
import com.theclub.userservice.model.User;
import com.theclub.userservice.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterUserRequest request) {
        return UserResponse.full(userService.register(request));
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser caller) {
        return UserResponse.full(userService.getActive(caller.id()));
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser caller) {
        User user = caller.isAdmin() ? userService.get(id) : userService.getActive(id);
        return isSelfOrAdmin(id, caller) ? UserResponse.full(user) : UserResponse.publicView(user);
    }

    @GetMapping("/email/{email}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse getByEmail(@PathVariable String email) {
        return UserResponse.full(userService.getByEmail(email));
    }

    /** Multipart upload (field "file"): JPEG, PNG or WebP up to the configured size. */
    @PostMapping(value = "/me/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserResponse uploadPhoto(@RequestParam("file") MultipartFile file,
                                    @AuthenticationPrincipal AuthenticatedUser caller) {
        return UserResponse.full(userService.uploadPhoto(caller.id(), file));
    }

    @DeleteMapping("/me/photo")
    public UserResponse removePhoto(@AuthenticationPrincipal AuthenticatedUser caller) {
        return UserResponse.full(userService.removePhoto(caller.id()));
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request,
                               @AuthenticationPrincipal AuthenticatedUser caller) {
        requireSelfOrAdmin(id, caller);
        return UserResponse.full(userService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser caller) {
        requireSelfOrAdmin(id, caller);
        userService.deactivate(id);
    }

    private static boolean isSelfOrAdmin(UUID id, AuthenticatedUser caller) {
        return caller.id().equals(id) || caller.isAdmin();
    }

    private static void requireSelfOrAdmin(UUID id, AuthenticatedUser caller) {
        if (!isSelfOrAdmin(id, caller)) {
            throw ApiException.forbidden("You can only change your own account");
        }
    }
}
