package com.seuoj.seuojbackend.vo.discussion;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VoteResultVO(
        int score,
        @JsonProperty("my_vote") int myVote) {
}
