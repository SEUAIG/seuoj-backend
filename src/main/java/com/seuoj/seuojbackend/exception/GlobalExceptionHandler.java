package com.seuoj.seuojbackend.exception;

import com.seuoj.seuojbackend.common.ErrorCode;
import com.seuoj.seuojbackend.common.Result;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理请求参数错误异常 - 400
     */
    @ExceptionHandler(BadRequestException.class)
    @ResponseBody
    public ResponseEntity<Result<Object>> handleBadRequestException(BadRequestException e) {
        log.warn("BadRequestException: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseBody
    public ResponseEntity<Result<Object>> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("请求参数解析失败: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(ErrorCode.PARAMS_ERROR.getCode(), "请求参数格式错误"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseBody
    public ResponseEntity<Result<Object>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        log.warn("参数校验失败: {}", e.getMessage());
        String message = e.getBindingResult().getAllErrors().getFirst().getDefaultMessage();
        log.warn("MethodArgumentNotValidException: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(ErrorCode.PARAMS_ERROR.getCode(), message));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Object>> handleMissingParam(MissingServletRequestParameterException ex) {
        log.warn("缺少请求参数: {}", ex.getParameterName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(ErrorCode.PARAMS_ERROR.getCode(), "缺少请求参数: " + ex.getParameterName()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("参数类型不匹配: {} - {}", ex.getName(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(ErrorCode.PARAMS_ERROR.getCode(), "参数类型不匹配"));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseBody
    public ResponseEntity<Result<Object>> handleConstraintViolationException(ConstraintViolationException e) {
        log.warn("参数约束校验失败: {}", e.getMessage());
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .findFirst()
                .orElse("请求参数不合法");
        log.warn("ConstraintViolationException: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(ErrorCode.PARAMS_ERROR.getCode(), message));
    }

    /**
     * 处理权限认证异常 - 401
     */
    @ExceptionHandler(AuthorizationException.class)
    @ResponseBody
    public ResponseEntity<Result<Object>> handleAuthorizationException(AuthorizationException e) {
        log.warn("AuthorizationException: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Result.error(e.getCode(), e.getMessage()));
    }

    /**
     * 处理权限不足异常 - 403
     */
    @ExceptionHandler(ForbiddenException.class)
    @ResponseBody
    public ResponseEntity<Result<Object>> handleForbiddenException(ForbiddenException e) {
        log.warn("ForbiddenException: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Result.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(JudgeAuthException.class)
    @ResponseBody
    public ResponseEntity<Result<Object>> handleJudgeAuthException(JudgeAuthException e) {
        log.warn("JudgeAuthException: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Result.error(e.getCode(), e.getMessage()));
    }

    /**
     * 处理资源未找到异常 - 404
     */
    @ExceptionHandler(NotFoundException.class)
    @ResponseBody
    public ResponseEntity<Result<Object>> handleNotFoundException(NotFoundException e) {
        log.warn("NotFoundException: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseBody
    public ResponseEntity<Result<Object>> handleNoResourceFoundException(NoResourceFoundException e) {
        log.warn("NoResourceFoundException: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(ErrorCode.NOT_FOUND_ERROR.getCode(), "请求资源不存在"));
    }

    /**
     * 处理业务冲突异常 - 409
     */
    @ExceptionHandler(ConflictException.class)
    @ResponseBody
    public ResponseEntity<Result<Object>> handleConflictException(ConflictException e) {
        log.warn("ConflictException: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Result.error(e.getCode(), e.getMessage()));
    }

    /**
     * 处理第三方服务异常 - 502
     */
    @ExceptionHandler(ThirdPartyException.class)
    @ResponseBody
    public ResponseEntity<Result<Object>> handleThirdPartyException(ThirdPartyException e) {
        log.error("ThirdPartyException: ", e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Result.error(e.getCode(), "第三方服务暂时不可用"));
    }

    /**
     * 处理系统内部异常 - 500
     */
    @ExceptionHandler(InternalServerException.class)
    @ResponseBody
    public ResponseEntity<Result<Object>> handleInternalServerException(InternalServerException e) {
        log.error("系统内部异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(e.getCode(), "系统内部异常，请联系管理员"));
    }

    /**
     * 评测端服务异常 - 502
     */
    @ExceptionHandler(JudgeRemoteException.class)
    @ResponseBody
    public ResponseEntity<Result<Object>> handleJudgeRemoteException(JudgeRemoteException e) {
        log.error("JudgeRemoteException: ", e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Result.error(ErrorCode.JUDGE_SERVICE_ERROR.getCode(), e.getMessage()));
    }

    /**
     * 处理其他运行时异常 - 500
     */
    @ExceptionHandler(RuntimeException.class)
    @ResponseBody
    public ResponseEntity<Result<Object>> handleRuntimeException(RuntimeException e) {
        log.error("未预期的运行时异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(ErrorCode.SYSTEM_ERROR.getCode(), "系统繁忙，请稍后重试"));
    }
}