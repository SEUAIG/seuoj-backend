package com.seuoj.seuojbackend.common;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用返回结果封装类
 * @param <T> 返回数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> {
    private Integer code;
    private String message;
    private T data;

    public static Result<Void> success() {
        return new Result<>(0, "success", null);
    }
    public static <T> Result<T> success(T data) {
        return new Result<>(0, "success", data);
    }

    public static Result<Void> onlySuccess(String message) {
        return new Result<>(0, message, null);
    }

    public static <T> Result<T> error(Integer code, String message){
        return new Result<>(code, message, null);
    }
}
