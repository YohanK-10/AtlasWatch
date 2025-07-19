package com.atlasmind.ai_travel_recommendation.service;

import com.atlasmind.ai_travel_recommendation.dto.LoginUserDto;
import com.atlasmind.ai_travel_recommendation.dto.RegisterUserDto;
import com.atlasmind.ai_travel_recommendation.dto.VerifyUserDto;
import com.atlasmind.ai_travel_recommendation.models.User;
import com.atlasmind.ai_travel_recommendation.repository.UserRepository;
import com.atlasmind.ai_travel_recommendation.util.PasswordValidator;
import jakarta.mail.MessagingException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    public User signUp(RegisterUserDto signUpInfo) {
        if (!PasswordValidator.isStrong(signUpInfo.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                              "Password must be at least 8 characters long and contain uppercase, lowercase, number, and symbol.");
        }
        // Optional is a container that may or may not hold a user. Need to unwrap it to check.
        Optional<User> optionalUser1 = userRepository.findByEmail(signUpInfo.getEmail());
        Optional<User> optionalUser2 = userRepository.findByUsername(signUpInfo.getUsername());
        if (optionalUser1.isPresent() && optionalUser2.isPresent()) {
            User userByMail = optionalUser1.get();
            User userByUsername = optionalUser2.get();
            if (userByMail.equals(userByUsername)) {
                if (userByMail.isEnabled()) throw new ResponseStatusException(HttpStatus.CONFLICT, "This email is already registered and verified. Please log in.");
                return theLogicSendVerificationEmail(userByMail);
            } else throw new ResponseStatusException(HttpStatus.CONFLICT, "Username and email are both taken.");
        } else if (optionalUser1.isPresent()) {
            User userByMail = optionalUser1.get();
            if (userByMail.isEnabled()) throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists!");
            userByMail.setUsername(signUpInfo.getUsername());
            userByMail.setPassword(passwordEncoder.encode(signUpInfo.getPassword()));
            return theLogicSendVerificationEmail(userByMail);
        } else if (optionalUser2.isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already taken!!");
        } else {
            // Hashes the password.
            User user = new User(signUpInfo.getUsername(), signUpInfo.getEmail(), passwordEncoder.encode(signUpInfo.getPassword()));
            return theLogicSendVerificationEmail(user); // Returns the saved user object.
        }
    }

    public User theLogicSendVerificationEmail (User theUser) {
        theUser.setVerificationCode(generateVerificationCode());
        theUser.setExpirationTimeOfVerificationCode(LocalDateTime.now().plusMinutes(5));
        theUser.setEnable(false); // To check if the user is allowed to log in and access the system. First need to verify, so set to false.
        sendVerificationEmail(theUser);
        return userRepository.save(theUser);
    }

    public User authenticate(LoginUserDto loginInfo) {
        User user = userRepository.findByUsername(loginInfo.getLoginInfo())
                .or(() -> userRepository.findByEmail(loginInfo.getLoginInfo()))
                .orElseThrow(() -> new RuntimeException("User not found!"));

        if (!user.isEnabled()) throw new DisabledException("Account is not verified!!");
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken( // This extends a class which implements Authentication.
                loginInfo.getLoginInfo(),
                loginInfo.getPassword()
        ));
        return user;
    }

    public void verifyUser(VerifyUserDto input) {
        Optional<User> optionalUser = userRepository.findByEmail(input.getEmail());
        if (optionalUser.isPresent()) { // Checks if it is not Null
            User user = optionalUser.get();
            if (user.getExpirationTimeOfVerificationCode().isBefore(LocalDateTime.now())) {
                throw new RuntimeException("The verification code is expired!!");
            }
            if (user.getVerificationCode().equals(input.getVerificationCode())) {
                user.setEnable(true);
                user.setVerificationCode(null);
                user.setExpirationTimeOfVerificationCode(null);
                userRepository.save(user);
            } else {
                throw new RuntimeException("Invalid verification code!!");
            }
        } else throw new RuntimeException("User not found!!");
    }

    public void resendVerificationCode(String email) {
        Optional<User> optionalUser = userRepository.findByEmail(email);
        if(optionalUser.isPresent()) {
            User user = optionalUser.get();
            if (user.isEnabled()) throw new RuntimeException("The user was already verified!!");
            user.setVerificationCode(generateVerificationCode());
            user.setExpirationTimeOfVerificationCode(LocalDateTime.now().plusMinutes(5));
            sendVerificationEmail(user);
            userRepository.save(user);
        } else throw new RuntimeException("User was not found!!");
    }

    public void sendVerificationEmail(User user) {
        String subject = "Account Verification";
        String verificationCode = user.getVerificationCode();
        String htmlMessage = "<!DOCTYPE html>"
                + "<html>"
                + "<head><meta charset=\"UTF-8\"><title>Verify Your Email</title></head>"
                + "<body style=\"font-family: Arial, sans-serif; margin: 0; padding: 0;\">"
                + "  <div style=\"background-color: #f2f2f2; padding: 20px;\">"
                + "    <table align=\"center\" width=\"600\" style=\"background-color: #ffffff; padding: 20px;\">"
                + "      <tr>"
                + "        <td style=\"text-align: center; padding: 20px;\">"
                + "          <h2 style=\"color: #333;\">Verify Your Email</h2>"
                + "          <p style=\"color: #555; font-size: 16px;\">"
                + "            Use the verification code below to verify your email address:"
                + "          </p>"
                + "          <p style=\"font-size: 24px; font-weight: bold; color: #1a73e8; background-color: #f8f9fa; "
                + "                padding: 10px 20px; display: inline-block; border-radius: 5px;\">"
                +             verificationCode
                + "          </p>"
                + "          <p style=\"color: #555; font-size: 14px;\">"
                + "            If you didn't request this, please ignore this email."
                + "          </p>"
                + "        </td>"
                + "      </tr>"
                + "    </table>"
                + "  </div>"
                + "</body>"
                + "</html>";
        try {
            emailService.sendVerificationEmail(user.getEmail(), subject, htmlMessage);
        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }

    /**
     * Generates a secure, random verification code as a URL-safe Base64 string.
     * @return
     */
    private String generateVerificationCode() {
        byte[] randomnBytes = new byte[32]; // Creates the array
        new SecureRandom().nextBytes(randomnBytes); // Cryptographically strong bytes.
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomnBytes);
    }
}
