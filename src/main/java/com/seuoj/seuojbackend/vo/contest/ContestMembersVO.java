package com.seuoj.seuojbackend.vo.contest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.seuoj.seuojbackend.vo.permission.ResourcePermissionVO;
import java.util.List;

public record ContestMembersVO(
        @JsonProperty("creator_user_id") Long creatorUserId,
        List<ResourcePermissionVO> administrators,
        List<Participant> participants) {
    public record Participant(
            Long id,
            @JsonProperty("user_id") Long userId,
            String username,
            String nickname,
            @JsonProperty("display_name") String displayName,
            @JsonProperty("is_starred") boolean isStarred,
            @JsonProperty("is_managed") boolean isManaged,
            @JsonProperty("student_no") String studentNo,
            String college,
            @JsonProperty("class_name") String className,
            @JsonProperty("contact_email") String contactEmail,
            String notes,
            @JsonProperty("account_status") String accountStatus,
            @JsonProperty("can_manage_account") boolean canManageAccount) {}

    public record AccountOption(@JsonProperty("user_id") Long userId, String username, String nickname,
                                @JsonProperty("is_enabled") boolean isEnabled) {}

    public record AccountPlan(Long id, @JsonProperty("display_name") String displayName,
                              String username, String error) {}
    public record Credential(Long id, @JsonProperty("display_name") String displayName,
                             String username, String password) {
        @Override public String toString() { return "Credential[id=" + id + ", username=" + username + "]"; }
    }
}
