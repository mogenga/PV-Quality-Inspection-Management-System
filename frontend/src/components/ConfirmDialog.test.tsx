import { render, screen } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import ConfirmDialog from './ConfirmDialog';

it('不传 confirmLabel 时确认按钮仍是「确认删除」', () => {
  render(<ConfirmDialog open title="删除项目" content="确定删除该项目？" onCancel={vi.fn()} onConfirm={vi.fn()} />);

  // 既有两个删除调用点（项目、批次）都不传 confirmLabel：默认值一变，删除弹窗的可访问名就跟着变
  expect(screen.getByRole('button', { name: '确认删除' })).toBeInTheDocument();
});

it('传入 confirmLabel 时用自定义文案，取消按钮不变', () => {
  render(
    <ConfirmDialog
      open
      title="生成样例数据"
      content="将创建样例数据"
      confirmLabel="开始生成"
      onCancel={vi.fn()}
      onConfirm={vi.fn()}
    />,
  );

  expect(screen.getByRole('button', { name: '开始生成' })).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '确认删除' })).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: '取消' })).toBeInTheDocument();
});

it('传入 error 时原样展示后端原因', () => {
  render(
    <ConfirmDialog
      open
      title="生成样例数据"
      content="将创建样例数据"
      confirmLabel="开始生成"
      onCancel={vi.fn()}
      onConfirm={vi.fn()}
      error="样例数据已存在，如需重新生成请先由管理员清理数据库"
    />,
  );

  expect(screen.getByText('样例数据已存在，如需重新生成请先由管理员清理数据库')).toBeInTheDocument();
});
