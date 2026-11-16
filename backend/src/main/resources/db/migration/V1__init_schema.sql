CREATE TABLE app_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  display_name VARCHAR(50) NOT NULL,
  role VARCHAR(20) NOT NULL,
  is_active TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL,
  UNIQUE KEY uk_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE project (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_code VARCHAR(50) NOT NULL,
  project_name VARCHAR(100) NOT NULL,
  product_model VARCHAR(50) NULL,
  customer_name VARCHAR(100) NULL,
  manufacturer_name VARCHAR(100) NULL,
  location VARCHAR(100) NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  remark VARCHAR(255) NULL,
  created_at DATETIME NOT NULL,
  UNIQUE KEY uk_project_code (project_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE batch (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  batch_no VARCHAR(50) NOT NULL,
  planned_qty INT NOT NULL,
  start_date DATE NULL,
  end_date DATE NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
  remark VARCHAR(255) NULL,
  created_at DATETIME NOT NULL,
  UNIQUE KEY uk_batch_project_no (project_id, batch_no),
  CONSTRAINT fk_batch_project FOREIGN KEY (project_id) REFERENCES project (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE inspection_standard (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  scene VARCHAR(10) NOT NULL,
  process_name VARCHAR(50) NOT NULL,
  item_code VARCHAR(30) NOT NULL,
  item_name VARCHAR(100) NOT NULL,
  unit VARCHAR(20) NULL,
  value_type VARCHAR(10) NOT NULL,
  judge_type VARCHAR(20) NOT NULL,
  lower_limit DECIMAL(12,4) NULL,
  upper_limit DECIMAL(12,4) NULL,
  target_value DECIMAL(12,4) NULL,
  tolerance DECIMAL(12,4) NULL,
  sampling_note VARCHAR(100) NULL,
  is_enabled TINYINT NOT NULL DEFAULT 1,
  sort_order INT NOT NULL DEFAULT 0,
  remark VARCHAR(255) NULL,
  created_at DATETIME NOT NULL,
  UNIQUE KEY uk_std_project_item (project_id, item_code),
  CONSTRAINT fk_std_project FOREIGN KEY (project_id) REFERENCES project (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE inspection_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  batch_id BIGINT NOT NULL,
  standard_id BIGINT NOT NULL,
  scene VARCHAR(10) NOT NULL,
  process_name VARCHAR(50) NOT NULL,
  item_name VARCHAR(100) NOT NULL,
  serial_no VARCHAR(50) NULL,
  inspector_id BIGINT NOT NULL,
  inspect_time DATETIME NOT NULL,
  measured_value DECIMAL(12,4) NULL,
  text_value VARCHAR(100) NULL,
  result VARCHAR(10) NOT NULL,
  std_lower DECIMAL(12,4) NULL,
  std_upper DECIMAL(12,4) NULL,
  std_target DECIMAL(12,4) NULL,
  std_tolerance DECIMAL(12,4) NULL,
  std_unit VARCHAR(20) NULL,
  is_void TINYINT NOT NULL DEFAULT 0,
  void_reason VARCHAR(255) NULL,
  remark VARCHAR(255) NULL,
  created_at DATETIME NOT NULL,
  KEY idx_rec_project_time (project_id, inspect_time),
  KEY idx_rec_batch (batch_id),
  KEY idx_rec_standard_time (standard_id, inspect_time),
  KEY idx_rec_result (result),
  CONSTRAINT fk_rec_project FOREIGN KEY (project_id) REFERENCES project (id),
  CONSTRAINT fk_rec_batch FOREIGN KEY (batch_id) REFERENCES batch (id),
  CONSTRAINT fk_rec_standard FOREIGN KEY (standard_id) REFERENCES inspection_standard (id),
  CONSTRAINT fk_rec_inspector FOREIGN KEY (inspector_id) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE defect (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  record_id BIGINT NOT NULL,
  defect_type VARCHAR(50) NOT NULL,
  defect_grade VARCHAR(10) NOT NULL,
  defect_status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  handler_id BIGINT NULL,
  handle_note VARCHAR(255) NULL,
  resolved_at DATETIME NULL,
  created_at DATETIME NOT NULL,
  UNIQUE KEY uk_defect_record (record_id),
  KEY idx_defect_status_grade (defect_status, defect_grade),
  CONSTRAINT fk_defect_record FOREIGN KEY (record_id) REFERENCES inspection_record (id),
  CONSTRAINT fk_defect_handler FOREIGN KEY (handler_id) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE VIEW v_batch_pass_rate AS
SELECT r.batch_id,
       COUNT(*)                                           AS total,
       SUM(r.result = 'PASS')                             AS passed,
       ROUND(SUM(r.result = 'PASS') / COUNT(*) * 100, 2)  AS pass_rate
FROM inspection_record r
WHERE r.is_void = 0
GROUP BY r.batch_id;

CREATE VIEW v_defect_detail AS
SELECT d.id            AS defect_id,
       d.record_id,
       r.project_id,
       p.project_code,
       p.project_name,
       b.batch_no,
       r.scene,
       r.process_name,
       r.item_name,
       r.serial_no,
       r.inspect_time,
       r.std_lower,
       r.std_upper,
       r.std_target,
       r.std_tolerance,
       r.std_unit,
       d.defect_type,
       d.defect_grade,
       d.defect_status,
       d.handle_note,
       d.resolved_at,
       d.created_at    AS defect_created_at,
       i.display_name  AS inspector_name,
       h.display_name  AS handler_name
FROM defect d
JOIN inspection_record r ON r.id = d.record_id
JOIN batch b ON b.id = r.batch_id
JOIN project p ON p.id = r.project_id
JOIN app_user i ON i.id = r.inspector_id
LEFT JOIN app_user h ON h.id = d.handler_id;
