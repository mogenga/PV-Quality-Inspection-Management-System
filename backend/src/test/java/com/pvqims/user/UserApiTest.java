package com.pvqims.user;

import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;

    @BeforeEach
    void clearRedis() {
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    private String tokenFor(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.token");
    }

    @Test
    void inspectorCannotReachUserManagement() throws Exception {
        String t = tokenFor("inspector", "Inspector@123");
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + t))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.code").value(40300));
        mvc.perform(post("/api/users").header("Authorization", "Bearer " + t)
                .contentType(APPLICATION_JSON)
                .content("{\"username\":\"x1\",\"password\":\"Passw0rd!\",\"displayName\":\"X\",\"role\":\"INSPECTOR\"}"))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    void adminCreatesUserWhoCanThenLogIn() throws Exception {
        String admin = tokenFor("admin", "Admin@123");
        mvc.perform(post("/api/users").header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON)
                .content("{\"username\":\"wang\",\"password\":\"Wang@1234\",\"displayName\":\"王工\",\"role\":\"ENGINEER\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.id").isNumber())
           .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                .content("{\"username\":\"wang\",\"password\":\"Wang@1234\"}"))
           .andExpect(status().isOk());
    }

    @Test
    void duplicateUsernameReturns400NotServerError() throws Exception {
        String admin = tokenFor("admin", "Admin@123");
        mvc.perform(post("/api/users").header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"Passw0rd!\",\"displayName\":\"重名\",\"role\":\"ADMIN\"}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value(40002))
           .andExpect(jsonPath("$.message").value("用户名已存在"));

        // 仅大小写不同也算重复（utf8mb4_0900_ai_ci 大小写不敏感）
        mvc.perform(post("/api/users").header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON)
                .content("{\"username\":\"ADMIN\",\"password\":\"Passw0rd!\",\"displayName\":\"重名\",\"role\":\"ADMIN\"}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value(40002));
    }

    @Test
    void resetPasswordMakesOldPasswordStopWorking() throws Exception {
        String admin = tokenFor("admin", "Admin@123");
        long id = jdbc.queryForObject("SELECT id FROM app_user WHERE username = 'inspector'", Long.class);

        mvc.perform(patch("/api/users/{id}/password", id).header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON).content("{\"newPassword\":\"NewInsp@123\"}"))
           .andExpect(status().isOk());

        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                .content("{\"username\":\"inspector\",\"password\":\"Inspector@123\"}"))
           .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                .content("{\"username\":\"inspector\",\"password\":\"NewInsp@123\"}"))
           .andExpect(status().isOk());
    }

    @Test
    void disablingUserPreventsSubsequentLogin() throws Exception {
        String admin = tokenFor("admin", "Admin@123");
        long id = jdbc.queryForObject("SELECT id FROM app_user WHERE username = 'inspector'", Long.class);

        mvc.perform(put("/api/users/{id}", id).header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON)
                .content("{\"displayName\":\"李检验员\",\"role\":\"INSPECTOR\",\"isActive\":false}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.isActive").value(false));

        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                .content("{\"username\":\"inspector\",\"password\":\"Inspector@123\"}"))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void chineseAndEmojiDisplayNameRoundTripWithoutMojibake() throws Exception {
        String admin = tokenFor("admin", "Admin@123");
        String displayName = "王小明😀";

        String created = mvc.perform(post("/api/users").header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of(
                    "username", "wangxm", "password", "Wangxm@123",
                    "displayName", displayName, "role", "INSPECTOR"))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(created, "$.data.displayName")).isEqualTo(displayName);

        mvc.perform(get("/api/users").param("keyword", "wangxm").header("Authorization", "Bearer " + admin))
           .andExpect(jsonPath("$.data.records[0].displayName").value(displayName));
    }

    @Test
    void listWithoutFiltersReturnsPageEnvelopeAndFiltersAreGrouped() throws Exception {
        String admin = tokenFor("admin", "Admin@123");

        // 无条件查询是用户管理页首次打开的真实请求。
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + admin))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.page").value(1))
           .andExpect(jsonPath("$.data.pageSize").value(10))
           .andExpect(jsonPath("$.data.total").value(3))
           .andExpect(jsonPath("$.data.records.length()").value(3));

        // keyword 命中 username=admin，但 role 过滤为 INSPECTOR：没有括号分组时 OR 会绕过 role 条件返回 1。
        mvc.perform(get("/api/users").param("keyword", "admin").param("role", "INSPECTOR")
                .header("Authorization", "Bearer " + admin))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.total").value(0));

        // keyword 命中 display_name=李检验员。
        mvc.perform(get("/api/users").param("keyword", "李").header("Authorization", "Bearer " + admin))
           .andExpect(jsonPath("$.data.total").value(1))
           .andExpect(jsonPath("$.data.records[0].username").value("inspector"));

        mvc.perform(get("/api/users").param("role", "ENGINEER").header("Authorization", "Bearer " + admin))
           .andExpect(jsonPath("$.data.total").value(1))
           .andExpect(jsonPath("$.data.records[0].username").value("engineer"));
    }

    @Test
    void unknownUserAndInvalidRoleAreRejected() throws Exception {
        String admin = tokenFor("admin", "Admin@123");

        mvc.perform(put("/api/users/{id}", 999999L).header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON)
                .content("{\"displayName\":\"无此人\",\"role\":\"INSPECTOR\",\"isActive\":true}"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.code").value(40400));

        mvc.perform(patch("/api/users/{id}/password", 999999L).header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON).content("{\"newPassword\":\"Passw0rd!\"}"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.code").value(40400));

        mvc.perform(post("/api/users").header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON)
                .content("{\"username\":\"root\",\"password\":\"Passw0rd!\",\"displayName\":\"越权角色\",\"role\":\"SUPERUSER\"}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void updateDoesNotTouchPasswordHash() throws Exception {
        String admin = tokenFor("admin", "Admin@123");
        long id = jdbc.queryForObject("SELECT id FROM app_user WHERE username = 'inspector'", Long.class);
        String before = jdbc.queryForObject("SELECT password_hash FROM app_user WHERE id = ?", String.class, id);

        mvc.perform(put("/api/users/{id}", id).header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON)
                .content("{\"displayName\":\"李检验员（改）\",\"role\":\"ENGINEER\",\"isActive\":true}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.displayName").value("李检验员（改）"))
           .andExpect(jsonPath("$.data.role").value("ENGINEER"))
           .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        String after = jdbc.queryForObject("SELECT password_hash FROM app_user WHERE id = ?", String.class, id);
        assertThat(after).isEqualTo(before);
    }

    @Test
    void overlongDisplayNameIsRejectedWithValidationError() throws Exception {
        String admin = tokenFor("admin", "Admin@123");
        mvc.perform(post("/api/users").header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON)
                .content("{\"username\":\"toolong\",\"password\":\"Passw0rd!\",\"displayName\":\"%s\",\"role\":\"ADMIN\"}"
                    .formatted("很".repeat(60))))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value(40001))
           // spring.web.locale 固定为 zh_CN，校验消息不再随 JVM 默认语言变成英文。
           .andExpect(jsonPath("$.message").value("个数必须在0和50之间"));
    }

    /**
     * 25 个汉字 = 75 UTF-8 字节，字符数未超 @Size(max = 64)，但 BCrypt 只接受 ≤ 72 字节。
     */
    private static final String OVERLONG_BYTES_PASSWORD = "密".repeat(25);

    @Test
    void createRejectsPasswordOver72Utf8BytesWith400() throws Exception {
        String admin = tokenFor("admin", "Admin@123");
        mvc.perform(post("/api/users").header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of(
                    "username", "cjkpass", "password", OVERLONG_BYTES_PASSWORD,
                    "displayName", "密码超长", "role", "INSPECTOR"))))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value(40001))
           .andExpect(jsonPath("$.message").value("密码超过 72 字节，请缩短"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE username = 'cjkpass'", Long.class))
            .isZero();
    }

    @Test
    void resetPasswordRejectsPasswordOver72Utf8BytesWith400() throws Exception {
        String admin = tokenFor("admin", "Admin@123");
        long id = jdbc.queryForObject("SELECT id FROM app_user WHERE username = 'inspector'", Long.class);
        String before = jdbc.queryForObject("SELECT password_hash FROM app_user WHERE id = ?", String.class, id);

        mvc.perform(patch("/api/users/{id}/password", id).header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("newPassword", OVERLONG_BYTES_PASSWORD))))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value(40001))
           .andExpect(jsonPath("$.message").value("密码超过 72 字节，请缩短"));

        assertThat(jdbc.queryForObject("SELECT password_hash FROM app_user WHERE id = ?", String.class, id))
            .isEqualTo(before);
    }

    /**
     * 停用、改角色、重置密码都必须立刻撤销目标用户所有已签发的令牌。
     */
    @Test
    void disablingUserRevokesLiveSession() throws Exception {
        String admin = tokenFor("admin", "Admin@123");
        createUser(admin, "wang", "Wang@1234", "ENGINEER");
        String wang = tokenFor("wang", "Wang@1234");
        long id = jdbc.queryForObject("SELECT id FROM app_user WHERE username = 'wang'", Long.class);

        mvc.perform(get("/api/users").header("Authorization", "Bearer " + wang))
           .andExpect(status().isForbidden());

        mvc.perform(put("/api/users/{id}", id).header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON)
                .content("{\"displayName\":\"王工\",\"role\":\"ENGINEER\",\"isActive\":false}"))
           .andExpect(status().isOk());

        mvc.perform(get("/api/users").header("Authorization", "Bearer " + wang))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void resettingPasswordRevokesTargetUsersLiveSession() throws Exception {
        String admin = tokenFor("admin", "Admin@123");
        createUser(admin, "wang", "Wang@1234", "ENGINEER");
        String wang = tokenFor("wang", "Wang@1234");
        long id = jdbc.queryForObject("SELECT id FROM app_user WHERE username = 'wang'", Long.class);

        mvc.perform(patch("/api/users/{id}/password", id).header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON).content("{\"newPassword\":\"Wang@5678\"}"))
           .andExpect(status().isOk());

        mvc.perform(get("/api/users").header("Authorization", "Bearer " + wang))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void changingRoleRevokesLiveSession() throws Exception {
        String admin = tokenFor("admin", "Admin@123");
        createUser(admin, "wang", "Wang@1234", "ENGINEER");
        String wang = tokenFor("wang", "Wang@1234");
        long id = jdbc.queryForObject("SELECT id FROM app_user WHERE username = 'wang'", Long.class);

        mvc.perform(put("/api/users/{id}", id).header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON)
                .content("{\"displayName\":\"王工\",\"role\":\"INSPECTOR\",\"isActive\":true}"))
           .andExpect(status().isOk());

        mvc.perform(get("/api/users").header("Authorization", "Bearer " + wang))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void changingOnlyDisplayNameKeepsLiveSession() throws Exception {
        String admin = tokenFor("admin", "Admin@123");
        createUser(admin, "wang", "Wang@1234", "ENGINEER");
        String wang = tokenFor("wang", "Wang@1234");
        long id = jdbc.queryForObject("SELECT id FROM app_user WHERE username = 'wang'", Long.class);

        mvc.perform(put("/api/users/{id}", id).header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON)
                .content("{\"displayName\":\"王工（改）\",\"role\":\"ENGINEER\",\"isActive\":true}"))
           .andExpect(status().isOk());

        // 改显示名不涉及权限与凭据，不应把用户踢下线。
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + wang))
           .andExpect(status().isForbidden());
    }

    @Test
    void pageSizeIsCappedAndNegativeFallsBackToTheCap() throws Exception {
        String admin = tokenFor("admin", "Admin@123");

        mvc.perform(get("/api/users").param("pageSize", "100000").header("Authorization", "Bearer " + admin))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.pageSize").value(100));

        // 负数不加限制会让 MyBatis-Plus 去掉 LIMIT 返回整表。
        mvc.perform(get("/api/users").param("pageSize", "-1").header("Authorization", "Bearer " + admin))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.pageSize").value(100))
           .andExpect(jsonPath("$.data.records.length()").value(3));
    }

    private void createUser(String adminToken, String username, String password, String role) throws Exception {
        mvc.perform(post("/api/users").header("Authorization", "Bearer " + adminToken)
                .contentType(APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of(
                    "username", username, "password", password,
                    "displayName", "王工", "role", role))))
           .andExpect(status().isOk());
    }
}
