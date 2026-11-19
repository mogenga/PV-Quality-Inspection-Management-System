import { useState } from 'react';
import type { FormEvent } from 'react';
import { Alert, Box, Button, Paper, Stack, TextField, Typography } from '@mui/material';
import { useNavigate } from 'react-router';
import { login } from '../api/auth';
import { ApiError } from '../api/client';
import { useAuthStore } from '../stores/authStore';
import { HOME_BY_ROLE } from '../utils/labels';
import { color, shadow } from '../theme/muiTheme';
import PasswordVisibilityAdornment from '../components/PasswordVisibilityAdornment';
import BrandBackdrop from './login/BrandBackdrop';
import BrandMark from './login/BrandMark';

const SYSTEM_NAME = '光伏组件校验数字化平台';

export default function LoginPage() {
  const navigate = useNavigate();
  const setSession = useAuthStore((state) => state.setSession);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [passwordVisible, setPasswordVisible] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const { token, user } = await login({ username, password });
      setSession(token, user);
      navigate(HOME_BY_ROLE[user.role], { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '网络异常，请稍后重试');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Box
      sx={{
        position: 'relative',
        minHeight: '100dvh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        overflow: 'hidden',
        bgcolor: color.bg,
        p: 2,
      }}
    >
      {/* 全局背景：整页共用一幅透视阵列，不再左右分割 */}
      <BrandBackdrop />

      <Box sx={{ position: 'relative', zIndex: 1, width: '100%', maxWidth: 420 }}>
        {/* 标识与系统名在卡片上方，像企业门户的抬头 */}
        <Stack spacing={2} sx={{ alignItems: 'center', mb: 4 }}>
          <Box
            sx={{
              width: 72,
              height: 72,
              borderRadius: '18px',
              bgcolor: color.surface1,
              boxShadow: shadow.card,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <BrandMark size={56} />
          </Box>
          <Box sx={{ textAlign: 'center' }}>
            <Typography variant="h2" component="h1" sx={{ letterSpacing: '-0.01em' }}>
              {SYSTEM_NAME}
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
              标准集中配置 · 判定服务端执行 · 异常闭环留痕
            </Typography>
          </Box>
        </Stack>

        <Paper sx={{ p: 4, boxShadow: shadow.card }}>
          <Stack component="form" spacing={3} onSubmit={handleSubmit}>
            {error && <Alert severity="error">{error}</Alert>}
            <Stack spacing={1}>
              <Typography component="label" htmlFor="login-username" variant="body2" sx={{ fontWeight: 550 }}>
                用户名
              </Typography>
              <TextField
                id="login-username"
                name="username"
                value={username}
                onChange={(event) => setUsername(event.target.value)}
                required
                autoComplete="username"
                slotProps={{ htmlInput: { 'aria-label': '用户名' } }}
              />
            </Stack>
            <Stack spacing={1}>
              <Typography component="label" htmlFor="login-password" variant="body2" sx={{ fontWeight: 550 }}>
                密码
              </Typography>
              <TextField
                id="login-password"
                name="password"
                type={passwordVisible ? 'text' : 'password'}
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                required
                autoComplete="current-password"
                slotProps={{
                  htmlInput: { 'aria-label': '密码' },
                  input: {
                    endAdornment: (
                      <PasswordVisibilityAdornment
                        visible={passwordVisible}
                        onToggle={() => setPasswordVisible((visible) => !visible)}
                      />
                    ),
                  },
                }}
              />
            </Stack>
            <Button type="submit" variant="contained" size="large" disabled={submitting}>
              登录
            </Button>
          </Stack>
        </Paper>

        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', textAlign: 'center', mt: 3 }}>
          内部系统 · 仅限授权人员使用
        </Typography>
      </Box>
    </Box>
  );
}
