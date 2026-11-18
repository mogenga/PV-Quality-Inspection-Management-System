import type { StandardSummary } from '../api/types';

// 后端 BigDecimal 会序列化成 3.0000 这样的尾随零；String(Number(v)) 一并收掉
function num(value: number): string {
  return String(Number(value));
}

function withUnit(text: string, unit: string | null): string {
  return unit ? `${text} ${unit}` : text;
}

export function formatStandardValue(
  std: Pick<StandardSummary, 'judgeType' | 'lowerLimit' | 'upperLimit' | 'targetValue' | 'tolerance' | 'unit'>,
): string {
  const { judgeType, lowerLimit, upperLimit, targetValue, tolerance, unit } = std;
  switch (judgeType) {
    case 'GT':
      return lowerLimit === null ? '—' : withUnit(`> ${num(lowerLimit)}`, unit);
    case 'LT':
      return upperLimit === null ? '—' : withUnit(`< ${num(upperLimit)}`, unit);
    case 'RANGE':
      return lowerLimit === null || upperLimit === null
        ? '—'
        : withUnit(`${num(lowerLimit)} – ${num(upperLimit)}`, unit);
    case 'STD_TOL':
      return targetValue === null || tolerance === null
        ? '—'
        : withUnit(`${num(targetValue)} ± ${num(tolerance)}`, unit);
    case 'PASS_FAIL':
      // 人工判定只看 PASS/FAIL，没有数值标准；写「人工判定」比空破折号更说明问题，
      // 用户不会误以为「标准没配好」
      return '人工判定';
  }
}
