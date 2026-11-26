package br.com.frotamais.comum;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

/**
 * Leitura estrita dos corpos JSON (contratos 1.2), num lugar só para todos os DTOs.
 *
 * <ul>
 * <li><b>Campo desconhecido → 400</b> ("Campo desconhecido: 'x'."). O Jackson 3
 * passou a ignorá-los por padrão; um nome de campo errado no cliente seria
 * descartado em silêncio, e o dado se perderia. Sem exceções de compatibilidade:
 * o {@code hora} antigo do abastecimento também é recusado, porque o cliente
 * antigo já falharia pela falta da quilometragem obrigatória.</li>
 * <li><b>Campos inteiros</b> (Integer, Long, Short, BigInteger, inclusive em
 * listas como {@code caminhaoIds}): só aceitam número inteiro do JSON. Por padrão
 * o Jackson truncaria {@code 1.5} para {@code 1} e converteria {@code "152430"}
 * e {@code true} antes de a validação ver o valor. Agora esses casos respondem
 * 400 ("Campo 'x' com valor inválido.") e nada é gravado.</li>
 * </ul>
 *
 * Valores monetários e litros ({@code BigDecimal}) não mudam: aceitam número ou
 * texto numérico, como o front-end envia.
 */
@Configuration
public class ConfigJackson {

    @Bean
    JsonMapperBuilderCustomizer leituraEstrita() {
        return builder -> builder
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .withCoercionConfig(LogicalType.Integer, c -> c
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail));
    }
}
