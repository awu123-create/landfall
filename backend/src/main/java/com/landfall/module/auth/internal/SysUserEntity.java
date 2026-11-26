package com.landfall.module.auth.internal;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.util.UUID;

@TableName("sys_user")
class SysUserEntity {

    @TableId
    private UUID id;

    private String username;
    private String email;
    private String passwordHash;
    private String nickname;
    private String avatarUrl;
    private String bio;
    private UserStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;

    UUID getId() {
        return id;
    }

    void setId(UUID id) {
        this.id = id;
    }

    String getUsername() {
        return username;
    }

    void setUsername(String username) {
        this.username = username;
    }

    String getEmail() {
        return email;
    }

    void setEmail(String email) {
        this.email = email;
    }

    String getPasswordHash() {
        return passwordHash;
    }

    void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    String getNickname() {
        return nickname;
    }

    void setNickname(String nickname) {
        this.nickname = nickname;
    }

    String getAvatarUrl() {
        return avatarUrl;
    }

    String getBio() {
        return bio;
    }

    UserStatus getStatus() {
        return status;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    Instant getDeletedAt() {
        return deletedAt;
    }

    void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
