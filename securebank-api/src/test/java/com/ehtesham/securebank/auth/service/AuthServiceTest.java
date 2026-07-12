package com.ehtesham.securebank.auth.service;

import com.ehtesham.securebank.auth.dto.LoginRequest;
import com.ehtesham.securebank.auth.dto.RegisterRequest;
import com.ehtesham.securebank.auth.entity.RefreshToken;
import com.ehtesham.securebank.auth.service.impl.AuthServiceImpl;
import com.ehtesham.securebank.common.enums.Role;
import com.ehtesham.securebank.common.enums.UserStatus;
import com.ehtesham.securebank.common.exception.EmailAlreadyExistsException;
import com.ehtesham.securebank.common.exception.InvalidCredentialsException;
import com.ehtesham.securebank.common.exception.RateLimitExceededException;
import com.ehtesham.securebank.notification.publisher.NotificationEventPublisher;
import com.ehtesham.securebank.otp.enums.OtpPurpose;
import com.ehtesham.securebank.otp.service.OtpService;
import com.ehtesham.securebank.security.ratelimit.RateLimiterService;
import com.ehtesham.securebank.security.service.JwtService;
import com.ehtesham.securebank.user.dto.UserResponse;
import com.ehtesham.securebank.user.entity.User;
import com.ehtesham.securebank.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Tests")
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private OtpService otpService;
//    @Mock private EmailService emailService;
    @Mock private NotificationEventPublisher notificationPublisher;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private RateLimiterService rateLimiterService;
    @Mock private LoginAttemptService loginAttemptService;

    private AuthServiceImpl authService;
    private User testUser;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                userRepository,
                passwordEncoder,
                jwtService,
                refreshTokenService,
                otpService,
//                emailService,
                notificationPublisher,
                authenticationManager,
                rateLimiterService,
                loginAttemptService);

        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@securebank.com");
        testUser.setFirstName("Test");
        testUser.setLastName("User");
        testUser.setRole(Role.CUSTOMER);
        testUser.setUserStatus(UserStatus.PENDING_KYC);
        testUser.setEmailVerified(true);
    }

    // ── Registration Tests ────────────────────────────────────────

    @Nested
    @DisplayName("Registration Tests")
    class RegistrationTests {

        @Test
        @DisplayName("Should register new user successfully")
        void register_success() {
            RegisterRequest request = buildRegisterRequest(
                    "new@securebank.com");

            when(rateLimiterService.tryConsume(
                    anyString(), anyInt(), any(Duration.class)))
                    .thenReturn(true);
            when(userRepository.findByEmail("new@securebank.com"))
                    .thenReturn(Optional.empty());
            when(passwordEncoder.encode(any()))
                    .thenReturn("encoded_password");
            when(userRepository.save(any(User.class)))
                    .thenAnswer(inv -> {
                        User u = inv.getArgument(0);
                        u.setId(99L);
                        return u;
                    });
            when(otpService.generateAndSaveOtp(
                    anyString(), eq(OtpPurpose.EMAIL_VERIFICATION)))
                    .thenReturn("123456");

            UserResponse response = authService.register(
                    request, "127.0.0.1");

            assertThat(response.getEmail())
                    .isEqualTo("new@securebank.com");
            assertThat(response.getRole())
                    .isEqualTo(Role.CUSTOMER);
            assertThat(response.getUserStatus())
                    .isEqualTo(UserStatus.PENDING_KYC);

            verify(notificationPublisher).publishOtpEmail(
                    eq("new@securebank.com"),
                    eq("123456"),
                    eq("Email Verification"));
        }

        @Test
        @DisplayName("Should throw when email already exists and is verified")
        void register_duplicateVerifiedEmail() {
            testUser.setEmailVerified(true);

            when(rateLimiterService.tryConsume(
                    anyString(), anyInt(), any(Duration.class)))
                    .thenReturn(true);
            when(userRepository.findByEmail(testUser.getEmail()))
                    .thenReturn(Optional.of(testUser));

            assertThatThrownBy(() ->
                    authService.register(
                            buildRegisterRequest(testUser.getEmail()),
                            "127.0.0.1"))
                    .isInstanceOf(EmailAlreadyExistsException.class)
                    .hasMessageContaining("Please login instead");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should resend OTP when email exists but is unverified")
        void register_unverifiedEmailResendOtp() {
            testUser.setEmailVerified(false);

            when(rateLimiterService.tryConsume(
                    anyString(), anyInt(), any(Duration.class)))
                    .thenReturn(true);
            when(userRepository.findByEmail(testUser.getEmail()))
                    .thenReturn(Optional.of(testUser));
            when(otpService.generateAndSaveOtp(
                    anyString(), eq(OtpPurpose.EMAIL_VERIFICATION)))
                    .thenReturn("654321");

            UserResponse response = authService.register(
                    buildRegisterRequest(testUser.getEmail()),
                    "127.0.0.1");

            // returns existing user's data — NOT a new registration error
            assertThat(response.getEmail())
                    .isEqualTo(testUser.getEmail());
            assertThat(response.isEmailVerified()).isFalse();

            // new user must NOT be created
            verify(userRepository, never()).save(any());

            // but OTP MUST be resent
            verify(notificationPublisher).publishOtpEmail(
                    anyString(), eq("654321"),
                    eq("Email Verification"));
        }

        @Test
        @DisplayName("Should throw when rate limit exceeded on registration")
        void register_rateLimitExceeded() {
            when(rateLimiterService.tryConsume(
                    anyString(), anyInt(), any(Duration.class)))
                    .thenReturn(false); // rate limit hit

            assertThatThrownBy(() ->
                    authService.register(
                            buildRegisterRequest("any@email.com"),
                            "192.168.1.1"))
                    .isInstanceOf(RateLimitExceededException.class);

            // nothing should have been touched
            verify(userRepository, never()).findByEmail(any());
            verify(userRepository, never()).save(any());
        }
    }

    // ── Login Tests ───────────────────────────────────────────────

    @Nested
    @DisplayName("Login Tests")
    class LoginTests {

        @Test
        @DisplayName("Should login successfully and return tokens")
        void login_success() {
            LoginRequest request = new LoginRequest();
            request.setEmail(testUser.getEmail());
            request.setPassword("ValidPass123!");

            RefreshToken mockToken = new RefreshToken();
            mockToken.setToken("mock-refresh-token");
            mockToken.setUser(testUser);

            when(rateLimiterService.tryConsume(
                    anyString(), anyInt(), any(Duration.class)))
                    .thenReturn(true);
            when(authenticationManager.authenticate(any()))
                    .thenReturn(null); // authentication succeeds
            when(userRepository.findByEmail(testUser.getEmail()))
                    .thenReturn(Optional.of(testUser));
            when(jwtService.generateToken(anyString(), anyString()))
                    .thenReturn("mock.jwt.access.token");
            when(refreshTokenService.createRefreshToken(any()))
                    .thenReturn(mockToken);

            var response = authService.login(request);

            assertThat(response.getAccessToken())
                    .isEqualTo("mock.jwt.access.token");
            assertThat(response.getRefreshToken())
                    .isEqualTo("mock-refresh-token");
            assertThat(response.getUserStatus())
                    .isEqualTo(UserStatus.PENDING_KYC);

            // successful login resets attempt counter
            verify(loginAttemptService).resetAttempts(testUser.getEmail());
            verify(loginAttemptService, never())
                    .recordFailedAttempt(any());
        }

        @Test
        @DisplayName("Should record failed attempt on wrong password")
        void login_wrongPasswordRecordsAttempt() {
            LoginRequest request = new LoginRequest();
            request.setEmail(testUser.getEmail());
            request.setPassword("WrongPassword!");

            when(rateLimiterService.tryConsume(
                    anyString(), anyInt(), any(Duration.class)))
                    .thenReturn(true);
            when(authenticationManager.authenticate(any()))
                    .thenThrow(new BadCredentialsException(
                            "bad credentials"));

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(InvalidCredentialsException.class);

            verify(loginAttemptService)
                    .recordFailedAttempt(testUser.getEmail());
            verify(loginAttemptService, never())
                    .resetAttempts(any());
        }

        @Test
        @DisplayName("Should throw RateLimitExceeded before even " +
                "attempting authentication")
        void login_rateLimitBlocksBeforeAuthentication() {
            LoginRequest request = new LoginRequest();
            request.setEmail(testUser.getEmail());
            request.setPassword("AnyPassword1!");

            when(rateLimiterService.tryConsume(
                    anyString(), anyInt(), any(Duration.class)))
                    .thenReturn(false);

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(RateLimitExceededException.class);

            // authentication must NEVER be called if rate limit fires first
            verify(authenticationManager, never()).authenticate(any());
            verify(loginAttemptService, never())
                    .recordFailedAttempt(any());
        }

        @Test
        @DisplayName("Should throw EmailNotVerifiedException when " +
                "account is disabled due to unverified email")
        void login_unverifiedEmailBlocked() {
            testUser.setEmailVerified(false);

            LoginRequest request = new LoginRequest();
            request.setEmail(testUser.getEmail());
            request.setPassword("ValidPass123!");

            when(rateLimiterService.tryConsume(
                    anyString(), anyInt(), any(Duration.class)))
                    .thenReturn(true);
            when(authenticationManager.authenticate(any()))
                    .thenThrow(new DisabledException("disabled"));
            when(userRepository.findByEmail(testUser.getEmail()))
                    .thenReturn(Optional.of(testUser));

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(
                            com.ehtesham.securebank.common.exception
                                    .EmailNotVerifiedException.class);
        }

        @Test
        @DisplayName("Should NOT call recordFailedAttempt when account " +
                "is disabled — not a password failure")
        void login_disabledAccountDoesNotIncrementFailedAttempts() {
            LoginRequest request = new LoginRequest();
            request.setEmail(testUser.getEmail());
            request.setPassword("ValidPass123!");

            when(rateLimiterService.tryConsume(
                    anyString(), anyInt(), any(Duration.class)))
                    .thenReturn(true);
            when(authenticationManager.authenticate(any()))
                    .thenThrow(new DisabledException("disabled"));
            when(userRepository.findByEmail(testUser.getEmail()))
                    .thenReturn(Optional.of(testUser));

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(Exception.class);

            // DisabledException is NOT a password failure —
            // failed attempt counter must NOT increment
            verify(loginAttemptService, never())
                    .recordFailedAttempt(any());
        }
    }

    // ── Helper ────────────────────────────────────────────────────

    private RegisterRequest buildRegisterRequest(String email) {
        RegisterRequest request = new RegisterRequest();
        request.setEmail(email);
        request.setPassword("ValidPass123!");
        request.setFirstName("Test");
        request.setLastName("User");
        return request;
    }
}