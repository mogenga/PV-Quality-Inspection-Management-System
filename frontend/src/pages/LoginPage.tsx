import { useState } from 'react';
import type { FormEvent } from 'react';
import { Alert, Box, Button, Paper, Stack, TextField, Typography } from '@mui/material';
import { useNavigate } from 'react-router';
import { login } from '../api/auth';
import { ApiError } from '../api/client';
import { useAuthStore } from '../stores/authStore';
import { HOME_BY_ROLE } from '../utils/labels';

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
    <Box
      sx={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        bgcolor: 'background.default',
        p: 2,
      }}
    >
      <Paper sx={{ width: '100%', maxWidth: 400, p: 4 }}>
        <Stack component="form" spacing={3} onSubmit={handleSubmit}>
          <Typography variant="h2" component="h1">
            光伏组件校验数字化平台
          </Typography>
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
        </Stack>
      </Paper>
    </Box>
  );
}
