package cn.ksuser.api.exception;

import cn.ksuser.api.result.ApiErrorResponses;
import cn.ksuser.api.result.ApiResult;
import cn.ksuser.api.result.ApiResultCode;
import cn.ksuser.api.result.BusinessArgumentException;
import cn.ksuser.api.result.BusinessStateException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private ResponseEntity<?> response(HttpServletRequest request, int status, ApiResultCode code) {
        return response(request, status, ApiResult.of(code));
    }
    private ResponseEntity<?> response(HttpServletRequest request, int status, ApiResult result) {
        return ResponseEntity.status(status).body(ApiErrorResponses.body(request, status, result));
    }
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<?> method(HttpServletRequest request) { return response(request, 405, ApiResultCode.METHOD_NOT_ALLOWED); }
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<?> media(HttpServletRequest request) { return response(request, 415, ApiResultCode.UNSUPPORTED_MEDIA_TYPE); }
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<?> notFound(HttpServletRequest request) { return response(request, 404, ApiResultCode.NOT_FOUND); }
    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
        ServletRequestBindingException.class, MethodArgumentNotValidException.class,
        MethodArgumentTypeMismatchException.class, jakarta.validation.ConstraintViolationException.class,
        org.springframework.web.multipart.support.MissingServletRequestPartException.class,
        org.springframework.web.multipart.MultipartException.class,
        org.springframework.web.method.annotation.HandlerMethodValidationException.class})
    public ResponseEntity<?> invalid(HttpServletRequest request) { return response(request, 400, ApiResultCode.BAD_REQUEST); }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> uploadSize(HttpServletRequest request) { return response(request, 413, ApiResultCode.REQUEST_UPLOAD_TOO_LARGE); }
    @ExceptionHandler(BusinessArgumentException.class)
    public ResponseEntity<?> argument(BusinessArgumentException ex, HttpServletRequest request) { return response(request, 400, ex.getResult()); }
    @ExceptionHandler(BusinessStateException.class)
    public ResponseEntity<?> state(BusinessStateException ex, HttpServletRequest request) {
        logger.error("Business state unavailable: {}", ex.getResult().errorCode(), ex);
        return response(request, 503, ex.getResult());
    }
    @ExceptionHandler(Oauth2Exception.class)
    public ResponseEntity<?> oauth(Oauth2Exception ex, HttpServletRequest request) { return response(request, ex.getStatus().value(), ex.getResult()); }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> unexpected(Exception ex, HttpServletRequest request) {
        logger.error("Unhandled API exception", ex);
        return response(request, 500, ApiResultCode.SERVER_ERROR);
    }
}
