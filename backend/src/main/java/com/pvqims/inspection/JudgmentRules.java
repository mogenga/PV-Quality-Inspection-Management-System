package com.pvqims.inspection;

import java.math.BigDecimal;

import com.pvqims.common.exception.BizException;
import com.pvqims.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * 判定规则的唯一归属（SRS 7 章可维护性）：配置期的数值列校验、规整与运行时的判定求值都在这里，
 * 新增判定方式只改这一个类。
 */
@Component
public class JudgmentRules {

    /**
     * 判定涉及的四个数值列；无关列由 {@link #normalize} 置空，避免旧上限/旧公差残留到后续判定。
     */
    public record Numeric(BigDecimal lower, BigDecimal upper, BigDecimal target, BigDecimal tolerance) {

        public static Numeric empty() {
            return new Numeric(null, null, null, null);
        }
    }

    /**
     * 配置期校验（SRS 4.4 约束）：必须在规整前对原始输入执行，否则无关列里的非法值会被静默清掉。
     * 值类型一致性属于配置期规则，留在 StandardService。
     */
    public void validateNumeric(String judgeType, BigDecimal lower, BigDecimal upper,
                                BigDecimal target, BigDecimal tolerance) {
        switch (judgeType) {
            case "GT" -> {
                if (lower == null) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "判定方式为 GT 时必须填写下限");
                }
            }
            case "LT" -> {
                if (upper == null) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "判定方式为 LT 时必须填写上限");
                }
            }
            case "RANGE" -> {
                if (lower == null || upper == null) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "判定方式为 RANGE 时必须填写上下限");
                }
            }
            case "STD_TOL" -> {
                if (target == null || tolerance == null) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "判定方式为 STD_TOL 时必须填写标准值与公差");
                }
            }
            default -> {
                // PASS_FAIL 不要求数值列；枚举合法性已由 @Pattern 保证。
            }
        }
        if (lower != null && upper != null && lower.compareTo(upper) >= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "下限必须小于上限");
        }
        if (tolerance != null && tolerance.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "公差必须大于 0");
        }
    }

    /**
     * 按判定方式保留相关数值列，其余置 null。
     */
    public Numeric normalize(String judgeType, BigDecimal lower, BigDecimal upper,
                             BigDecimal target, BigDecimal tolerance) {
        return switch (judgeType) {
            case "GT" -> new Numeric(lower, null, null, null);
            case "LT" -> new Numeric(null, upper, null, null);
            case "RANGE" -> new Numeric(lower, upper, null, null);
            case "STD_TOL" -> new Numeric(null, null, target, tolerance);
            default -> Numeric.empty();
        };
    }

    /**
     * 运行时判定（BR-01，只在服务端执行）。数值比较一律 compareTo；边界闭区间按 SRS 4.4。
     * PASS_FAIL 只认 "PASS" 为合格，其余（含 null）一律 FAIL —— 宁可判严不判松。
     */
    public String evaluate(String judgeType, BigDecimal measured, String textValue, Numeric standard) {
        return switch (judgeType) {
            case "GT" -> requireMeasured(measured).compareTo(standard.lower()) > 0 ? "PASS" : "FAIL";
            case "LT" -> requireMeasured(measured).compareTo(standard.upper()) < 0 ? "PASS" : "FAIL";
            case "RANGE" -> {
                BigDecimal value = requireMeasured(measured);
                yield value.compareTo(standard.lower()) >= 0 && value.compareTo(standard.upper()) <= 0
                    ? "PASS" : "FAIL";
            }
            case "STD_TOL" -> requireMeasured(measured).subtract(standard.target()).abs()
                .compareTo(standard.tolerance()) <= 0 ? "PASS" : "FAIL";
            default -> "PASS".equals(textValue) ? "PASS" : "FAIL";
        };
    }

    private BigDecimal requireMeasured(BigDecimal measured) {
        if (measured == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "数值型检验项必须填写实测值");
        }
        return measured;
    }
}
