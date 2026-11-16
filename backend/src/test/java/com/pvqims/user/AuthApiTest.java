package com.pvqims.user;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthApiTest {

    @Autowired MockMvc mvc;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean PasswordEncoder passwordEncoder;

    @BeforeEach
    void clearRedis() {
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    private String loginBody(String username, String password) {
        return "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password);
    }

    @Test
    void seededAdminCanLogInAndPasswordHashIsNeverReturned() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                .content(loginBody("admin", "Admin@123")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.code").value(0))
           .andExpect(jsonPath("$.data.token").isNotEmpty())
           .andExpect(jsonPath("$.data.user.role").value("ADMIN"))
           .andExpect(jsonPath("$.data.user.displayName").value("系统管理员"))
           .andExpect(jsonPath("$.data.user.passwordHash").doesNotExist());
    }

    @Test
    void wrongPasswordAndUnknownUserBothReturnSameGeneric401() throws Exception {
        String wrong = mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                .content(loginBody("admin", "wrong-password")))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(40100))
            .andReturn().getResponse().getContentAsString();

        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                .content(loginBody("no-such-user", "whatever")))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.code").value(40100))
           .andExpect(content().json(wrong));
    }

    @Test
    void disabledAccountCannotLogIn() throws Exception {
        jdbc.update("UPDATE app_user SET is_active = 0 WHERE username = 'inspector'");
        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                .content(loginBody("inspector", "Inspector@123")))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void unknownUserStillPaysOneBcryptComparison() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                .content(loginBody("no-such-user", "whatever")))
           .andExpect(status().isUnauthorized());

        verify(passwordEncoder, times(1)).matches(eq("whatever"), anyString());
    }

    @Test
    void disabledAccountStillPaysOneBcryptComparison() throws Exception {
        jdbc.update("UPDATE app_user SET is_active = 0 WHERE username = 'inspector'");
        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                .content(loginBody("inspector", "Inspector@123")))
           .andExpect(status().isUnauthorized());

        verify(passwordEncoder, times(1)).matches(eq("Inspector@123"), anyString());
    }

    @Test
    void requestWithoutTokenGetsJson401NotHtml() throws Exception {
        mvc.perform(get("/api/users"))
           .andExpect(status().isUnauthorized())
           .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
           .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void malformedAuthorizationHeaderGets401() throws Exception {
        mvc.perform(get("/api/users").header("Authorization", "Token abc"))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void garbageBearerTokenGetsJson401NotHtmlOr500() throws Exception {
        mvc.perform(get("/api/users").header("Authorization", "Bearer not-a-real-token"))
           .andExpect(status().isUnauthorized())
           .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
           .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void emptyBearerTokenGetsJson401NotHtmlOr500() throws Exception {
        mvc.perform(get("/api/users").header("Authorization", "Bearer "))
           .andExpect(status().isUnauthorized())
           .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
           .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void logoutRevokesTokenAndOldTokenIsRejected() throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                .content(loginBody("engineer", "Engineer@123")))
            .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.data.token");

        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
           .andExpect(status().isOk());

        mvc.perform(get("/api/users").header("Authorization", "Bearer " + token))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void tokenWhoseRedisKeyDisappearedIsRejected() throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                .content(loginBody("engineer", "Engineer@123")))
            .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.data.token");

        redis.getConnectionFactory().getConnection().serverCommands().flushDb();

        mvc.perform(get("/api/users").header("Authorization", "Bearer " + token))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.code").value(40101));
    }
}
