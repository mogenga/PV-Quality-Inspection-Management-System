package com.pvqims.inspection;

import java.math.BigDecimal;

import com.pvqims.common.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class JudgmentRulesTest {

    private final JudgmentRules rules = new JudgmentRules();

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @Test
    void gtPassesOnlyAboveLower() {
        JudgmentRules.Numeric std = new JudgmentRules.Numeric(d("100"), null, null, null);
        assertThat(rules.evaluate("GT", d("100.0001"), null, std)).isEqualTo("PASS");
        assertThat(rules.evaluate("GT", d("100"), null, std)).isEqualTo("FAIL");
        assertThat(rules.evaluate("GT", d("99.99"), null, std)).isEqualTo("FAIL");
    }

    @Test
    void ltPassesOnlyBelowUpper() {
        JudgmentRules.Numeric std = new JudgmentRules.Numeric(null, d("3"), null, null);
        assertThat(rules.evaluate("LT", d("2.99"), null, std)).isEqualTo("PASS");
        assertThat(rules.evaluate("LT", d("3"), null, std)).isEqualTo("FAIL");
    }

    @Test
    void rangeBoundariesAreInclusive() {
        JudgmentRules.Numeric std = new JudgmentRules.Numeric(d("3.0"), d("3.5"), null, null);
        assertThat(rules.evaluate("RANGE", d("3.0"), null, std)).isEqualTo("PASS");
        assertThat(rules.evaluate("RANGE", d("3.5"), null, std)).isEqualTo("PASS");
        assertThat(rules.evaluate("RANGE", d("3.25"), null, std)).isEqualTo("PASS");
        assertThat(rules.evaluate("RANGE", d("3.51"), null, std)).isEqualTo("FAIL");
        assertThat(rules.evaluate("RANGE", d("2.99"), null, std)).isEqualTo("FAIL");
    }

    @Test
    void stdTolBoundaryIsInclusive() {
        JudgmentRules.Numeric std = new JudgmentRules.Numeric(null, null, d("710"), d("5"));
        assertThat(rules.evaluate("STD_TOL", d("715"), null, std)).isEqualTo("PASS");
        assertThat(rules.evaluate("STD_TOL", d("705"), null, std)).isEqualTo("PASS");
        assertThat(rules.evaluate("STD_TOL", d("710"), null, std)).isEqualTo("PASS");
        assertThat(rules.evaluate("STD_TOL", d("715.01"), null, std)).isEqualTo("FAIL");
        assertThat(rules.evaluate("STD_TOL", d("704.99"), null, std)).isEqualTo("FAIL");
    }

    @Test
    void passFailTrustsTextValue() {
        assertThat(rules.evaluate("PASS_FAIL", null, "PASS", JudgmentRules.Numeric.empty())).isEqualTo("PASS");
        assertThat(rules.evaluate("PASS_FAIL", null, "FAIL", JudgmentRules.Numeric.empty())).isEqualTo("FAIL");
        // 文字结论缺失时不能当成合格
        assertThat(rules.evaluate("PASS_FAIL", null, null, JudgmentRules.Numeric.empty())).isEqualTo("FAIL");
        assertThat(rules.evaluate("PASS_FAIL", null, "随便写的", JudgmentRules.Numeric.empty())).isEqualTo("FAIL");
    }

    @Test
    void numericJudgeTypesRequireMeasuredValue() {
        JudgmentRules.Numeric std = new JudgmentRules.Numeric(d("1"), null, null, null);
        assertThatThrownBy(() -> rules.evaluate("GT", null, null, std))
            .isInstanceOf(BizException.class)
            .hasMessage("数值型检验项必须填写实测值");
    }

    @Test
    void normalizeKeepsOnlyColumnsRelevantToJudgeType() {
        assertThat(rules.normalize("GT", d("1"), d("2"), d("3"), d("4")))
            .isEqualTo(new JudgmentRules.Numeric(d("1"), null, null, null));
        assertThat(rules.normalize("LT", d("1"), d("2"), d("3"), d("4")))
            .isEqualTo(new JudgmentRules.Numeric(null, d("2"), null, null));
        assertThat(rules.normalize("RANGE", d("1"), d("2"), d("3"), d("4")))
            .isEqualTo(new JudgmentRules.Numeric(d("1"), d("2"), null, null));
        assertThat(rules.normalize("STD_TOL", d("1"), d("2"), d("3"), d("4")))
            .isEqualTo(new JudgmentRules.Numeric(null, null, d("3"), d("4")));
        assertThat(rules.normalize("PASS_FAIL", d("1"), d("2"), d("3"), d("4")))
            .isEqualTo(JudgmentRules.Numeric.empty());
    }

    @Test
    void validateNumericRejectsMissingAndInvertedRanges() {
        assertThatThrownBy(() -> rules.validateNumeric("GT", null, null, null, null))
            .hasMessage("判定方式为 GT 时必须填写下限");
        assertThatThrownBy(() -> rules.validateNumeric("RANGE", d("3.5"), d("3.0"), null, null))
            .hasMessage("下限必须小于上限");
        assertThatThrownBy(() -> rules.validateNumeric("STD_TOL", null, null, d("710"), d("0")))
            .hasMessage("公差必须大于 0");
        // 合法输入不抛
        rules.validateNumeric("RANGE", d("3.0"), d("3.5"), null, null);
    }
}
