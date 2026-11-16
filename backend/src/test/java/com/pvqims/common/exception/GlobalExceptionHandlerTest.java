package com.pvqims.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.TEXT_PLAIN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 嵌套在测试类里的控制器会被 Boot 的 TestTypeExcludeFilter 排除，因此除 controllers 外还需显式 @Import。
 */
@WebMvcTest(controllers = GlobalExceptionHandlerTest.ProbeController.class)
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTest.ProbeController.class})
@AutoConfigureMockMvc(addFilters = false)
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void bizExceptionMapsToHttpStatusFromErrorCode() throws Exception {
        mvc.perform(get("/probe/biz"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(40002))
            .andExpect(jsonPath("$.message").value("用户名已存在"))
            .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void unexpectedExceptionHidesInternalDetail() throws Exception {
        mvc.perform(get("/probe/boom"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value(50000))
            .andExpect(jsonPath("$.message").value("服务器内部错误"))
            .andExpect(jsonPath("$.message").value(not(containsString("boom"))));
    }

    @Test
    void unknownPathReturns404EnvelopeNot500() throws Exception {
        mvc.perform(get("/probe/no-such-endpoint"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(40400))
            .andExpect(jsonPath("$.message").value("资源不存在"))
            .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void wrongMethodReturns405EnvelopeNot500() throws Exception {
        mvc.perform(get("/probe/post-only"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.code").value(40500))
            .andExpect(jsonPath("$.message").value("请求方法不支持"));
    }

    @Test
    void unsupportedContentTypeReturns415EnvelopeNot500() throws Exception {
        mvc.perform(post("/probe/post-only").contentType(TEXT_PLAIN).content("x"))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.code").value(41500))
            .andExpect(jsonPath("$.message").value("不支持的请求类型"));
    }

    @RestController
    static class ProbeController {

        @GetMapping("/probe/biz")
        void biz() {
            throw new BizException(ErrorCode.USERNAME_EXISTS);
        }

        @GetMapping("/probe/boom")
        void boom() {
            throw new IllegalStateException("boom");
        }

        @PostMapping(path = "/probe/post-only", consumes = APPLICATION_JSON_VALUE)
        void postOnly() {
        }
    }
}
