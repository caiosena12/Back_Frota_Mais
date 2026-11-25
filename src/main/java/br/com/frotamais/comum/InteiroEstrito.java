package br.com.frotamais.comum;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Inteiro que só aceita número inteiro no JSON. Sem ele, o Jackson converte
 * 1.5 em 1 e "152430" em 152430, e um valor digitado errado seria gravado
 * truncado. Recusado, vira 400 "Campo 'x' com valor inválido." (ApiExceptionHandler).
 *
 * Uso: {@code @JsonDeserialize(using = InteiroEstrito.class) Integer campo}.
 * Aplicado onde truncar muda o dado (quilometragem do abastecimento).
 */
public class InteiroEstrito extends ValueDeserializer<Integer> {

    @Override
    public Integer deserialize(JsonParser p, DeserializationContext ctxt) {
        if (p.currentToken() != JsonToken.VALUE_NUMBER_INT) {
            return (Integer) ctxt.handleUnexpectedToken(Integer.class, p);
        }
        return p.getIntValue();
    }
}
