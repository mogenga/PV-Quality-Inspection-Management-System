package com.pvqims.dashboard;

import java.time.LocalDate;
import java.util.List;

import com.pvqims.common.api.ApiResult;
import com.pvqims.dashboard.dto.DashboardDtos.BatchPassRate;
import com.pvqims.dashboard.dto.DashboardDtos.ControlChart;
import com.pvqims.dashboard.dto.DashboardDtos.DefectDuration;
import com.pvqims.dashboard.dto.DashboardDtos.GradeSceneCount;
import com.pvqims.dashboard.dto.DashboardDtos.InspectorWorkload;
import com.pvqims.dashboard.dto.DashboardDtos.ParetoItem;
import com.pvqims.dashboard.dto.DashboardDtos.ProcessComparison;
import com.pvqims.dashboard.dto.DashboardDtos.Summary;
import com.pvqims.dashboard.dto.DashboardDtos.TrendPoint;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 看板接口（FR-13、FR-14）。SRS 4.8：看板对全部已登录角色开放，故不加角色校验；
 * SecurityConfig 已要求所有请求通过认证。
 *
 * <p>筛选维度按 SRS 4.7：项目、批次、场景、工序、时间段。全部参数可选，缺省即不过滤。
 * 控制图只接受 standardId——标准本身已固定场景与工序，不再重复筛选。</p>
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public ApiResult<Summary> summary(@RequestParam(required = false) Long projectId,
                                      @RequestParam(required = false) Long batchId,
                                      @RequestParam(required = false) LocalDate from,
                                      @RequestParam(required = false) LocalDate to,
                                      @RequestParam(required = false) String scene,
                                      @RequestParam(required = false) String processName) {
        return ApiResult.ok(
            dashboardService.summary(new DashboardQuery(projectId, batchId, from, to, scene, processName)));
    }

    @GetMapping("/pass-rate-trend")
    public ApiResult<List<TrendPoint>> passRateTrend(@RequestParam(required = false) Long projectId,
                                                     @RequestParam(required = false) Long batchId,
                                                     @RequestParam(required = false) LocalDate from,
                                                     @RequestParam(required = false) LocalDate to,
                                                     @RequestParam(required = false) String scene,
                                                     @RequestParam(required = false) String processName) {
        return ApiResult.ok(
            dashboardService.passRateTrend(new DashboardQuery(projectId, batchId, from, to, scene, processName)));
    }

    @GetMapping("/process-comparison")
    public ApiResult<List<ProcessComparison>> processComparison(
        @RequestParam(required = false) Long projectId,
        @RequestParam(required = false) Long batchId,
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(required = false) String scene,
        @RequestParam(required = false) String processName) {
        return ApiResult.ok(dashboardService.processComparison(
            new DashboardQuery(projectId, batchId, from, to, scene, processName)));
    }

    @GetMapping("/pareto")
    public ApiResult<List<ParetoItem>> pareto(@RequestParam(required = false) Long projectId,
                                              @RequestParam(required = false) Long batchId,
                                              @RequestParam(required = false) LocalDate from,
                                              @RequestParam(required = false) LocalDate to,
                                              @RequestParam(required = false) String scene,
                                              @RequestParam(required = false) String processName) {
        return ApiResult.ok(
            dashboardService.pareto(new DashboardQuery(projectId, batchId, from, to, scene, processName)));
    }

    @GetMapping("/defect-grade")
    public ApiResult<List<GradeSceneCount>> defectGrade(@RequestParam(required = false) Long projectId,
                                                        @RequestParam(required = false) Long batchId,
                                                        @RequestParam(required = false) LocalDate from,
                                                        @RequestParam(required = false) LocalDate to,
                                                        @RequestParam(required = false) String scene,
                                                        @RequestParam(required = false) String processName) {
        return ApiResult.ok(dashboardService.defectGradeByScene(
            new DashboardQuery(projectId, batchId, from, to, scene, processName)));
    }

    @GetMapping("/defect-duration")
    public ApiResult<List<DefectDuration>> defectDuration(@RequestParam(required = false) Long projectId,
                                                          @RequestParam(required = false) Long batchId,
                                                          @RequestParam(required = false) LocalDate from,
                                                          @RequestParam(required = false) LocalDate to,
                                                          @RequestParam(required = false) String scene,
                                                          @RequestParam(required = false) String processName) {
        return ApiResult.ok(dashboardService.defectDuration(
            new DashboardQuery(projectId, batchId, from, to, scene, processName)));
    }

    @GetMapping("/control-chart")
    public ApiResult<ControlChart> controlChart(@RequestParam Long standardId) {
        return ApiResult.ok(dashboardService.controlChart(standardId));
    }

    @GetMapping("/inspector-workload")
    public ApiResult<List<InspectorWorkload>> inspectorWorkload(
        @RequestParam(required = false) Long projectId,
        @RequestParam(required = false) Long batchId,
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(required = false) String scene,
        @RequestParam(required = false) String processName) {
        return ApiResult.ok(dashboardService.inspectorWorkload(
            new DashboardQuery(projectId, batchId, from, to, scene, processName)));
    }

    @GetMapping("/batch-gauge")
    public ApiResult<List<BatchPassRate>> batchGauge(@RequestParam(required = false) Long projectId,
                                                     @RequestParam(required = false) Long batchId,
                                                     @RequestParam(required = false) LocalDate from,
                                                     @RequestParam(required = false) LocalDate to,
                                                     @RequestParam(required = false) String scene,
                                                     @RequestParam(required = false) String processName) {
        return ApiResult.ok(dashboardService.batchPassRates(
            new DashboardQuery(projectId, batchId, from, to, scene, processName)));
    }
}
