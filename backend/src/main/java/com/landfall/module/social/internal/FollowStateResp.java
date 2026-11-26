package com.landfall.module.social.internal;

/**
 * 关注/取关后的最新关系状态，调用方直接用返回值刷新按钮与粉丝数，无需二次查询。
 */
record FollowStateResp(boolean following, boolean mutualFollow, long followerCount) {}
