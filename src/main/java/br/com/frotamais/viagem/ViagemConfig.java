package br.com.frotamais.viagem;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LimitesPlausibilidade.class)
public class ViagemConfig {
}
