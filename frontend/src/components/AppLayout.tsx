import { AppBar, Box, Button, Chip, Drawer, List, ListItemButton, ListItemText, Stack, Toolbar } from '@mui/material';
import { Link as RouterLink, Outlet, useLocation, useNavigate } from 'react-router';
import { logout } from '../api/auth';
import { useAuthStore } from '../stores/authStore';
import { ROLE_LABELS } from '../utils/labels';
import type { Role } from '../utils/labels';

export const MENU_ITEMS: { key: string; label: string; roles: Role[] }[] = [
  { key: '/dashboard', label: '质量看板', roles: ['ENGINEER', 'INSPECTOR', 'ADMIN'] },
  { key: '/projects', label: '项目与批次', roles: ['ENGINEER', 'INSPECTOR', 'ADMIN'] },
  { key: '/standards', label: '校验标准', roles: ['ENGINEER', 'ADMIN'] },
  { key: '/inspection', label: '校验录入', roles: ['ENGINEER', 'INSPECTOR'] },
  { key: '/defects', label: '缺陷处理', roles: ['ENGINEER', 'INSPECTOR', 'ADMIN'] },
  { key: '/users', label: '用户管理', roles: ['ADMIN'] },
];

const DRAWER_WIDTH = 240;

export default function AppLayout() {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const user = useAuthStore((state) => state.user);
  const clear = useAuthStore((state) => state.clear);

  const handleLogout = async () => {
    try {
      await logout();
    } finally {
      clear();
      navigate('/login', { replace: true });
    }
  };

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh', bgcolor: 'background.default' }}>
      <Drawer
        variant="permanent"
        sx={{
          width: DRAWER_WIDTH,
          flexShrink: 0,
          // 侧栏是页面骨架，不是卡片：抵消主题给 MuiPaper 的 20px 大圆角
          '& .MuiDrawer-paper': {
            width: DRAWER_WIDTH,
            boxSizing: 'border-box',
            borderRadius: 0,
            bgcolor: 'background.paper',
          },
        }}
      >
        <List>
          {MENU_ITEMS.filter((item) => user && item.roles.includes(user.role)).map((item) => (
            <ListItemButton key={item.key} component={RouterLink} to={item.key} selected={pathname === item.key}>
              <ListItemText primary={item.label} />
            </ListItemButton>
          ))}
        </List>
      </Drawer>
      <Box sx={{ flexGrow: 1, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
        <AppBar
          color="inherit"
          position="static"
          elevation={0}
          sx={{
            bgcolor: 'background.paper',
            borderRadius: 0,
            borderBottom: '1px solid',
            borderColor: 'divider',
          }}
        >
          <Toolbar sx={{ justifyContent: 'flex-end', gap: 2 }}>
            {user && (
              <>
                <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
                  <Box component="span">{user.displayName}</Box>
                  <Chip size="small" label={ROLE_LABELS[user.role]} />
                </Stack>
                <Button onClick={handleLogout}>退出登录</Button>
              </>
            )}
          </Toolbar>
        </AppBar>
        <Box component="main" sx={{ flexGrow: 1, p: 3 }}>
          <Outlet />
        </Box>
      </Box>
    </Box>
  );
}
