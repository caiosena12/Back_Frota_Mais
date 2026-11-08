package br.com.frotamais.auth;

import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import br.com.frotamais.comum.RegraNegocioException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> tratarValidacao(
            MethodArgumentNotValidException ex) {
        List<String> detalhes = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .distinct()
                .toList();

        return ResponseEntity.badRequest().body(Map.of(
                "status", 400,
                "erro", "Dados inválidos",
                "detalhes", detalhes));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> tratarConflito(
            DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "status", 409,
                "erro", "Conflito de dados",
                "mensagem", "Um dado viola uma regra de integridade ou já existe."));
    }

    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<Map<String, Object>> tratarRegraNegocio(
            RegraNegocioException ex) {
        HttpStatus status = ex.getStatus();
        String erro = switch (status) {
            case BAD_REQUEST -> "Dados inválidos";
            case NOT_FOUND -> "Não encontrado";
            case CONFLICT -> "Conflito de dados";
            default -> status.getReasonPhrase();
        };
        return ResponseEntity.status(status).body(Map.of(
                "status", status.value(),
                "erro", erro,
                "detalhes", List.of(ex.getMessage())));
    }
}
