import { Box, Typography } from '@mui/material';

export default function PlaceholderPage({ title }: { title: string }) {
  return (
    <Box>
      <Typography variant="h3" component="h1">
        {title}
      </Typography>
      <Typography variant="body2" color="text.secondary">
        功能开发中
      </Typography>
    </Box>
  );
}
