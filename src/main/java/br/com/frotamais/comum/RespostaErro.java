package br.com.frotamais.comum;

import java.util.List;

import org.springframework.http.HttpStatus;

/**
 * Corpo único de toda resposta de erro da API:
 * {"status": 409, "erro": "Conflito de dados", "detalhes": ["..."]}.
 *
 * "erro" é a categoria (estável, boa para o front-end decidir o que fazer);
 * "detalhes" traz as mensagens para o usuário, sem stack trace nem dado interno.
 */
public record RespostaErro(int status, String erro, List<String> detalhes) {

    public static RespostaErro de(HttpStatus status, List<String> detalhes) {
        return new RespostaErro(status.value(), categoria(status), detalhes);
    }

    public static RespostaErro de(HttpStatus status, String detalhe) {
        return de(status, List.of(detalhe));
    }

    static String categoria(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "Dados inválidos";
            case UNAUTHORIZED -> "Não autenticado";
            case FORBIDDEN -> "Acesso negado";
            case NOT_FOUND -> "Não encontrado";
            case METHOD_NOT_ALLOWED -> "Método não permitido";
            case CONFLICT -> "Conflito de dados";
            case UNSUPPORTED_MEDIA_TYPE -> "Formato não suportado";
            case SERVICE_UNAVAILABLE -> "Serviço indisponível";
            case INTERNAL_SERVER_ERROR -> "Erro interno";
            default -> status.getReasonPhrase();
        };
    }

    /** JSON escrito à mão para os filtros de segurança, que rodam fora do MVC. Só textos fixos. */
    String json() {
        StringBuilder sb = new StringBuilder("{\"status\":").append(status)
                .append(",\"erro\":\"").append(escapar(erro)).append("\",\"detalhes\":[");
        for (int i = 0; i < detalhes.size(); i++) {
            sb.append(i > 0 ? "," : "").append('"').append(escapar(detalhes.get(i))).append('"');
        }
        return sb.append("]}").toString();
    }

    private static String escapar(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
