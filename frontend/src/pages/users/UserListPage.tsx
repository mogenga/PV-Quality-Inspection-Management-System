import { useId, useState } from 'react';
import type { FormEvent } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  Paper,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createUser, listUsers, resetPassword, updateUser } from '../../api/users';
import { ApiError } from '../../api/client';
import type { UserSummary } from '../../api/types';
import { ROLE_LABELS } from '../../utils/labels';
import type { Role } from '../../utils/labels';
import { shadow } from '../../theme/muiTheme';
import LabeledField from '../../components/LabeledField';

const ROLE_OPTIONS: { value: Role; label: string }[] = (Object.keys(ROLE_LABELS) as Role[]).map((role) => ({
  value: role,
  label: ROLE_LABELS[role],
}));

const ROLE_FILTER_OPTIONS: { value: Role | ''; label: string }[] = [{ value: '', label: '全部' }, ...ROLE_OPTIONS];

const STATUS_FILTER_OPTIONS: { value: '' | 'active' | 'inactive'; label: string }[] = [
  { value: '', label: '全部' },
  { value: 'active', label: '启用' },
  { value: 'inactive', label: '停用' },
];

const ACTIVE_OPTIONS: { value: 'active' | 'inactive'; label: string }[] = [
  { value: 'active', label: '启用' },
  { value: 'inactive', label: '停用' },
];

function LabeledSelect<T extends string>({
  label,
  value,
  options,
  onChange,
}: {
  label: string;
  value: T;
  options: { value: T; label: string }[];
  onChange: (value: T) => void;
}) {
  const id = useId();
  return (
    <Stack spacing={1} sx={{ minWidth: 140 }}>
      {/* 标签在框外，不随 Select 上飘——与登录页输入框保持一致 */}
      <Typography component="label" htmlFor={id} variant="body2" sx={{ fontWeight: 550 }}>
        {label}
      </Typography>
      <Select
        id={id}
        value={value}
        onChange={(event) => onChange(event.target.value as T)}
        inputProps={{ 'aria-label': label }}
      >
        {options.map((option) => (
          <MenuItem key={option.value} value={option.value}>
            {option.label}
          </MenuItem>
        ))}
      </Select>
    </Stack>
  );
}

function errorMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : '网络异常，请稍后重试';
}

type DialogState =
  { type: 'create' } | { type: 'edit'; user: UserSummary } | { type: 'reset-password'; user: UserSummary } | null;

function CreateUserDialog({ onClose }: { onClose: () => void }) {
  const queryClient = useQueryClient();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [role, setRole] = useState<Role>('INSPECTOR');
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: createUser,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] });
      onClose();
    },
    onError: (err) => setError(errorMessage(err)),
  });

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    mutation.mutate({ username, password, displayName, role });
  };

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="xs">
      <Box component="form" onSubmit={handleSubmit}>
        <DialogTitle>新增用户</DialogTitle>
        <DialogContent>
          <Stack spacing={3} sx={{ pt: 1 }}>
            {error && <Alert severity="error">{error}</Alert>}
            <LabeledField label="登录名" value={username} onChange={setUsername} autoComplete="off" />
            <LabeledField
              label="初始密码"
              type="password"
              value={password}
              onChange={setPassword}
              autoComplete="new-password"
            />
            <LabeledField label="显示姓名" value={displayName} onChange={setDisplayName} />
            <LabeledSelect label="角色" value={role} options={ROLE_OPTIONS} onChange={setRole} />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>取消</Button>
          <Button type="submit" variant="contained" disabled={mutation.isPending}>
            确定
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
  );
}

function EditUserDialog({ user, onClose }: { user: UserSummary; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [displayName, setDisplayName] = useState(user.displayName);
  const [role, setRole] = useState<Role>(user.role);
  const [isActive, setIsActive] = useState(user.isActive);
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: (req: { displayName: string; role: Role; isActive: boolean }) => updateUser(user.id, req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] });
      onClose();
    },
    onError: (err) => setError(errorMessage(err)),
  });

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    mutation.mutate({ displayName, role, isActive });
  };

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="xs">
      <Box component="form" onSubmit={handleSubmit}>
        <DialogTitle>编辑用户</DialogTitle>
        <DialogContent>
          <Stack spacing={3} sx={{ pt: 1 }}>
            {error && <Alert severity="error">{error}</Alert>}
            <LabeledField label="显示姓名" value={displayName} onChange={setDisplayName} />
            <LabeledSelect label="角色" value={role} options={ROLE_OPTIONS} onChange={setRole} />
            <LabeledSelect
              label="状态"
              value={isActive ? 'active' : 'inactive'}
              options={ACTIVE_OPTIONS}
              onChange={(value) => setIsActive(value === 'active')}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>取消</Button>
          <Button type="submit" variant="contained" disabled={mutation.isPending}>
            确定
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
  );
}

function ResetPasswordDialog({ user, onClose }: { user: UserSummary; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [newPassword, setNewPassword] = useState('');
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: (password: string) => resetPassword(user.id, password),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] });
      onClose();
    },
    onError: (err) => setError(errorMessage(err)),
  });

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    mutation.mutate(newPassword);
  };

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="xs">
      <Box component="form" onSubmit={handleSubmit}>
        <DialogTitle>重置密码</DialogTitle>
        <DialogContent>
          <Stack spacing={3} sx={{ pt: 1 }}>
            {error && <Alert severity="error">{error}</Alert>}
            <LabeledField
              label="新密码"
              type="password"
              value={newPassword}
              onChange={setNewPassword}
              autoComplete="new-password"
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>取消</Button>
          <Button type="submit" variant="contained" disabled={mutation.isPending}>
            确定
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
  );
}

export default function UserListPage() {
  const [keyword, setKeyword] = useState('');
  const [role, setRole] = useState<Role | ''>('');
  const [status, setStatus] = useState<'' | 'active' | 'inactive'>('');
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [dialog, setDialog] = useState<DialogState>(null);

  const params = {
    keyword: keyword || undefined,
    role: role || undefined,
    isActive: status === '' ? undefined : status === 'active',
    page: page + 1,
    pageSize,
  };

  const { data, isPending, error } = useQuery({
    queryKey: ['users', params],
    queryFn: () => listUsers(params),
  });

  const total = data?.total ?? 0;
  // 空结果或编辑后收缩到更小的末页时，起点不能越过总数；空列表显示 0–0
  const rangeStart = total === 0 ? 0 : Math.min(page * pageSize + 1, total);
  const rangeEnd = total === 0 ? 0 : Math.min((page + 1) * pageSize, total);

  return (
    <Stack spacing={3}>
      <Typography variant="h3" component="h1">
        用户管理
      </Typography>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', mb: 2 }}>
        <Typography variant="body2" color="text.secondary">
          共 {total} 个账号
        </Typography>
        <Typography variant="caption" color="text.secondary">
          当前显示第 {rangeStart}–{rangeEnd} 条
        </Typography>
      </Box>
      <Stack direction="row" spacing={2} sx={{ flexWrap: 'wrap', alignItems: 'flex-start' }}>
        <Stack spacing={1} sx={{ minWidth: 180 }}>
          <Typography component="label" htmlFor="user-keyword" variant="body2" sx={{ fontWeight: 550 }}>
            关键字
          </Typography>
          <TextField
            id="user-keyword"
            size="small"
            value={keyword}
            onChange={(event) => {
              setKeyword(event.target.value);
              setPage(0);
            }}
          />
        </Stack>
        <LabeledSelect
          label="角色"
          value={role}
          options={ROLE_FILTER_OPTIONS}
          onChange={(value) => {
            setRole(value);
            setPage(0);
          }}
        />
        <LabeledSelect
          label="状态"
          value={status}
          options={STATUS_FILTER_OPTIONS}
          onChange={(value) => {
            setStatus(value);
            setPage(0);
          }}
        />
        <Button variant="contained" sx={{ ml: 'auto', mt: 3.5 }} onClick={() => setDialog({ type: 'create' })}>
          新增用户
        </Button>
      </Stack>
      <TableContainer component={Paper} sx={{ boxShadow: shadow.card }}>
        <Table>
          <TableHead sx={{ bgcolor: 'background.default' }}>
            <TableRow>
              <TableCell>登录名</TableCell>
              <TableCell>显示姓名</TableCell>
              <TableCell>角色</TableCell>
              <TableCell>状态</TableCell>
              <TableCell>创建时间</TableCell>
              <TableCell>操作</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {isPending && (
              <TableRow>
                <TableCell colSpan={6} align="center">
                  加载中…
                </TableCell>
              </TableRow>
            )}
            {error && (
              <TableRow>
                <TableCell colSpan={6}>
                  <Alert severity="error">{errorMessage(error)}</Alert>
                </TableCell>
              </TableRow>
            )}
            {data?.records.map((record) => (
              <TableRow key={record.id}>
                <TableCell>{record.username}</TableCell>
                <TableCell>{record.displayName}</TableCell>
                <TableCell>{ROLE_LABELS[record.role]}</TableCell>
                <TableCell>
                  <Chip
                    size="small"
                    label={record.isActive ? '启用' : '停用'}
                    color={record.isActive ? 'success' : 'default'}
                  />
                </TableCell>
                <TableCell>{record.createdAt.replace('T', ' ')}</TableCell>
                <TableCell>
                  <Stack direction="row" spacing={1}>
                    <Button size="small" onClick={() => setDialog({ type: 'edit', user: record })}>
                      编辑
                    </Button>
                    <Button size="small" onClick={() => setDialog({ type: 'reset-password', user: record })}>
                      重置密码
                    </Button>
                  </Stack>
                </TableCell>
              </TableRow>
            ))}
            {data?.records.length === 0 && (
              <TableRow>
                <TableCell colSpan={6} align="center">
                  没有符合条件的用户
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
        <TablePagination
          component="div"
          count={data?.total ?? 0}
          page={page}
          rowsPerPage={pageSize}
          rowsPerPageOptions={[10, 20, 50]}
          onPageChange={(_, nextPage) => setPage(nextPage)}
          onRowsPerPageChange={(event) => {
            setPageSize(Number(event.target.value));
            setPage(0);
          }}
        />
      </TableContainer>
      {dialog?.type === 'create' && <CreateUserDialog onClose={() => setDialog(null)} />}
      {dialog?.type === 'edit' && <EditUserDialog user={dialog.user} onClose={() => setDialog(null)} />}
      {dialog?.type === 'reset-password' && <ResetPasswordDialog user={dialog.user} onClose={() => setDialog(null)} />}
    </Stack>
  );
}
