package com.ehtesham.securebank.auth.service.impl;

import com.ehtesham.securebank.audit.annotation.Auditable;
import com.ehtesham.securebank.auth.dto.*;
import com.ehtesham.securebank.auth.entity.RefreshToken;
import com.ehtesham.securebank.auth.service.AuthService;
import com.ehtesham.securebank.auth.service.LoginAttemptService;
import com.ehtesham.securebank.security.util.ClientIpUtils;
import com.ehtesham.securebank.auth.service.RefreshTokenService;
import com.ehtesham.securebank.common.enums.Role;
import com.ehtesham.securebank.common.enums.UserStatus;
import com.ehtesham.securebank.common.exception.*;

import com.ehtesham.securebank.notification.publisher.NotificationEventPublisher;
import com.ehtesham.securebank.otp.enums.OtpPurpose;
import com.ehtesham.securebank.otp.service.OtpService;
import com.ehtesham.securebank.security.ratelimit.RateLimiterService;
import com.ehtesham.securebank.security.service.JwtService;
import com.ehtesham.securebank.user.dto.UserResponse;
import com.ehtesham.securebank.user.entity.User;
import com.ehtesham.securebank.user.repository.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuthServiceImpl implements AuthService {


    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final OtpService otpService;
    private final NotificationEventPublisher notificationPublisher;
    private final AuthenticationManager authenticationManager;
    private final RateLimiterService rateLimiterService;
    private final LoginAttemptService loginAttemptService;
    public AuthServiceImpl(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService, OtpService otpService,
            NotificationEventPublisher notificationPublisher, AuthenticationManager authenticationManager, RateLimiterService rateLimiterService, LoginAttemptService loginAttemptService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.otpService = otpService;
        this.notificationPublisher = notificationPublisher;
        this.authenticationManager = authenticationManager;
        this.rateLimiterService = rateLimiterService;
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request, String clientIp) {

        String rateLimitKey = "register:" + clientIp;

        boolean allowed = rateLimiterService.tryConsume(
                rateLimitKey, 3, Duration.ofHours(1));

        if (!allowed) {
            throw new RateLimitExceededException(
                    "Too many registration attempts. Please try again later.");
        }

        User existingUser = userRepository
                .findByEmail(request.getEmail()).orElse(null);

        // M7 fix: previously this branch threw EmailAlreadyExistsException
        // — a response shape distinguishable from every other outcome —
        // which let anyone confirm whether an email had a verified
        // SecureBank account just by submitting a registration attempt
        // against it. Now every path below returns the identical
        // "check your email" UserResponse shape regardless of whether the
        // email was new, already registered-but-unverified, or already
        // verified. The real accountholder gets a security alert instead
        // of the caller getting a different response.
        //
        // M7 residual fix: the response below is built from the
        // SUBMITTED request fields, not existingUser — returning
        // existingUser's real firstName/lastName/id/role/userStatus was a
        // bigger leak than the 409 it replaced (anyone who knew/guessed a
        // registered email could recover that customer's real name and
        // internal user ID, unauthenticated, in a normal 200 response).
        // Echoing back exactly what the caller submitted keeps this
        // response indistinguishable from a genuine new signup — id is
        // omitted for the same reason a new signup's real id shouldn't be
        // guessable either way.
        if (existingUser != null && existingUser.isEmailVerified()) {

            notificationPublisher.publishDuplicateRegistrationAlert(
                    existingUser.getEmail());

            return UserResponse.builder()
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .email(request.getEmail())
                    .role(Role.CUSTOMER)
                    .userStatus(UserStatus.PENDING_KYC)
                    .emailVerified(false)
                    .build();
        }

        if (existingUser != null) {

            String otp = otpService.generateAndSaveOtp(
                    existingUser.getEmail(), OtpPurpose.EMAIL_VERIFICATION);

            notificationPublisher.publishOtpEmail(existingUser.getEmail(), otp, "Email Verification");

            return UserResponse.builder()
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .email(request.getEmail())
                    .role(Role.CUSTOMER)
                    .userStatus(UserStatus.PENDING_KYC)
                    .emailVerified(false)
                    .build();
        }

        User user = new User();
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.CUSTOMER);
        user.setUserStatus(UserStatus.PENDING_KYC);

        User savedUser = userRepository.save(user);

        String otp = otpService.generateAndSaveOtp(
                savedUser.getEmail(), OtpPurpose.EMAIL_VERIFICATION);

        notificationPublisher.publishOtpEmail(savedUser.getEmail(), otp, "Email Verification");

        return UserResponse.builder()
                .id(savedUser.getId())
                .firstName(savedUser.getFirstName())
                .lastName(savedUser.getLastName())
                .email(savedUser.getEmail())
                .role(savedUser.getRole())
                .userStatus(savedUser.getUserStatus())
                .emailVerified(savedUser.isEmailVerified())
                .build();
    }
    @Auditable(action = "LOGIN")
    @Transactional
    @Override
    public AuthResponse login(LoginRequest request) {
        // rate limit by EMAIL, not IP — this specifically protects
        // against brute-forcing ONE account's password, regardless
        // of how many different IPs the attacker uses
        String rateLimitKey = "login:" + request.getEmail();

        boolean allowed = rateLimiterService.tryConsume(
                rateLimitKey,
                10,                          // 5 attempts
                Duration.ofMinutes(15));    // per 15 minutes

        if (!allowed) {
            throw new RateLimitExceededException(
                    "Too many login attempts. Please try again later.");
        }

        // M2 fix: the email-keyed limit above (and the 5-failure account
        // lock further down) protect one account from being brute-forced
        // — but keyed purely on email, with no cost to an attacker who
        // instead grinds through many DIFFERENT customers' emails from one
        // machine to lock them all out (a free griefing tool, per the
        // audit). This second limiter is keyed on source IP instead:
        // deliberately generous, since a shared office/NAT IP with several
        // real users failing their own logins is normal — but a single IP
        // hitting this many login attempts across (implicitly) many
        // different target accounts is a strong signal of scripted
        // enumeration, not a person mistyping their password.
        String ipRateLimitKey = "login-ip:" + ClientIpUtils.getClientIp();
        boolean withinIpLimit = rateLimiterService.tryConsume(
                ipRateLimitKey,
                30,
                Duration.ofMinutes(15));

        if (!withinIpLimit) {
            throw new RateLimitExceededException(
                    "Too many login attempts from this network. " +
                            "Please try again later.");
        }

        // Spring Security handles everything:
        // → calls CustomUserDetailsService.loadUserByUsername()
        // → checks SUSPENDED/CLOSED status
        // → verifies password with BCrypt
        // → throws exceptions if anything fails
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );
        }
        catch (AccountSuspendedException | AccountClosedException ex) {
            throw ex;
        }
        catch (org.springframework.security.authentication.DisabledException ex) {

            // isEnabled() returned false — could be SUSPENDED or CLOSED,
            // look up which one to give the correct specific message
            User user = userRepository.findByEmail(request.getEmail()).orElse(null);

            if (user != null && user.getUserStatus() == UserStatus.CLOSED) {
                throw new AccountClosedException("This account has been closed.");
            }
            if (user != null && user.getUserStatus() == UserStatus.SUSPENDED) {
                throw new AccountSuspendedException(
                        "Your account has been suspended. Contact support.");
            }

            if (user != null && !user.isEmailVerified()) {
                throw new EmailNotVerifiedException(
                        "Please verify your email before logging in. " +
                                "Check your inbox for the verification OTP, or " +
                                "request a new one.");
            }

            throw new AccountSuspendedException(
                    "Your account has been suspended. Contact support.");

        }
        catch (org.springframework.security.authentication.LockedException ex) {

            // Spring's own LockedException doesn't carry OUR custom
            // "minutes remaining" message — we need to compute that here instead
            User user = userRepository.findByEmail(request.getEmail()).orElse(null);

            String message = "Account is locked due to too many failed login attempts.";

            if (user != null && user.getLockedUntil() != null) {
                long minutesRemaining = java.time.Duration.between(
                        LocalDateTime.now(), user.getLockedUntil()).toMinutes();
                message += " Try again in " + (minutesRemaining + 1) + " minute(s).";
            }

            throw new AccountLockedException(message);

        }
        catch (AuthenticationException ex) {

            // calling through the INTERFACE, on a DIFFERENT bean —
            // this goes through Spring's proxy correctly,
            // REQUIRES_NEW actually takes effect now
            loginAttemptService.recordFailedAttempt(request.getEmail());
            throw new InvalidCredentialsException(
                    "Invalid email or password");
        }

        // NEW — successful login resets the counter back to 0
        loginAttemptService.resetAttempts(request.getEmail());
        // if we reach here — authentication succeeded
        // load user for token generation + refresh token
        User user = userRepository
                .findByEmail(request.getEmail())
                .orElseThrow(() ->
                        new InvalidCredentialsException(
                                "Invalid email or password"));

        String accessToken = jwtService.generateToken(
                user.getEmail(), "ROLE_" + user.getRole().name() ,user.getId() , user.getUserStatus());

        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        return new AuthResponse(
                accessToken, refreshToken.getToken(),
                user.getUserStatus(), user.getRole());
    }

    @Override
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {

        RefreshToken newRefreshToken = refreshTokenService
                .rotateToken(request.getRefreshToken());

        String newAccessToken = jwtService.generateToken(
                newRefreshToken.getUser().getEmail(),
                "ROLE_" + newRefreshToken.getUser().getRole().name() ,newRefreshToken.getUser().getId(),newRefreshToken.getUser().getUserStatus());

        return new AuthResponse(
                newAccessToken,
                newRefreshToken.getToken(),       // ← NEW token returned, not the old one
                newRefreshToken.getUser().getUserStatus(),
                newRefreshToken.getUser().getRole());
    }

    @Override
    @Transactional
    public void logout(RefreshTokenRequest request) {
        refreshTokenService.revokeByToken(request.getRefreshToken());
    }

    @Override
    public void forgotPassword(EmailOnlyRequest request) {

        // always return success even if email doesn't exist
        // security: don't reveal which emails are registered
        boolean userExists = userRepository
                .existsByEmail(request.getEmail());

        if (userExists) {
            String otp = otpService.generateAndSaveOtp(
                    request.getEmail(), OtpPurpose.PASSWORD_RESET);   // ← added purpose
//            emailService.sendOtpEmail(request.getEmail(), otp ,"Password Reset");
            notificationPublisher.publishOtpEmail(request.getEmail(), otp ,"Password Reset");

        }

        // if user doesn't exist, we do nothing but still return success
        // caller never knows if email was registered or not
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {

        // C3 fix: without this, an attacker who knows (or guesses) an
        // email could script through all 1,000,000 six-digit OTP
        // combinations well within the OTP's 10-minute lifetime. Rate
        // limit is keyed by email — same reasoning as login: this
        // specifically protects one account regardless of how many IPs
        // the attempts come from. The per-OTP failed-attempt lockout in
        // OtpServiceImpl backs this up even within the allowed attempts.
        String rateLimitKey = "reset-password:" + request.getEmail();
        boolean allowed = rateLimiterService.tryConsume(
                rateLimitKey, 5, Duration.ofMinutes(10));
        if (!allowed) {
            throw new RateLimitExceededException(
                    "Too many password reset attempts. Please try again later.");
        }

        // 1. verify user exists
        User user = userRepository
                .findByEmail(request.getEmail())
                .orElseThrow(() ->
                        new InvalidOtpException("Invalid or expired OTP"));
        // same error — don't reveal email existence

        // 2. verify OTP
        otpService.verifyOtp(request.getEmail(), request.getOtp(), OtpPurpose.PASSWORD_RESET);

        // 3. update password
        user.setPassword(
                passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // 4. invalidate OTP so it can't be reused
        otpService.invalidateOtps(request.getEmail(), OtpPurpose.PASSWORD_RESET);

        // 5. revoke all refresh tokens — force re-login on all devices
        refreshTokenService.revokeAllUserTokens(user);
    }
    @Auditable(action = "CREATE_STAFF")
    @Override
    @Transactional
    public UserResponse createStaffUser(CreateStaffRequest request) {

        if (request.getRole() != Role.TELLER && request.getRole() != Role.ADMIN) {
            throw new InvalidRoleException(
                    "Role must be TELLER or ADMIN for staff creation");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException("Email already exists");
        }

        User user = new User();
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPhoneNumber(request.getPhoneNumber());
        user.setRole(request.getRole());
        //email verified because admin adding it
        user.setEmailVerified(true);

        // staff accounts skip KYC entirely — verified by employment,
        // not by the customer KYC process
        user.setUserStatus(UserStatus.ACTIVE);

        User savedUser = userRepository.save(user);

        return UserResponse.builder()
                .id(savedUser.getId())
                .firstName(savedUser.getFirstName())
                .lastName(savedUser.getLastName())
                .email(savedUser.getEmail())
                .role(savedUser.getRole())
                .userStatus(savedUser.getUserStatus())
                .build();
    }
// AuthServiceImpl — two new methods added, using the SAME
// otpService and emailService it already has injected

    @Override
    public void sendEmailVerificationOtp(String email) {

        // C3 residual fix: this is the one OTP-issuing endpoint that was
        // missed when rate limiting was added elsewhere (login,
        // resetPassword, verifyEmail all have it). It's public and
        // unauthenticated, so without this anyone can email-bomb an
        // arbitrary address, and repeatedly generating fresh OTPs resets
        // the per-OTP failed-attempt lockout each time — reopening a
        // slow brute-force path that lockout alone doesn't close.
        String rateLimitKey = "send-otp:" + email;
        boolean allowed = rateLimiterService.tryConsume(
                rateLimitKey, 5, Duration.ofMinutes(10));
        if (!allowed) {
            throw new RateLimitExceededException(
                    "Too many requests. Please try again later.");
        }

        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null || user.isEmailVerified()) {
            return;   // silent, same information-hiding principle as forgotPassword
        }

        String otp = otpService.generateAndSaveOtp(
                email, OtpPurpose.EMAIL_VERIFICATION);
        notificationPublisher.publishOtpEmail(email, otp ,"Email Verification");
    }

    @Override
    @Transactional
    public void verifyEmail(String email, String otp) {

        // C3 fix: same brute-force protection as resetPassword — without
        // it, EMAIL_VERIFICATION OTPs are guessable within their 10-minute
        // window.
        String rateLimitKey = "verify-email:" + email;
        boolean allowed = rateLimiterService.tryConsume(
                rateLimitKey, 5, Duration.ofMinutes(10));
        if (!allowed) {
            throw new RateLimitExceededException(
                    "Too many verification attempts. Please try again later.");
        }

        otpService.verifyOtp(email, otp, OtpPurpose.EMAIL_VERIFICATION);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidOtpException("User not found"));

        user.setEmailVerified(true);
        userRepository.save(user);
    }

    @Override
    public List<ActiveSessionResponse> getActiveSessions(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return refreshTokenService.getActiveSessions(user, null);
    }

    @Override
    public void revokeSession(Long userId, String tokenFamily) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        refreshTokenService.revokeSession(user, tokenFamily);
    }

    @Override
    public void logoutAllDevices(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        refreshTokenService.revokeAllUserTokens(user);
    }
}
