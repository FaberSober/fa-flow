-- ------------------------- info -------------------------
-- @@ver: 1_000_007
-- @@info: 补充自定义表单管理菜单
-- ------------------------- info -------------------------

INSERT INTO "base_rbac_menu" (
  "id", "parent_id", "scope", "name", "sort", "level", "icon", "status", "link_type", "link_url",
  "crt_time", "crt_user", "crt_name", "crt_host", "upd_time", "upd_user", "upd_name", "upd_host", "deleted"
)
SELECT 13000400, 13000000, 1, '表单管理', 4, 1, 'mdi:form-select', TRUE, 1,
       '/admin/flow/manage/form', CURRENT_TIMESTAMP, '1', '超级管理员', '127.0.0.1',
       CURRENT_TIMESTAMP, '1', '超级管理员', '127.0.0.1', FALSE
WHERE NOT EXISTS (
  SELECT 1 FROM "base_rbac_menu"
  WHERE "id" = 13000400 OR "link_url" = '/admin/flow/manage/form'
)
ON CONFLICT ("id") DO NOTHING;

SELECT setval(
    pg_get_serial_sequence('base_rbac_menu', 'id'),
    COALESCE((SELECT MAX("id") FROM "base_rbac_menu"), 1),
    TRUE
);
