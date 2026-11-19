package com.pvqims.demo;

import com.pvqims.common.api.ApiResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 演示数据初始化接口（FR-16）。仅管理员可用（SRS 4.2、4.8）。
 * 数据是运行期写入的普通行，不是 Flyway 迁移（迁移已冻结）。
 */
@RestController
@RequestMapping("/api/demo")
public class DemoController {

    private final DemoDataService demoDataService;

    public DemoController(DemoDataService demoDataService) {
        this.demoDataService = demoDataService;
    }

    @PostMapping("/seed")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<DemoSeedResult> seed() {
        return ApiResult.ok(demoDataService.seed());
    }
}
