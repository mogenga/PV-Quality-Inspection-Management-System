import {
  AppBar,
  Box,
  Button,
  Chip,
  Drawer,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Stack,
  Toolbar,
  Typography,
} from '@mui/material';
import {
  AccountTreeOutlined,
  BugReportOutlined,
  DashboardOutlined,
  FactCheckOutlined,
  HistoryOutlined,
  PeopleOutlined,
  RuleOutlined,
} from '@mui/icons-material';
import { Link as RouterLink, Outlet, useLocation, useNavigate } from 'react-router';
import { logout } from '../api/auth';
import { useAuthStore } from '../stores/authStore';
import { ROLE_LABELS } from '../utils/labels';
import type { Role } from '../utils/labels';
import BrandMark from '../pages/login/BrandMark';

type MenuItem = { key: string; label: string; icon: typeof DashboardOutlined; roles: Role[] };

export const MENU_ITEMS: MenuItem[] = [
  { key: '/dashboard', label: '质量看板', icon: DashboardOutlined, roles: ['ENGINEER', 'INSPECTOR', 'ADMIN'] },
  { key: '/projects', label: '项目与批次', icon: AccountTreeOutlined, roles: ['ENGINEER', 'INSPECTOR', 'ADMIN'] },
  { key: '/standards', label: '校验标准', icon: RuleOutlined, roles: ['ENGINEER', 'ADMIN'] },
  { key: '/inspection', label: '校验录入', icon: FactCheckOutlined, roles: ['ENGINEER', 'INSPECTOR'] },
  { key: '/records', label: '校验记录', icon: HistoryOutlined, roles: ['ENGINEER', 'INSPECTOR', 'ADMIN'] },
  { key: '/defects', label: '缺陷处理', icon: BugReportOutlined, roles: ['ENGINEER', 'INSPECTOR', 'ADMIN'] },
  { key: '/users', label: '用户管理', icon: PeopleOutlined, roles: ['ADMIN'] },
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
    } catch {
      // 会话已在别处失效或网络异常时接口会拒绝，本地登录态无论如何都要清
    } finally {
      clear();
      navigate('/login', { replace: true });
    }
  };

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', minHeight: '100vh', bgcolor: 'background.default' }}>
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
        <Toolbar sx={{ gap: 2 }}>
          <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
            <BrandMark size={28} />
            <Typography variant="h3" component="span">
              光伏组件校验数字化平台
            </Typography>
          </Stack>
          <Box sx={{ flexGrow: 1 }} />
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
      <Box sx={{ display: 'flex', flexGrow: 1, minHeight: 0 }}>
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
              borderRight: '1px solid',
              borderColor: 'divider',
              // 顶栏通栏后，侧栏不再占满视口高度
              position: 'relative',
              height: '100%',
            },
          }}
        >
          <List>
            {MENU_ITEMS.filter((item) => user && item.roles.includes(user.role)).map((item) => {
              // 图标不得进入可访问名：MUI 图标默认 aria-hidden，链接名仍是纯文字标签
              const Icon = item.icon;
              return (
                <ListItemButton
                  key={item.key}
                  component={RouterLink}
                  to={item.key}
                  selected={pathname === item.key}
                  aria-current={pathname === item.key ? 'page' : undefined}
                >
                  <ListItemIcon sx={{ color: 'inherit' }}>
                    <Icon fontSize="small" />
                  </ListItemIcon>
                  <ListItemText primary={item.label} />
                </ListItemButton>
              );
            })}
          </List>
        </Drawer>
        <Box component="main" sx={{ flexGrow: 1, minWidth: 0, p: 3 }}>
          <Outlet />
        </Box>
      </Box>
    </Box>
  );
}
