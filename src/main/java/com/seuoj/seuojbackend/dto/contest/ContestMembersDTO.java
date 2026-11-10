package com.seuoj.seuojbackend.dto.contest;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public final class ContestMembersDTO {
    private ContestMembersDTO() {}

    public record Administrator(@Size(max = 100) String username, @JsonProperty("user_id") @Positive Long userId) {}

    public record Participant(
            @Size(max = 64) String username,
            @JsonProperty("display_name") @NotBlank @Size(max = 100) String displayName,
            @JsonProperty("is_starred") Boolean isStarred,
            @Positive Long id,
            @JsonProperty("student_no") @Size(max = 100) String studentNo,
            @Size(max = 100) String college,
            @JsonProperty("class_name") @Size(max = 100) String className,
            @JsonProperty("contact_email") @Email @Size(max = 254) String contactEmail,
            @Size(max = 1000) String notes,
            @JsonProperty("user_id") @Positive Long userId) {
        public Participant(String username, String displayName, Boolean isStarred) {
            this(username, displayName, isStarred, null, null, null, null, null, null, null);
        }
    }

    public record Batch(@NotEmpty @Size(max = 1000) List<@NotNull @Valid Participant> participants) {}

    public record Selection(@NotEmpty @Size(max = 1000) List<@NotNull @Positive Long> ids,
                            @NotBlank @Pattern(regexp = "star|unstar|remove") String action) {}

    public record Generate(@NotEmpty @Size(max = 100) List<@NotNull @Positive Long> ids,
                           @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,31}") String prefix,
                           @NotNull @Min(1) @Max(999999000) Integer start,
                           @NotNull @Min(1) @Max(9) Integer digits) {}

    public record Accounts(@NotEmpty @Size(max = 100) List<@NotNull @Positive Long> ids,
                           Boolean enabled) {}
}
