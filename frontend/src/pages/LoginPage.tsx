import { useState } from 'react';
import type { FormEvent } from 'react';
import { Alert, Box, Button, Paper, Stack, TextField, Typography } from '@mui/material';
import { alpha } from '@mui/material/styles';
import { BugReport, FactCheck, Rule, WbSunny } from '@mui/icons-material';
import { useNavigate } from 'react-router';
import { login } from '../api/auth';
import { ApiError } from '../api/client';
import { useAuthStore } from '../stores/authStore';
import { HOME_BY_ROLE } from '../utils/labels';
import { color, radius, shadow } from '../theme/muiTheme';
import BrandBackdrop from './login/BrandBackdrop';

const SYSTEM_NAME = '光伏组件校验数字化平台';

const FEATURES: { icon: typeof Rule; text: string }[] = [
  { icon: Rule, text: '校验标准可配置，新增检验项不改代码' },
  { icon: FactCheck, text: '判定由服务端执行，不信任前端结果' },
  { icon: BugReport, text: '不合格自动进缺陷闭环，状态严格流转' },
];

export default function LoginPage() {
  const navigate = useNavigate();
  const setSession = useAuthStore((state) => state.setSession);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
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
    <Box sx={{ minHeight: '100dvh', display: 'flex' }}>
      <Box
        sx={{
          display: { xs: 'none', md: 'flex' },
          flex: '1 1 45%',
          position: 'relative',
          overflow: 'hidden',
          bgcolor: color.fg,
          p: 6,
        }}
      >
        <BrandBackdrop />
        <Box sx={{ position: 'relative', zIndex: 1, display: 'flex', flexDirection: 'column', flexGrow: 1 }}>
          <Box sx={{ my: 'auto' }}>
            <Box
              sx={{
                width: 48,
                height: 48,
                borderRadius: `${radius.sm}px`,
                bgcolor: color.surface1,
                color: color.fg,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                mb: 4,
              }}
            >
              <WbSunny fontSize="small" />
            </Box>
            <Typography variant="h1" component="p" sx={{ color: color.surface1, lineHeight: 1.15, maxWidth: 400 }}>
              {SYSTEM_NAME}
            </Typography>
            <Typography variant="body1" sx={{ mt: 3, color: alpha(color.surface1, 0.7), maxWidth: 400 }}>
              标准集中配置 · 判定服务端执行 · 异常闭环留痕
            </Typography>
            <Stack spacing={2} sx={{ mt: 6 }}>
              {FEATURES.map(({ icon: Icon, text }) => (
                <Stack key={text} direction="row" spacing={2} sx={{ alignItems: 'center' }}>
                  <Box sx={{ color: color.surface1, display: 'flex', flexShrink: 0 }}>
                    <Icon sx={{ fontSize: 18 }} />
                  </Box>
                  <Typography variant="body2" sx={{ color: alpha(color.surface1, 0.8) }}>
                    {text}
                  </Typography>
                </Stack>
              ))}
            </Stack>
          </Box>
          <Typography variant="caption" sx={{ pt: 4, color: alpha(color.surface1, 0.5) }}>
            内部系统 · 仅限授权人员使用
          </Typography>
        </Box>
      </Box>
      <Box
        sx={{
          flex: '1 1 55%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          bgcolor: 'background.default',
          p: 2,
        }}
      >
        <Paper sx={{ width: '100%', maxWidth: 400, p: 4, boxShadow: shadow.card }}>
          <Stack component="form" spacing={3} onSubmit={handleSubmit}>
            <Box>
              <Typography variant="h2" component="h1">
                {SYSTEM_NAME}
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                请使用您的账号登录
              </Typography>
            </Box>
            {error && <Alert severity="error">{error}</Alert>}
            <TextField
              label="用户名"
              name="username"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              required
              slotProps={{ inputLabel: { required: false } }}
            />
            <TextField
              label="密码"
              name="password"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
              slotProps={{ inputLabel: { required: false } }}
            />
            <Button type="submit" variant="contained" disabled={submitting}>
              登录
            </Button>
            <Typography variant="caption" color="text.secondary">
              默认账号见 README
            </Typography>
          </Stack>
        </Paper>
      </Box>
    </Box>
  );
}
