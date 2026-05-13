package org.happyzion.api.common.error

import org.apache.catalina.connector.ClientAbortException
import org.happyzion.api.common.response.ApiErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class GlobalExceptionHandler {
    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(UnauthorizedException::class)
    fun handleUnauthorized(ex: UnauthorizedException): ResponseEntity<ApiErrorResponse> =
        errorResponse(
            status = HttpStatus.UNAUTHORIZED,
            code = "UNAUTHORIZED",
            message = ex.message ?: "인증 정보가 올바르지 않습니다.",
        )

    @ExceptionHandler(ForbiddenException::class)
    fun handleForbidden(ex: ForbiddenException): ResponseEntity<ApiErrorResponse> =
        errorResponse(
            status = HttpStatus.FORBIDDEN,
            code = "FORBIDDEN",
            message = ex.message ?: "접근 권한이 없습니다.",
        )

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(ex: NotFoundException): ResponseEntity<ApiErrorResponse> =
        errorResponse(
            status = HttpStatus.NOT_FOUND,
            code = "NOT_FOUND",
            message = ex.message ?: "요청한 리소스를 찾을 수 없습니다.",
        )

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResourceFound(ex: NoResourceFoundException): ResponseEntity<ApiErrorResponse> =
        errorResponse(
            status = HttpStatus.NOT_FOUND,
            code = "NOT_FOUND",
            message = "요청한 리소스를 찾을 수 없습니다.",
        )

    @ExceptionHandler(MethodArgumentNotValidException::class, IllegalArgumentException::class)
    fun handleBadRequest(ex: Exception): ResponseEntity<ApiErrorResponse> =
        errorResponse(
            status = HttpStatus.BAD_REQUEST,
            code = "INVALID_REQUEST",
            message = ex.message ?: "잘못된 요청입니다.",
        )

    @ExceptionHandler(ClientAbortException::class)
    fun handleClientAbort(ex: ClientAbortException): ResponseEntity<Void> {
        logger.debug("Client aborted response before it was fully written", ex)

        return ResponseEntity.status(499).build()
    }

    @ExceptionHandler(Exception::class)
    fun handleInternal(ex: Exception): ResponseEntity<ApiErrorResponse> {
        logger.error("Unhandled API exception", ex)

        return errorResponse(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            code = "INTERNAL_SERVER_ERROR",
            message = "서버 오류가 발생했습니다.",
        )
    }

    private fun errorResponse(
        status: HttpStatus,
        code: String,
        message: String,
    ): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(status)
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                ApiErrorResponse(
                    code = code,
                    message = message,
                )
            )
}
