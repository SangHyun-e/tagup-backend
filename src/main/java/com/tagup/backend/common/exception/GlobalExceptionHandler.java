package com.tagup.backend.common.exception;

import com.tagup.backend.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException e) {
        log.warn("CustomException: {}", e.getMessage());
        return ResponseEntity
                .status(e.getErrorCode().getStatus())
                .body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(ApiResponse.fail(message));
    }

    /**
     * 존재하지 않는 경로 요청.
     *
     * <p>아래 일반 Exception 핸들러가 잡으면 404가 500으로 나가고 ERROR 로그까지 남는다.
     * 서버가 외부에 공개되면(터널/프로덕션) 스캐너가 임의 경로를 찔러대므로
     * 로그가 실제 장애로 오인될 만큼 오염된다. 404로 정확히 응답하고 debug로만 남긴다.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException e) {
        log.debug("존재하지 않는 경로: {}", e.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail("요청한 경로를 찾을 수 없습니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unexpected exception", e);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.fail(ErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
    }
}
