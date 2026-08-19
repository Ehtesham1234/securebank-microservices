package com.ehtesham.securebank.admin;

import com.ehtesham.securebank.common.exception.ResourceNotFoundException;
import com.ehtesham.securebank.common.response.ApiResponse;
import com.ehtesham.securebank.user.dto.UserResponse;
import com.ehtesham.securebank.user.entity.User;
import com.ehtesham.securebank.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Administrative operations")
public class AdminController {

    private final UserRepository userRepository;

    @GetMapping("/api/v1/admin/test")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public String adminTest() {
        return "Welcome Admin";
    }

    // C3 fix: /api/v1/auth/generate-hash deleted entirely. It was an
    // unauthenticated BCrypt-hashing oracle — publicly reachable (its
    // path matched the /api/v1/auth/** permitAll pattern), no
    // @PreAuthorize, and explicitly commented "DELETE after use" but
    // never removed. Also a free CPU-exhaustion lever since BCrypt is
    // deliberately expensive and this had no rate limit.

    /** Paginated, optionally search-filtered (by first name / last name /
     *  email, case-insensitive substring match) user list — the piece
     *  that was missing for any admin "browse and click into a user"
     *  screen. Default sort by id keeps paging stable across requests. */
    @GetMapping("/api/v1/admin/users")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> listUsers(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        Page<User> users = userRepository.searchUsers(search, pageable);
        Page<UserResponse> response = users.map(this::toUserResponse);
        return ResponseEntity.ok(ApiResponse.success("Users retrieved", response));
    }

    @GetMapping("/api/v1/admin/users/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
        return ResponseEntity.ok(ApiResponse.success("User retrieved", toUserResponse(user)));
    }

    private UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .role(user.getRole())
                .userStatus(user.getUserStatus())
                .emailVerified(user.isEmailVerified())
                .build();
    }
}