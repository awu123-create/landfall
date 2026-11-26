-- 关注关系：单向 Follow 记录；双向记录实时构成 MutualFollow（互关好友）。
-- 按 docs/standards/database.md「禁存竞态派生布尔列」，不设 is_mutual 冗余列，互关一律用 EXISTS 判定。

CREATE TABLE user_follows (
    id UUID PRIMARY KEY,
    follower_id UUID NOT NULL REFERENCES sys_user (id) ON DELETE CASCADE,
    followee_id UUID NOT NULL REFERENCES sys_user (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_user_follows_pair UNIQUE (follower_id, followee_id),
    CONSTRAINT chk_user_follows_not_self CHECK (follower_id <> followee_id)
);

-- 粉丝列表与互关 EXISTS 判定；唯一约束已覆盖 (follower_id, followee_id) 前缀。
CREATE INDEX idx_user_follows_followee_follower
    ON user_follows (followee_id, follower_id);
