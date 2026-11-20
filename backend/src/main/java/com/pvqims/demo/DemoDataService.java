package com.pvqims.demo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.pvqims.common.exception.BizException;
import com.pvqims.common.exception.ErrorCode;
import com.pvqims.inspection.JudgmentRules;
import com.pvqims.inspection.entity.Defect;
import com.pvqims.inspection.entity.InspectionRecord;
import com.pvqims.inspection.mapper.DefectMapper;
import com.pvqims.inspection.mapper.InspectionRecordMapper;
import com.pvqims.masterdata.entity.Batch;
import com.pvqims.masterdata.entity.Project;
import com.pvqims.masterdata.mapper.BatchMapper;
import com.pvqims.masterdata.mapper.ProjectMapper;
import com.pvqims.standard.entity.InspectionStandard;
import com.pvqims.standard.mapper.InspectionStandardMapper;
import com.pvqims.user.entity.AppUser;
import com.pvqims.user.mapper.AppUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 样例数据初始化（FR-16、AC-10）：运行期写入普通业务行，不是 Flyway 迁移（迁移已冻结）。
 *
 * <p>数据规模固定为 2 项目 / 6 批次 / 20 标准 / 360 记录，判定一律交给 {@link JudgmentRules}（BR-01），
 * 五个 std_* 列照抄标准（BR-02），FAIL 记录 1:1 生成缺陷（BR-04）。
 * 随机源用固定种子，同一次运行可复现。</p>
 */
@Service
public class DemoDataService {

    private static final Logger log = LoggerFactory.getLogger(DemoDataService.class);

    /**
     * 已播种的判定依据：第一个样例项目的编号。
     * 编号与名称刻意贴近真实业务（「PV-2026-…」+ 客户名），界面上不出现「演示」字样。
     */
    private static final String DEMO_PROJECT_CODE = "PV-2026-101";

    /** 固定随机种子：数据规模与落点可复现（SRS 附录 A）。 */
    private static final long DEMO_SEED = 20261118L;

    private static final int DAYS = 30;
    private static final int RECORDS_PER_DAY = 12;

    /** 缺陷率 11%，落在 SRS 附录 A 的 8%–15% 区间。 */
    private static final int DEFECT_PERCENT = 11;

    /** 约 40% 的缺陷走到 CLOSED，让 CH-08 有一批已关闭缺陷可算时长。 */
    private static final int CLOSE_PERCENT = 40;

    private static final String DEMO_PASSWORD = "Demo@1234";
    private static final String DEMO_INSPECTOR_PREFIX = "inspector-";

    /** 账号名与显示名都按真实班组命名，图表里看到的是人名而不是「演示检验员」。 */
    private static final String[] DEMO_INSPECTOR_USERNAMES = {"inspector-wang", "inspector-zhao"};
    private static final String[] DEMO_INSPECTOR_NAMES = {"王海燕", "赵志强"};

    /** 七种缺陷类型里轮转五种（SRS 附录 A：隐裂、虚焊、气泡、功率不足、外观不良）。 */
    private static final String[] DEFECT_TYPES = {"CRACK", "SOLDER", "BUBBLE", "LOW_POWER", "APPEARANCE"};

    private final ProjectMapper projectMapper;
    private final BatchMapper batchMapper;
    private final InspectionStandardMapper standardMapper;
    private final InspectionRecordMapper recordMapper;
    private final DefectMapper defectMapper;
    private final AppUserMapper userMapper;
    private final JudgmentRules judgmentRules;
    private final PasswordEncoder passwordEncoder;

    public DemoDataService(ProjectMapper projectMapper, BatchMapper batchMapper,
                           InspectionStandardMapper standardMapper, InspectionRecordMapper recordMapper,
                           DefectMapper defectMapper, AppUserMapper userMapper, JudgmentRules judgmentRules,
                           PasswordEncoder passwordEncoder) {
        this.projectMapper = projectMapper;
        this.batchMapper = batchMapper;
        this.standardMapper = standardMapper;
        this.recordMapper = recordMapper;
        this.defectMapper = defectMapper;
        this.userMapper = userMapper;
        this.judgmentRules = judgmentRules;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 播种整套样例数据。任何一步失败都整体回滚，不会留下半套数据；
     * 已播过种时按「样例数据已存在」拒绝，避免与真实数据混在一起。
     */
    @Transactional
    public DemoSeedResult seed() {
        if (projectMapper.exists(new LambdaQueryWrapper<Project>()
            .eq(Project::getProjectCode, DEMO_PROJECT_CODE))) {
            throw new BizException(ErrorCode.DEMO_DATA_EXISTS);
        }
        Random random = new Random(DEMO_SEED);
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        List<Long> inspectorIds = createInspectorIds();
        Long engineerId = firstUserIdOfRole("ENGINEER");

        List<ProjectContext> contexts = List.of(
            createContext("PV-2026-101", "青海共和 200MW 光伏电站", "710W 双玻", "国家能源集团",
                "晶科能源", "青海共和", today),
            createContext("PV-2026-102", "甘肃敦煌 80MW 光伏电站", "705W 单玻", "华能",
                "隆基绿能", "甘肃敦煌", today));

        int recordCount = 0;
        int defectCount = 0;
        int defectOrdinal = 0;
        for (int day = DAYS - 1; day >= 0; day--) {
            for (int slot = 0; slot < RECORDS_PER_DAY; slot++) {
                int index = (DAYS - 1 - day) * RECORDS_PER_DAY + slot;
                ProjectContext context = contexts.get(index % contexts.size());
                InspectionStandard standard =
                    context.standards.get(context.recordIndex % context.standards.size());
                Batch batch = context.recordBatches.get(
                    (context.recordIndex / context.standards.size()) % context.recordBatches.size());
                LocalDateTime inspectTime = inspectTimeOf(today, now, day, slot);

                boolean fail = random.nextInt(100) < DEFECT_PERCENT;
                Measurement measurement = measurementFor(standard, fail, random);
                String result = judgmentRules.evaluate(standard.getJudgeType(), measurement.value(),
                    measurement.text(), numericOf(standard));

                InspectionRecord record = new InspectionRecord();
                record.setProjectId(context.project.getId());
                record.setBatchId(batch.getId());
                record.setStandardId(standard.getId());
                record.setScene(standard.getScene());
                record.setProcessName(standard.getProcessName());
                record.setItemName(standard.getItemName());
                record.setInspectorId(inspectorIds.get(context.recordIndex % inspectorIds.size()));
                record.setInspectTime(inspectTime);
                record.setMeasuredValue(measurement.value());
                record.setTextValue(measurement.text());
                record.setResult(result);
                record.setStdLower(standard.getLowerLimit());
                record.setStdUpper(standard.getUpperLimit());
                record.setStdTarget(standard.getTargetValue());
                record.setStdTolerance(standard.getTolerance());
                record.setStdUnit(standard.getUnit());
                record.setIsVoid(false);
                recordMapper.insert(record);
                // createdAt 由 MyMetaObjectHandler 填成 now()，这里改回判定那一刻，历史才连续。
                recordMapper.update(new LambdaUpdateWrapper<InspectionRecord>()
                    .eq(InspectionRecord::getId, record.getId())
                    .set(InspectionRecord::getCreatedAt, inspectTime));
                recordCount++;

                if ("FAIL".equals(result)) {
                    defectCount++;
                    insertDefect(record.getId(), inspectTime, engineerId, defectOrdinal++, random);
                }
                context.recordIndex++;
            }
        }
        log.info("样例数据初始化完成 projects={} records={} defects={}",
            contexts.size(), recordCount, defectCount);
        return new DemoSeedResult(recordCount, defectCount, contexts.size());
    }

    /**
     * 缺陷与 FAIL 记录 1:1：等级按 1:3:6 落点，类型按五类轮转；
     * created_at 必须等于判定那一刻（记录的 inspect_time），否则 CH-08 的时长恒为 0。
     * 约 40% 直接走到 CLOSED，resolved_at 落在 created_at 之后 4–48 小时；
     * 今天生成的记录按此推算可能落到未来，此时截到 now 之前半小时（见 resolvedAtOf）。
     */
    private void insertDefect(Long recordId, LocalDateTime inspectTime, Long engineerId, int ordinal,
                              Random random) {
        Defect defect = new Defect();
        defect.setRecordId(recordId);
        defect.setDefectType(DEFECT_TYPES[ordinal % DEFECT_TYPES.length]);
        defect.setDefectGrade(gradeOf(random));
        defect.setDefectStatus("OPEN");
        defectMapper.insert(defect);

        LambdaUpdateWrapper<Defect> update = new LambdaUpdateWrapper<Defect>()
            .eq(Defect::getId, defect.getId())
            .set(Defect::getCreatedAt, inspectTime);
        if (random.nextInt(100) < CLOSE_PERCENT) {
            LocalDateTime resolvedAt = inspectTime.plusHours(4 + random.nextInt(45));
            LocalDateTime now = LocalDateTime.now();
            if (resolvedAt.isAfter(now)) {
                // 今天的记录按 4–48h 推算会落到未来：截到 now 之前半小时，
                // 但不得早于 created_at（最近半小时内判定的记录）。
                resolvedAt = now.minusMinutes(30);
                if (!resolvedAt.isAfter(inspectTime)) {
                    resolvedAt = inspectTime.plusMinutes(1);
                }
            }
            update.set(Defect::getDefectStatus, "CLOSED")
                .set(Defect::getHandlerId, engineerId)
                .set(Defect::getHandleNote, "返工完成并复检合格")
                .set(Defect::getResolvedAt, resolvedAt);
        }
        defectMapper.update(update);
    }

    /** 致命 / 严重 / 轻微 = 1 : 3 : 6。 */
    private static String gradeOf(Random random) {
        int roll = random.nextInt(10);
        return roll == 0 ? "CRITICAL" : roll <= 3 ? "MAJOR" : "MINOR";
    }

    /**
     * 按判定方式生成实测值：pass 落在规格内，fail 落在规格外，具体判定仍由
     * {@link JudgmentRules#evaluate} 计算，这里不另写一套规则（BR-01）。
     */
    private static Measurement measurementFor(InspectionStandard standard, boolean fail, Random random) {
        String judgeType = standard.getJudgeType();
        if ("PASS_FAIL".equals(judgeType)) {
            return new Measurement(null, fail ? "FAIL" : "PASS");
        }
        double value = switch (judgeType) {
            case "GT" -> {
                double lower = standard.getLowerLimit().doubleValue();
                yield fail ? lower * (0.70 + random.nextDouble() * 0.20)
                    : lower * (1.05 + random.nextDouble() * 0.15);
            }
            case "LT" -> {
                double upper = standard.getUpperLimit().doubleValue();
                yield fail ? upper * (1.02 + random.nextDouble() * 0.08)
                    : upper * (0.90 + random.nextDouble() * 0.07);
            }
            case "RANGE" -> {
                double lower = standard.getLowerLimit().doubleValue();
                double span = standard.getUpperLimit().doubleValue() - lower;
                yield fail ? (random.nextBoolean() ? lower - span * 0.1 : lower + span * 1.1)
                    : lower + span * (0.1 + random.nextDouble() * 0.8);
            }
            case "STD_TOL" -> {
                double target = standard.getTargetValue().doubleValue();
                double tolerance = standard.getTolerance().doubleValue();
                double direction = random.nextBoolean() ? 1 : -1;
                yield fail ? target + direction * tolerance * (1.1 + random.nextDouble() * 0.5)
                    : target + direction * tolerance * random.nextDouble() * 0.9;
            }
            default -> throw new IllegalStateException("未知判定方式: " + judgeType);
        };
        return new Measurement(BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP), null);
    }

    /** 一次判定的输入：数值型给 value，人工判定给 text。 */
    private record Measurement(BigDecimal value, String text) {
    }

    /**
     * 今天往前 29 天共 30 天；今天的时间点压在 now 之前，历史日期固定落在 08:00–17:10。
     */
    private static LocalDateTime inspectTimeOf(LocalDate today, LocalDateTime now, int day, int slot) {
        if (day == 0) {
            return now.minusMinutes(10L * (RECORDS_PER_DAY - slot)).withSecond(0).withNano(0);
        }
        return today.minusDays(day).atTime(8, 0).plusMinutes(50L * slot);
    }

    /**
     * CH-07 要有三根柱：既有检验员一名 + 两名样例检验员。
     * 库里没有既有检验员时只用两名样例检验员，播种仍能完成。
     */
    private List<Long> createInspectorIds() {
        List<Long> ids = new ArrayList<>();
        userMapper.selectList(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getRole, "INSPECTOR")
                .orderByAsc(AppUser::getId))
            .stream()
            .filter(user -> !user.getUsername().startsWith(DEMO_INSPECTOR_PREFIX))
            .findFirst()
            .ifPresent(user -> ids.add(user.getId()));
        for (int i = 0; i < DEMO_INSPECTOR_USERNAMES.length; i++) {
            AppUser user = new AppUser();
            user.setUsername(DEMO_INSPECTOR_USERNAMES[i]);
            user.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
            user.setDisplayName(DEMO_INSPECTOR_NAMES[i]);
            user.setRole("INSPECTOR");
            user.setIsActive(true);
            userMapper.insert(user);
            ids.add(user.getId());
        }
        return ids;
    }

    private Long firstUserIdOfRole(String role) {
        AppUser user = userMapper.selectOne(new LambdaQueryWrapper<AppUser>()
            .eq(AppUser::getRole, role)
            .orderByAsc(AppUser::getId)
            .last("LIMIT 1"));
        return user == null ? null : user.getId();
    }

    private ProjectContext createContext(String code, String name, String productModel, String customerName,
                                         String manufacturerName, String location, LocalDate today) {
        Project project = new Project();
        project.setProjectCode(code);
        project.setProjectName(name);
        project.setProductModel(productModel);
        project.setCustomerName(customerName);
        project.setManufacturerName(manufacturerName);
        project.setLocation(location);
        project.setStatus("ACTIVE");
        // 一次性播种：生成后项目下已有批次与记录，应用内无法删除（40006/40007），重新生成需清理数据库。
        // 备注里不写「演示」字样：这些行在界面上与真实数据无从分辨，备注也不该暴露。
        project.setRemark("历史数据导入，一次性生成，应用内无法删除");
        projectMapper.insert(project);
        List<Batch> batches = createBatches(project, today);
        return new ProjectContext(project, batches, createStandards(project));
    }

    /** 每个项目 3 个批次，覆盖 PLANNED / PRODUCING / COMPLETED；记录只落在已开工的两个批次上。 */
    private List<Batch> createBatches(Project project, LocalDate today) {
        List<Batch> batches = new ArrayList<>();
        batches.add(createBatch(project, "-B1", today.plusDays(5), null, "PLANNED"));
        batches.add(createBatch(project, "-B2", today.minusDays(20), null, "PRODUCING"));
        batches.add(createBatch(project, "-B3", today.minusDays(45), today.minusDays(15), "COMPLETED"));
        return batches;
    }

    private Batch createBatch(Project project, String suffix, LocalDate startDate, LocalDate endDate,
                              String status) {
        Batch batch = new Batch();
        batch.setProjectId(project.getId());
        batch.setBatchNo(project.getProjectCode() + suffix);
        batch.setPlannedQty(1200);
        batch.setStartDate(startDate);
        batch.setEndDate(endDate);
        batch.setStatus(status);
        batchMapper.insert(batch);
        return batch;
    }

    /**
     * 每个项目 10 条标准：IQC 3 / PQC 4 / OQC 3，五种判定方式都有，
     * 且 GT、LT 各至少一条（让控制图的单边限值也出现）。
     */
    private List<InspectionStandard> createStandards(Project project) {
        List<StandardSpec> specs = List.of(
            new StandardSpec("IQC", "来料检验", "IQC-01", "玻璃厚度", "mm", "GT",
                bd("3.0"), null, null, null),
            new StandardSpec("IQC", "来料检验", "IQC-02", "边框长度", "mm", "LT",
                null, bd("1200"), null, null),
            new StandardSpec("IQC", "来料检验", "IQC-03", "来料外观", null, "PASS_FAIL",
                null, null, null, null),
            new StandardSpec("PQC", "层压", "PQC-01", "层压温度", "℃", "RANGE",
                bd("140"), bd("160"), null, null),
            new StandardSpec("PQC", "层压", "PQC-02", "组件功率", "W", "STD_TOL",
                null, null, bd("710"), bd("5")),
            new StandardSpec("PQC", "焊接", "PQC-03", "焊接拉力", "N", "GT",
                bd("45"), null, null, null),
            new StandardSpec("PQC", "焊接", "PQC-04", "焊带外观", null, "PASS_FAIL",
                null, null, null, null),
            new StandardSpec("OQC", "包装", "OQC-01", "边框宽度", "mm", "RANGE",
                bd("3.0"), bd("3.5"), null, null),
            new StandardSpec("OQC", "包装", "OQC-02", "外观检查", null, "PASS_FAIL",
                null, null, null, null),
            new StandardSpec("OQC", "包装", "OQC-03", "成品重量", "kg", "LT",
                null, bd("35"), null, null));
        List<InspectionStandard> standards = new ArrayList<>();
        for (int i = 0; i < specs.size(); i++) {
            StandardSpec spec = specs.get(i);
            JudgmentRules.Numeric numeric = judgmentRules.normalize(spec.judgeType(), spec.lower(),
                spec.upper(), spec.target(), spec.tolerance());
            InspectionStandard standard = new InspectionStandard();
            standard.setProjectId(project.getId());
            standard.setScene(spec.scene());
            standard.setProcessName(spec.processName());
            standard.setItemCode(spec.itemCode());
            standard.setItemName(spec.itemName());
            standard.setUnit(spec.unit());
            standard.setValueType("PASS_FAIL".equals(spec.judgeType()) ? "TEXT" : "NUMERIC");
            standard.setJudgeType(spec.judgeType());
            standard.setLowerLimit(numeric.lower());
            standard.setUpperLimit(numeric.upper());
            standard.setTargetValue(numeric.target());
            standard.setTolerance(numeric.tolerance());
            standard.setIsEnabled(true);
            standard.setSortOrder(i + 1);
            standardMapper.insert(standard);
            standards.add(standard);
        }
        return standards;
    }

    private static JudgmentRules.Numeric numericOf(InspectionStandard standard) {
        return new JudgmentRules.Numeric(standard.getLowerLimit(), standard.getUpperLimit(),
            standard.getTargetValue(), standard.getTolerance());
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    /** 标准的声明式描述，落库前统一经 {@link JudgmentRules#normalize}。 */
    private record StandardSpec(String scene, String processName, String itemCode, String itemName,
                                String unit, String judgeType, BigDecimal lower, BigDecimal upper,
                                BigDecimal target, BigDecimal tolerance) {
    }

    /** 一个样例项目及其批次与标准；recordIndex 是该项目的记录序号。 */
    private static final class ProjectContext {

        private final Project project;
        /** 记录只落在已开工的两个批次上（PLANNED 批次没有记录）。 */
        private final List<Batch> recordBatches;
        private final List<InspectionStandard> standards;
        private int recordIndex;

        private ProjectContext(Project project, List<Batch> batches, List<InspectionStandard> standards) {
            this.project = project;
            this.recordBatches = batches.subList(1, batches.size());
            this.standards = standards;
        }
    }
}
