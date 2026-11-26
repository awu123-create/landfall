-- Landfall（见屿）RBAC 基线表与活跃行为流水表

CREATE TABLE sys_user (
    id UUID PRIMARY KEY,
    username VARCHAR(32) NOT NULL,
    email VARCHAR(128) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname VARCHAR(64) NOT NULL,
    avatar_url VARCHAR(512),
    bio VARCHAR(255) NOT NULL DEFAULT '',
    status VARCHAR(16) NOT NULL DEFAULT 'NORMAL' CHECK (status IN ('NORMAL', 'BANNED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX uk_sys_user_username_active
    ON sys_user (LOWER(username))
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uk_sys_user_email_active
    ON sys_user (LOWER(email))
    WHERE deleted_at IS NULL;

CREATE TABLE sys_role (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(64) NOT NULL,
    description VARCHAR(255) NOT NULL DEFAULT '',
    is_builtin BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE sys_permission (
    id UUID PRIMARY KEY,
    parent_id UUID REFERENCES sys_permission (id) ON DELETE RESTRICT,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(64) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_sys_permission_no_self_parent CHECK (parent_id IS NULL OR parent_id <> id)
);

CREATE INDEX idx_sys_permission_parent_sort
    ON sys_permission (parent_id, sort_order);

CREATE TABLE sys_user_role (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES sys_user (id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES sys_role (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_sys_user_role UNIQUE (user_id, role_id)
);

CREATE INDEX idx_sys_user_role_role_id
    ON sys_user_role (role_id);

CREATE TABLE sys_role_permission (
    id UUID PRIMARY KEY,
    role_id UUID NOT NULL REFERENCES sys_role (id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES sys_permission (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_sys_role_permission UNIQUE (role_id, permission_id)
);

CREATE INDEX idx_sys_role_permission_permission_id
    ON sys_role_permission (permission_id);

CREATE TABLE user_activity_logs (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES sys_user (id) ON DELETE CASCADE,
    action_type VARCHAR(32) NOT NULL,
    target_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_user_activity_logs_created_user
    ON user_activity_logs (created_at, user_id);

CREATE INDEX idx_user_activity_logs_user_created
    ON user_activity_logs (user_id, created_at);

-- 内置保护角色种子（固定 RFC 9562 UUIDv7）
INSERT INTO sys_role (id, code, name, description, is_builtin) VALUES
    ('019ac0d6-d400-7000-8000-000000000001', 'ROLE_USER', '普通用户', '默认社区注册用户角色', true),
    ('019ac0d6-d400-7000-8000-000000000002', 'ROLE_MODERATOR', '内容审核员', '负责内容复审、敏感词治理与基础运维', true),
    ('019ac0d6-d400-7000-8000-000000000003', 'ROLE_ADMIN', '超级管理员', '拥有系统全部管理权限', true);

-- 初始两级 PermissionCode 权限树种子
INSERT INTO sys_permission (id, parent_id, code, name, sort_order) VALUES
    ('019ac0d6-d400-7000-8000-000000000100', NULL, 'admin:access', '后台访问准入', 10),
    ('019ac0d6-d400-7000-8000-000000000101', '019ac0d6-d400-7000-8000-000000000100', 'admin:user:manage', '用户管理', 20),
    ('019ac0d6-d400-7000-8000-000000000102', '019ac0d6-d400-7000-8000-000000000100', 'admin:role:manage', '角色与权限管理', 30),
    ('019ac0d6-d400-7000-8000-000000000103', '019ac0d6-d400-7000-8000-000000000100', 'admin:post:manage', '作品管理', 40),
    ('019ac0d6-d400-7000-8000-000000000104', '019ac0d6-d400-7000-8000-000000000100', 'admin:comment:manage', '评论管理', 50),
    ('019ac0d6-d400-7000-8000-000000000105', '019ac0d6-d400-7000-8000-000000000100', 'admin:tag:manage', '标签管理', 60),
    ('019ac0d6-d400-7000-8000-000000000106', '019ac0d6-d400-7000-8000-000000000100', 'admin:audit:review', '内容复审', 70),
    ('019ac0d6-d400-7000-8000-000000000107', '019ac0d6-d400-7000-8000-000000000100', 'admin:word:manage', '敏感词库管理', 80),
    ('019ac0d6-d400-7000-8000-000000000108', '019ac0d6-d400-7000-8000-000000000100', 'admin:stat:view', '活跃统计查看', 90);

-- 超级管理员绑定全部 9 个权限点，内容审核员绑定后台准入及内容治理相关 7 个权限点
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT seed.id::uuid, r.id, p.id
FROM (VALUES
    ('019ac0d6-d400-7000-8000-000000000201', 'ROLE_ADMIN', 'admin:access'),
    ('019ac0d6-d400-7000-8000-000000000202', 'ROLE_ADMIN', 'admin:user:manage'),
    ('019ac0d6-d400-7000-8000-000000000203', 'ROLE_ADMIN', 'admin:role:manage'),
    ('019ac0d6-d400-7000-8000-000000000204', 'ROLE_ADMIN', 'admin:post:manage'),
    ('019ac0d6-d400-7000-8000-000000000205', 'ROLE_ADMIN', 'admin:comment:manage'),
    ('019ac0d6-d400-7000-8000-000000000206', 'ROLE_ADMIN', 'admin:tag:manage'),
    ('019ac0d6-d400-7000-8000-000000000207', 'ROLE_ADMIN', 'admin:audit:review'),
    ('019ac0d6-d400-7000-8000-000000000208', 'ROLE_ADMIN', 'admin:word:manage'),
    ('019ac0d6-d400-7000-8000-000000000209', 'ROLE_ADMIN', 'admin:stat:view'),
    ('019ac0d6-d400-7000-8000-000000000210', 'ROLE_MODERATOR', 'admin:access'),
    ('019ac0d6-d400-7000-8000-000000000211', 'ROLE_MODERATOR', 'admin:post:manage'),
    ('019ac0d6-d400-7000-8000-000000000212', 'ROLE_MODERATOR', 'admin:comment:manage'),
    ('019ac0d6-d400-7000-8000-000000000213', 'ROLE_MODERATOR', 'admin:tag:manage'),
    ('019ac0d6-d400-7000-8000-000000000214', 'ROLE_MODERATOR', 'admin:audit:review'),
    ('019ac0d6-d400-7000-8000-000000000215', 'ROLE_MODERATOR', 'admin:word:manage'),
    ('019ac0d6-d400-7000-8000-000000000216', 'ROLE_MODERATOR', 'admin:stat:view')
) AS seed(id, role_code, perm_code)
JOIN sys_role r ON r.code = seed.role_code
JOIN sys_permission p ON p.code = seed.perm_code;
