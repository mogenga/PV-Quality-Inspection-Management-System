INSERT INTO app_user (username, password_hash, display_name, role, is_active, created_at) VALUES
('admin',    '$2a$10$uWiy0CLudTWpdlSImZqJsOPAetd0f79tK5aszgQE7/k3u2OSQoV8S', '系统管理员', 'ADMIN',     1, NOW()),
('engineer', '$2a$10$NGst6A2zk.2hFW1Lss2F0OykUrTYI171qL9FzFD7Ds8M/Y3NfqVEC', '张工程师',   'ENGINEER',  1, NOW()),
('inspector','$2a$10$itVjUn0Pc3P1lK42MezSKuaTQnryUOHwjIEitv.EfE/pKGlbZBOjK', '李检验员',   'INSPECTOR', 1, NOW());
