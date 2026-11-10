package org.cvp.assistant.controller;

import org.cvp.assistant.application.chat.ForbiddenException;
import org.cvp.assistant.application.chat.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(UnauthorizedException.class)
    ProblemDetail unauthorized(UnauthorizedException exception) { return problem(HttpStatus.UNAUTHORIZED, exception.getMessage()); }
    @ExceptionHandler(ForbiddenException.class)
    ProblemDetail forbidden(ForbiddenException exception) { return problem(HttpStatus.FORBIDDEN, exception.getMessage()); }
    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException exception) { return problem(HttpStatus.NOT_FOUND, exception.getMessage()); }
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    ProblemDetail invalid(Exception exception) { return problem(HttpStatus.BAD_REQUEST, "Invalid chat request"); }
    private ProblemDetail problem(HttpStatus status, String detail) { return ProblemDetail.forStatusAndDetail(status, detail); }
}
