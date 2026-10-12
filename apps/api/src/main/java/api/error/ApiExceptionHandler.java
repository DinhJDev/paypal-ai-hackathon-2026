package api.error;

import java.net.URI;
import java.util.Comparator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	public record ValidationError(String field, String message) { }

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		var problem = ProblemDetail.forStatusAndDetail(status, "Request validation failed.");
		var errors = exception.getBindingResult().getAllErrors().stream()
				.map(error -> new ValidationError(error instanceof FieldError fieldError
						? fieldError.getField() : error.getObjectName(),
						error.getDefaultMessage() == null ? "Invalid value." : error.getDefaultMessage()))
				.sorted(Comparator.comparing(ValidationError::field).thenComparing(ValidationError::message))
				.toList();
		problem.setProperty("errors", errors);
		return handleExceptionInternal(exception, problem, headers, status, request);
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		var problem = ProblemDetail.forStatusAndDetail(status, "Request body is missing or contains invalid JSON.");
		return handleExceptionInternal(exception, problem, headers, status, request);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<Object> handleUnexpectedException(Exception exception, WebRequest request) {
		log.error("Unexpected request failure", exception);
		var problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
		return handleExceptionInternal(exception, problem, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
	}

	@Override
	protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException exception,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		var problem = ProblemDetail.forStatusAndDetail(status, "Request parameter is invalid.");
		return handleExceptionInternal(exception, problem, headers, status, request);
	}

	@Override
	protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		if (body instanceof ProblemDetail problem) {
			if (problem.getType() == null) {
				problem.setType(URI.create("about:blank"));
			}
			if (problem.getInstance() == null && request instanceof ServletWebRequest servletRequest) {
				problem.setInstance(URI.create(servletRequest.getRequest().getRequestURI()));
			}
		}
		return super.createResponseEntity(body, headers, status, request);
	}
}
