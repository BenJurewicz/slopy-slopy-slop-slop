package pl.cloudmigration.api;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class Errors {
    public record Error(String code,String message,List<String> details) {}
    @ExceptionHandler(AppException.class)
    ResponseEntity<Error> app(AppException e){return ResponseEntity.status(e.status).body(new Error(e.code,e.getMessage(),e.details));}
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Error> upload(){return ResponseEntity.status(413).body(new Error("UPLOAD_SIZE","Plik przekracza dopuszczalny rozmiar.",List.of()));}
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<Error> input(){return ResponseEntity.badRequest().body(new Error("REQUEST","Nieprawidłowy format danych żądania.",List.of()));}
    @ExceptionHandler({org.springframework.web.servlet.resource.NoResourceFoundException.class,org.springframework.web.servlet.NoHandlerFoundException.class})
    ResponseEntity<Error> missing(){return ResponseEntity.status(404).body(new Error("NOT_FOUND","Zasób nie istnieje.",List.of()));}
    @ExceptionHandler(org.springframework.web.bind.ServletRequestBindingException.class)
    ResponseEntity<Error> missingParameter(){return ResponseEntity.badRequest().body(new Error("REQUEST","Brak wymaganego parametru lub pliku.",List.of()));}
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    ResponseEntity<Error> method(){return ResponseEntity.status(405).body(new Error("METHOD","Metoda nie jest obsługiwana.",List.of()));}
    @ExceptionHandler(Exception.class)
    ResponseEntity<Error> internal(Exception e){org.slf4j.LoggerFactory.getLogger(Errors.class).error("Request failed: {}",e.getClass().getSimpleName());return ResponseEntity.internalServerError().body(new Error("INTERNAL","Nie można wykonać operacji.",List.of()));}
}
