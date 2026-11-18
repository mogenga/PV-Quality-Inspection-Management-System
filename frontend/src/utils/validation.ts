export type RequiredField = { field: string; label: string; value: string };

/**
 * 提交前的前端必填检查。
 *
 * 返回空数组表示全部有值；否则返回缺失字段名（供输入框标红）并给出
 * 「请填写A、B」这样的提示——后端默认的「不能为空」不说哪个字段，
 * 用户要自己逐行找。
 */
export function findMissing(fields: RequiredField[]): RequiredField[] {
  return fields.filter((item) => item.value.trim() === '');
}

export function missingMessage(missing: RequiredField[]): string | null {
  if (missing.length === 0) return null;
  return `请填写${missing.map((item) => item.label).join('、')}`;
}
