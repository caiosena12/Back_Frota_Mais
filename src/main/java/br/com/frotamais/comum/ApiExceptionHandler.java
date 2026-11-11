package br.com.frotamais.comum;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import tools.jackson.core.JacksonException;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * Tratamento único de erros da API: toda resposta de erro sai no formato
 * RespostaErro {"status", "erro", "detalhes"}, sem stack trace nem detalhe
 * interno. Os 401/403 decididos pelos filtros de segurança usam o mesmo
 * formato (RespostasSeguranca).
 *
 * Logs: erros do cliente (4xx) não são registrados, exceto conflitos de
 * integridade, que registram só o nome da restrição. Erros inesperados (500)
 * são registrados com o stack trace, no servidor.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<RespostaErro> regraNegocio(RegraNegocioException ex) {
        return responder(ex.getStatus(), List.of(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RespostaErro> validacao(MethodArgumentNotValidException ex) {
        List<String> detalhes = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .distinct()
                .toList();
        if (detalhes.isEmpty()) {
            detalhes = ex.getBindingResult().getAllErrors().stream()
                    .map(e -> String.valueOf(e.getDefaultMessage())).distinct().toList();
        }
        return responder(HttpStatus.BAD_REQUEST, detalhes);
    }

    /** Validação de parâmetros e de elementos de listas (@Valid em List<...>). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<RespostaErro> validacaoMetodo(HandlerMethodValidationException ex) {
        List<String> detalhes = ex.getAllErrors().stream()
                .map(e -> String.valueOf(e.getDefaultMessage())).distinct().toList();
        return responder(HttpStatus.BAD_REQUEST, detalhes);
    }

    /** JSON malformado, enum inexistente, data ou número inválidos, campo desconhecido. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<RespostaErro> corpoIlegivel(HttpMessageNotReadableException ex) {
        return responder(HttpStatus.BAD_REQUEST, List.of(descreverCorpoIlegivel(ex)));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<RespostaErro> parametroInvalido(MethodArgumentTypeMismatchException ex) {
        String detalhe = "Parâmetro '" + ex.getName() + "' com valor inválido.";
        Class<?> tipo = ex.getRequiredType();
        if (tipo != null && tipo.isEnum()) {
            detalhe += " Valores aceitos: " + valoresEnum(tipo) + ".";
        } else if (tipo != null && tipo.getSimpleName().equals("LocalDate")) {
            detalhe += " Use o formato AAAA-MM-DD.";
        }
        return responder(HttpStatus.BAD_REQUEST, List.of(detalhe));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<RespostaErro> parametroAusente(MissingServletRequestParameterException ex) {
        return responder(HttpStatus.BAD_REQUEST, List.of("Parâmetro obrigatório ausente: " + ex.getParameterName() + "."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<RespostaErro> integridade(DataIntegrityViolationException ex) {
        String restricao = ex.getCause() instanceof ConstraintViolationException c ? c.getConstraintName() : null;
        log.warn("Violação de integridade no banco (restrição: {}).", restricao != null ? restricao : "não identificada");
        return responder(HttpStatus.CONFLICT,
                List.of("A operação viola uma regra de integridade dos dados ou o registro já existe."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<RespostaErro> acessoNegado(AccessDeniedException ex) {
        return responder(HttpStatus.FORBIDDEN, List.of("Seu perfil não tem permissão para esta operação."));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<RespostaErro> naoAutenticado(AuthenticationException ex) {
        return responder(HttpStatus.UNAUTHORIZED, List.of("Autenticação necessária."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<RespostaErro> metodo(HttpRequestMethodNotSupportedException ex) {
        return responder(HttpStatus.METHOD_NOT_ALLOWED, List.of("Método " + ex.getMethod() + " não é aceito nesta rota."));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<RespostaErro> formato(HttpMediaTypeNotSupportedException ex) {
        return responder(HttpStatus.UNSUPPORTED_MEDIA_TYPE, List.of("Envie o corpo como application/json."));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<RespostaErro> rotaInexistente(NoResourceFoundException ex) {
        return responder(HttpStatus.NOT_FOUND, List.of("Rota não encontrada."));
    }

    /** Legado: código novo usa RegraNegocioException. Mantido para não perder a mensagem. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<RespostaErro> statusExplicito(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String motivo = ex.getReason() != null ? ex.getReason() : RespostaErro.categoria(status);
        return responder(status, List.of(motivo));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RespostaErro> inesperado(Exception ex) {
        log.error("Erro inesperado ao processar a requisição.", ex);
        return responder(HttpStatus.INTERNAL_SERVER_ERROR,
                List.of("Erro inesperado. Tente novamente; se persistir, contate o suporte."));
    }

    private static ResponseEntity<RespostaErro> responder(HttpStatusCode codigo, List<String> detalhes) {
        HttpStatus status = HttpStatus.valueOf(codigo.value());
        return ResponseEntity.status(status).body(RespostaErro.de(status, detalhes));
    }

    private static String descreverCorpoIlegivel(HttpMessageNotReadableException ex) {
        // A causa mais profunda pode ser, por exemplo, um DateTimeParseException sem o
        // nome do campo; a exceção do Jackson, mais acima na cadeia, guarda o caminho.
        Throwable causa = ex.getMostSpecificCause();
        for (Throwable t = ex.getCause(); t != null; t = t.getCause()) {
            if (t instanceof JacksonException) {
                causa = t;
                break;
            }
        }
        if (causa instanceof UnrecognizedPropertyException u) {
            return "Campo desconhecido: '" + u.getPropertyName() + "'.";
        }
        if (causa instanceof MismatchedInputException m) {
            String campo = campo(m);
            Class<?> tipo = m.getTargetType();
            String prefixo = campo != null ? "Campo '" + campo + "' com valor inválido." : "Valor inválido no corpo da requisição.";
            if (tipo != null && tipo.isEnum()) {
                return prefixo + " Valores aceitos: " + valoresEnum(tipo) + ".";
            }
            if (tipo != null && tipo.getSimpleName().equals("LocalDate")) {
                return prefixo + " Use o formato AAAA-MM-DD.";
            }
            if (tipo != null && tipo.getSimpleName().equals("LocalTime")) {
                return prefixo + " Use o formato HH:MM.";
            }
            return prefixo;
        }
        if (causa instanceof StreamReadException) {
            return "JSON malformado.";
        }
        if (causa instanceof JacksonException j && campo(j) != null) {
            return "Campo '" + campo(j) + "' com valor inválido.";
        }
        return "Corpo da requisição ausente ou ilegível.";
    }

    private static String campo(JacksonException ex) {
        String caminho = ex.getPath().stream()
                .map(r -> r.getPropertyName() != null ? r.getPropertyName() : "[" + r.getIndex() + "]")
                .collect(Collectors.joining("."))
                .replace(".[", "[");
        return caminho.isBlank() ? null : caminho;
    }

    private static String valoresEnum(Class<?> tipo) {
        return Arrays.stream(tipo.getEnumConstants()).map(Object::toString).collect(Collectors.joining(", "));
    }
}
