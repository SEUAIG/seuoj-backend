package com.seuoj.seuojbackend.vo.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TokenExchangeVO {
    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("temp_token")
    private String tempToken;
}
