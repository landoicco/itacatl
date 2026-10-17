package dev.licaza.itacatl.exception;

import dev.licaza.itacatl.dto.OrderResponseDTO;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(CheckoutFailedException.class)
  public ResponseEntity<OrderResponseDTO> handleCheckoutFailed(CheckoutFailedException ex) {
    // Get the object with the all data and message
    OrderResponseDTO failedOrder = ex.getOrderResponse();

    // Return 422 Unprocessable Entity with the same structure
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(failedOrder);
  }

  @ExceptionHandler(InsufficientStockException.class)
  public ResponseEntity<String> handleInsufficientStock(InsufficientStockException ex) {
    // Return 400 Bad Request with the insufficient stock explanation
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
  }

  // RFC 9457 Problem Details (application/problem+json). The "code" property is stable, so the
  // client translates it instead of showing the English detail to users.
  @ExceptionHandler(ResourceNotFoundException.class)
  public ProblemDetail handleResourceNotFound(ResourceNotFoundException ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    problem.setTitle("Resource not found");
    problem.setProperty("code", ex.getCode());
    return problem;
  }

  @ExceptionHandler(EntityNotFoundException.class)
  public ResponseEntity<String> handleNotFound(EntityNotFoundException ex) {
    // Return 404 Not Found
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
  }
}
