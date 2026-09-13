-- ------------------------- info -------------------------
-- @@ver: 1_000_003
-- @@info: add menu
-- ------------------------- info -------------------------

INSERT INTO "base_rbac_menu" (
  "id", "parent_id", "scope", "name", "sort", "level", "icon", "status", "link_type", "link_url",
  "crt_time", "crt_user", "crt_name", "crt_host", "upd_time", "upd_user", "upd_name", "upd_host", "deleted"
)
SELECT 22020001, 13000000, 1, '表单管理', 4, 1, 'mdi:form-select', TRUE, 1,
       '/admin/flow/manage/form', '2025-12-16 16:00:27', '1', '超级管理员', '192.168.5.88',
       '2025-12-16 16:00:27', '1', '超级管理员', '127.0.0.1', FALSE
WHERE NOT EXISTS (
  SELECT 1 FROM "base_rbac_menu"
  WHERE "id" = 22020001 OR "link_url" = '/admin/flow/manage/form'
)
ON CONFLICT ("id") DO NOTHING;

INSERT INTO "base_rbac_menu" (
  "id", "parent_id", "scope", "name", "sort", "level", "icon", "status", "link_type", "link_url",
  "crt_time", "crt_user", "crt_name", "crt_host", "upd_time", "upd_user", "upd_name", "upd_host", "deleted"
)
SELECT 22020002, 10000000, 1, '功能示例', 3, 1, 'mdi:ev-plug-chademo', TRUE, 1,
       '/admin/flow/view/form', '2025-12-27 22:27:16', '1', '超级管理员', '169.254.143.154',
       '2025-12-27 22:27:15', '1', '超级管理员', '127.0.0.1', FALSE
WHERE NOT EXISTS (
  SELECT 1 FROM "base_rbac_menu"
  WHERE "id" = 22020002 OR "link_url" = '/admin/flow/view/form'
)
ON CONFLICT ("id") DO NOTHING;

SELECT setval(
    pg_get_serial_sequence('base_rbac_menu', 'id'),
    COALESCE((SELECT MAX("id") FROM "base_rbac_menu"), 1),
    TRUE
);
