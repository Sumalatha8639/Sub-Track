package com.subtrack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.subtrack.notification.EmailMessage;
import com.subtrack.notification.EmailSender;
import com.subtrack.security.RateLimitFilter;
import com.subtrack.security.RefreshCookies;
import com.subtrack.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** End-to-end auth flow against the real test database (project plan §7.1). */
@AutoConfigureMockMvc
class AuthFlowIntegrationTest extends AbstractIntegrationTest {

    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");
    private static final String ORIGIN = "http://localhost:5173";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    @MockitoBean
    private EmailSender emailSender;

    @BeforeEach
    void resetLimits() {
        rateLimitFilter.reset();
    }

    @Test
    void registerVerifyLoginRefreshLogout() throws Exception {
        String email = uniqueEmail();

        // Register: logged in straight away, refresh cookie set, verification email sent
        MvcResult reg = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(email, "password1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.emailVerified").value(false))
                .andExpect(jsonPath("$.user.timezone").value("Asia/Kolkata"))
                .andExpect(cookie().httpOnly(RefreshCookies.NAME, true))
                .andExpect(cookie().path(RefreshCookies.NAME, "/api/auth"))
                .andReturn();
        String access = JsonPath.read(reg.getResponse().getContentAsString(), "$.accessToken");

        // Verify email with the link from the email
        String verifyToken = lastTokenSentTo(email);
        mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + verifyToken + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + verifyToken + "\"}"))
                .andExpect(status().isBadRequest()); // single use

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true));

        // Login, then refresh rotates the cookie
        MvcResult login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(email, "password1")))
                .andExpect(status().isOk())
                .andReturn();
        Cookie first = login.getResponse().getCookie(RefreshCookies.NAME);

        MvcResult refreshed = mvc.perform(post("/api/auth/refresh").cookie(first).header("Origin", ORIGIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        Cookie second = refreshed.getResponse().getCookie(RefreshCookies.NAME);
        assertThat(second.getValue()).isNotEqualTo(first.getValue());

        // Logout revokes the current refresh token
        mvc.perform(post("/api/auth/logout").cookie(second).header("Origin", ORIGIN))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/refresh").cookie(second).header("Origin", ORIGIN))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forgotAndResetPassword() throws Exception {
        String email = uniqueEmail();
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json(email, "password1"))).andExpect(status().isCreated());

        mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk());
        String resetToken = lastTokenSentTo(email);

        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + resetToken + "\",\"newPassword\":\"new-password-2\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(email, "password1"))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(email, "new-password-2"))).andExpect(status().isOk());
    }

    @Test
    void forgotPasswordForUnknownEmailStillReturns200() throws Exception {
        mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ghost-" + UUID.randomUUID() + "@example.com\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void duplicateRegistrationIs409AndValidationIs400() throws Exception {
        String email = uniqueEmail();
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json(email, "password1"))).andExpect(status().isCreated());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json(email.toUpperCase(), "password1"))).andExpect(status().isConflict());

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"not-an-email\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void profileUpdateAndChangePassword() throws Exception {
        String email = uniqueEmail();
        MvcResult reg = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json(email, "password1"))).andExpect(status().isCreated()).andReturn();
        String access = JsonPath.read(reg.getResponse().getContentAsString(), "$.accessToken");
        Cookie session = reg.getResponse().getCookie(RefreshCookies.NAME);

        mvc.perform(put("/api/users/me").header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Asha K\",\"phone\":\"+91 98765 43210\",\"timezone\":\"Asia/Dubai\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Asha K"))
                .andExpect(jsonPath("$.timezone").value("Asia/Dubai"));

        mvc.perform(post("/api/auth/change-password").header("Authorization", "Bearer " + access)
                        .cookie(session).header("Origin", ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"password2\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/auth/change-password").header("Authorization", "Bearer " + access)
                        .cookie(session).header("Origin", ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"password1\",\"newPassword\":\"password2\"}"))
                .andExpect(status().isNoContent());

        // Current session survives the password change
        mvc.perform(post("/api/auth/refresh").cookie(session).header("Origin", ORIGIN))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpointsNeedAValidToken() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer nonsense"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/change-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"a\",\"newPassword\":\"password2\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshFromAnotherSiteIsForbidden() throws Exception {
        mvc.perform(post("/api/auth/refresh").cookie(new Cookie(RefreshCookies.NAME, "x"))
                        .header("Origin", "https://evil.example"))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginIsRateLimited() throws Exception {
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(json("ghost@example.com", "password1"))).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json("ghost@example.com", "password1"))).andExpect(status().isTooManyRequests());
    }

    // ---- helpers -------------------------------------------------------------

    private String lastTokenSentTo(String email) {
        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender, atLeastOnce()).send(captor.capture());
        List<EmailMessage> mine = captor.getAllValues().stream().filter(m -> m.to().equals(email)).toList();
        assertThat(mine).isNotEmpty();
        Matcher m = TOKEN.matcher(mine.get(mine.size() - 1).text());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    private static String json(String email, String password) {
        return "{\"name\":\"Asha\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }
}
