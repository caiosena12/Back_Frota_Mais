# Frota+: handoff do release para o front-end

**Contratos: versão `1.0`, congelados em 25/11/2026.** A partir daqui,
qualquer mudança que afete o front-end (rota, método, campo, tipo, código de
resposta ou permissão) precisa ser comunicada e registrada neste documento
**antes** de ser publicada.

| Item | Valor |
|---|---|
| Base analisada | `main` em `fc644d3` + branch `release/producao` |
| Commit publicado | ver `docs/DEPLOYMENT_PRODUCTION.md`, seção "Registro de publicação" |
| Migrations | V1 a V11 (Flyway) |
| Política de cálculo | `rentabilidade-v1` (`CalculadoraRentabilidade.VERSAO_POLITICA`) |
| Referência normativa | [`docs/API.md`](API.md): regras de cada rota. Este documento é o resumo congelado |

## 1. O que mudou para o front-end neste release

Nenhuma rota, DTO, tipo, código de resposta ou permissão existente mudou. O
que o front-end já testou contra a API real continua valendo. Novidades:

| Mudança | Efeito no front-end |
|---|---|
| **CORS** restrito à origem do front-end (`CORS_ORIGENS_PERMITIDAS`) | Em produção o Angular chama a API diretamente pela URL pública, sem proxy. Outra origem recebe `403` no preflight |
| `GET /actuator/health` público | Só para a hospedagem. Resposta `{"status":"UP"}` (ou `DOWN`) |
| Perfil `prod` | Nenhum efeito no contrato |

O `403` de CORS (origem não autorizada) é gerado pelo filtro de CORS do Spring
**antes** da API: corpo em texto simples, não no formato `{"status","erro","detalhes"}`.
O navegador não entrega essa resposta ao JavaScript de qualquer forma.

## 2. Convenções

- **Base:** `https://<URL-DA-API>` (ainda a definir; ver o documento de deploy). Prefixo `/api`.
- **JSON UTF-8.** Datas `AAAA-MM-DD`; horas `HH:MM` ou `HH:MM:SS`; instantes
  (`...Em`) em ISO-8601 UTC; "hoje" segue `America/Sao_Paulo`.
- **Dinheiro e decimais:** número com até 2 casas.
- **`PUT` substitui o recurso inteiro.** Não há `PATCH`.
- **Nulo em cálculo** = não calculável com os dados atuais, nunca zero.
- **Campo desconhecido no corpo:** `400`.

## 3. Autenticação (JWT)

- `Authorization: Bearer <token>`, obtido no registro, no login ou na ativação do convite.
- HS256, emissor `frota-mais`, validade de `JWT_HORAS` horas (padrão 8, de 1 a 24).
- Claims: `sub` (id do usuário), `empresaId`, `perfil`. **O front-end não deve
  autorizar nada pela claim:** o perfil válido é o devolvido no corpo
  (`perfil`), e o back-end confere tudo no banco a cada requisição.
- Sem refresh token, sem logout no servidor, sem `/api/me` e sem recuperação de senha.
  Logout = descartar o token. Token expirado ou usuário desativado: `401` → voltar ao login.

## 4. Erros

Corpo único: `{"status": 409, "erro": "Conflito de dados", "detalhes": ["..."]}`.
`detalhes` sempre em português; validação no formato `campo: mensagem`
(ex.: `abastecimentos[0].litros: é obrigatório`).

| Código | Quando |
|---|---|
| `400` | Validação, JSON malformado, enum/data inválidos, regra de dados |
| `401` | Sem token, token inválido ou expirado, usuário inativo, login recusado |
| `403` | Perfil sem permissão |
| `404` | Inexistente **ou de outra empresa**; motorista sem vínculo com o caminhão |
| `405` / `415` | Método ou `Content-Type` não suportados |
| `409` | Conflito com o estado atual |
| `503` | E-mail não configurado no servidor (convites). Nada é gravado |
| `500` | Erro inesperado, com mensagem genérica |

## 5. Endpoints (41 + health)

Legenda: **P** = público, **A** = qualquer autenticado, **D** = só DONO,
**D/M** = DONO e MOTORISTA (o MOTORISTA só com caminhões vinculados; sem vínculo: `404`).

| Método e rota | Acesso | Sucesso |
|---|---|---|
| `POST /api/auth/registro` | P | `201` TokenResponse |
| `POST /api/auth/login` | P | `200` TokenResponse |
| `POST /api/auth/senha` | A | `204` |
| `POST /api/convites/ativacao` | P | `200` TokenResponse |
| `GET /api/caminhoes?incluirExcluidos=` | D/M | `200` lista |
| `GET /api/caminhoes/{id}` | D/M | `200` |
| `POST /api/caminhoes` | D | `201` |
| `PUT /api/caminhoes/{id}` | D | `200` |
| `DELETE /api/caminhoes/{id}` | D | `204` (exclusão lógica) |
| `GET /api/motoristas` | D | `200` lista |
| `GET /api/motoristas/{id}` | D | `200` |
| `POST /api/motoristas` | D | `201` (envia convite; sem SMTP: `503`) |
| `PUT /api/motoristas/{id}` | D | `200` |
| `PUT /api/motoristas/{id}/caminhoes` | D | `200` |
| `POST /api/motoristas/{id}/convite` | D | `200` (reenvio) |
| `DELETE /api/motoristas/{id}/convite` | D | `200` (cancelamento) |
| `GET /api/viagens?status=&caminhaoId=` | D/M | `200` lista de ViagemResumoResponse |
| `GET /api/viagens/{id}` | D/M | `200` ViagemResponse (`rentabilidade` nula para MOTORISTA) |
| `POST /api/viagens` | D/M | `201` |
| `PUT /api/viagens/{id}` | D/M (M só em andamento) | `200` |
| `POST /api/viagens/{id}/finalizar` | D/M | `200` |
| `POST /api/viagens/{id}/cancelar` | D | `200` |
| `POST /api/viagens/historicas` | D | `201` |
| `GET /api/viagens/{id}/alteracoes` | D | `200` |
| `GET /api/viagens/{id}/fotografias-anteriores` | D | `200` |
| `GET /api/viagens/indicadores` | D | `200` |
| `GET /api/viagens/indicadores/por-caminhao` | D | `200` |
| `POST /api/viagens/{viagemId}/abastecimentos` | D/M | `201` |
| `PUT /api/viagens/{viagemId}/abastecimentos/{id}` | D/M | `200` |
| `DELETE /api/viagens/{viagemId}/abastecimentos/{id}` | D/M | `204` (só em andamento) |
| `POST /api/viagens/{viagemId}/pedagios` | D/M | `201` |
| `PUT /api/viagens/{viagemId}/pedagios/{id}` | D/M | `200` |
| `DELETE /api/viagens/{viagemId}/pedagios/{id}` | D/M | `204` |
| `POST /api/viagens/{viagemId}/despesas-extras` | D/M | `201` |
| `PUT /api/viagens/{viagemId}/despesas-extras/{id}` | D/M | `200` |
| `DELETE /api/viagens/{viagemId}/despesas-extras/{id}` | D/M | `204` |
| `GET /api/manutencoes?caminhaoId=&de=&ate=&incluirExcluidas=` | D | `200` lista |
| `GET /api/manutencoes/{id}` | D | `200` |
| `POST /api/manutencoes` | D | `201` |
| `PUT /api/manutencoes/{id}` | D | `200` |
| `DELETE /api/manutencoes/{id}` | D | `204` (exclusão lógica) |
| `GET /actuator/health` (e `/liveness`, `/readiness`) | P | `200` `{"status":"UP"}` (só para a hospedagem) |

MOTORISTA nunca vê custo, lucro, margem, indicadores, auditoria nem
manutenções; não altera viagem encerrada, não cria histórica e não cancela.
Indicadores aceitam um filtro de período: `ano`; `ano`+`mes`; `semana`
(qualquer data da semana); ou `de`/`ate`. Também aceitam `caminhaoId` opcional.

## 6. DTOs

`*` = obrigatório (validação do back-end; algumas regras adicionais, como
`status` no `PUT` de caminhão, ficam nos serviços e estão em `docs/API.md`).

**Requisições**

| DTO | Campos |
|---|---|
| RegistroRequest | `nomeEmpresa*`, `nome*`, `email*`, `senha*` |
| LoginRequest | `email*`, `senha*` |
| TrocaSenhaRequest | `senhaAtual*`, `novaSenha*` |
| AtivacaoRequest | `token*`, `senha*` |
| CaminhaoRequest | `placa*`, `marca*`, `modelo*`, `ano*`, `eixos*` (1 a 20), `status` (obrigatório no `PUT`), `capacidadeTanqueLitros` |
| MotoristaRequest | `nome*`, `email*`, `caminhaoIds` |
| MotoristaAtualizacaoRequest | `nome*`, `ativo*` |
| VinculosRequest | `caminhaoIds*` (lista completa) |
| ViagemCriacaoRequest | `caminhaoId*`, `motoristaId`, `dataInicio*`, `origem`, `destino`, `valorFrete`, `kmInicialVazio`, `kmInicialCarregado`, `consumoMedioVazio`, `consumoMedioCarregado`, `abastecimentos` (até 200) |
| ViagemAtualizacaoRequest | `dataInicio*`, `origem`, `destino`, `valorFrete`, `kmInicialVazio`, `kmInicialCarregado`, `consumoMedioVazio`, `consumoMedioCarregado`, `dataFim`, `kmFinalCarregado`, `motoristaId` |
| FinalizacaoRequest | `dataFim*`, `kmFinalCarregado*`, `consumoMedioVazio`, `consumoMedioCarregado` |
| CancelamentoRequest | `motivo*`, `dataFim`, `kmFinalCarregado`, `consumoMedioVazio`, `consumoMedioCarregado` |
| ViagemHistoricaRequest | `caminhaoId*`, `motoristaId`, `dataInicio*`, `dataFim*`, `origem*`, `destino*`, `valorFrete*`, `kmInicialVazio`, `kmInicialCarregado*`, `kmFinalCarregado*`, `consumoMedioVazio`, `consumoMedioCarregado*`, `abastecimentos`, `pedagios`, `despesasExtras` (até 200 cada) |
| AbastecimentoRequest | `tipo*`, `data*`, `hora`, `litros*`, `valorTotal*` |
| PedagioRequest | `data*`, `valor*` |
| DespesaExtraRequest | `descricao*` (livre), `data*`, `valor*` |
| ManutencaoRequest | `caminhaoId*`, `data*`, `descricao*`, `valor*` |

**Respostas**

| DTO | Campos |
|---|---|
| TokenResponse | `token`, `nome`, `perfil` |
| CaminhaoResponse | `id`, `placa`, `marca`, `modelo`, `ano`, `eixos`, `status`, `capacidadeTanqueLitros`, `inativadoEm`, `excluidoEm` |
| MotoristaResponse | `id`, `nome`, `email`, `situacao`, `caminhaoIds`, `conviteExpiraEm` |
| ViagemResumoResponse | `id`, `caminhaoId`, `placa`, `motoristaId`, `motoristaNome`, `status`, `origem`, `destino`, `dataInicio`, `dataFim`, `valorFrete`, `kmVazio`, `kmCarregado`, `custoOperacionalTotal`, `lucro`, `margemPercentual`, `calculoCompleto` |
| ViagemResponse | `id`, `caminhaoId`, `placa`, `motoristaId`, `motoristaNome`, `status`, `origem`, `destino`, `dataInicio`, `dataFim`, `valorFrete`, `kmInicialVazio`, `kmFinalVazio`, `kmInicialCarregado`, `kmFinalCarregado`, `consumoMedioVazio`, `consumoMedioCarregado`, `criadaEm`, `atualizadaEm`, `finalizadaEm`, `cadastroRetroativoEm`, `canceladaEm`, `motivoCancelamento`, `abastecimentosDiesel[]`, `abastecimentosArla[]`, `pedagios[]`, `despesasExtras[]`, `rentabilidade` |
| RentabilidadeResponse | `receita`, `kmVazio`, `kmCarregado`, `litrosEstimadosVazio`, `litrosEstimadosCarregado`, `precoMedioDiesel`, `origemPrecoDiesel`, `viagemReferenciaPrecoDieselId`, `referenciaPrecoConsolidadaEm`, `custoDieselVazio`, `custoDieselCarregado`, `custoArla`, `pedagios`, `despesasExtras`, `custoOperacionalTotal`, `lucro`, `margemPercentual`, `calculoCompleto`, `pendencias[]`, `politicaCalculo`, `resultadoConsolidadoEm` |
| AbastecimentoResponse | `id`, `tipo`, `data`, `hora`, `litros`, `valorTotal` |
| PedagioResponse | `id`, `data`, `valor` |
| DespesaExtraResponse | `id`, `descricao`, `data`, `valor` |
| AlteracaoViagemResponse | `id`, `alteradaEm`, `usuarioId`, `usuarioNome`, `perfil`, `entidade`, `registroId`, `campo`, `valorAnterior`, `valorNovo` |
| FotografiaAnteriorResponse | `id`, `substituidaEm`, `substituidaPorId`, `substituidaPorNome`, `perfil`, `resultado`, `resultadoNovo` |
| IndicadoresResponse | `de`, `ate`, `caminhaoId`, `criterioPeriodo`, `viagensFinalizadas`, `viagensConsideradas`, `viagensIncompletas`, `viagensComCalculoIncompleto[]`, `valorFreteIncompletasNaoIncluido`, `receitaTotal`, `custoDieselVazio`, `custoDieselCarregado`, `custoArla`, `pedagios`, `despesasExtras`, `custoOperacionalTotal`, `lucroTotal`, `margemPercentual`, `kmVazio`, `kmCarregado`, `custoPorKm`, `lucroPorKm`, `percentualKmVazio`, `consumoMedioKmPorLitro`, `dieselPago`, `dieselEstimado`, `diferencaDieselPagoEstimado`, `quantidadeManutencoes`, `custoManutencoes`, `resultadoAposManutencoes`, `viagensCanceladas`, `canceladasComCustoIncompleto[]`, `receitaCanceladas`, `custoCanceladas`, `resultadoCanceladas`, `resultadoFinanceiroPeriodo` |
| IndicadoresCaminhaoResponse | `caminhaoId`, `placa`, `indicadores` |
| ManutencaoResponse | `id`, `caminhaoId`, `placa`, `data`, `descricao`, `valor`, `criadaEm`, `atualizadaEm`, `excluidaEm` |

**Enums:** `StatusCaminhao` ATIVO, INATIVO, EXCLUIDO · `StatusViagem`
EM_ANDAMENTO, FINALIZADA, CANCELADA · `TipoCombustivel` DIESEL, ARLA ·
`SituacaoMotorista` CONVITE_PENDENTE, ATIVO, INATIVO · `Perfil` DONO,
MOTORISTA · `OrigemPrecoDiesel` ABASTECIMENTOS_DA_VIAGEM,
ULTIMO_PRECO_DO_CAMINHAO, SEM_REFERENCIA · `entidade` (auditoria) VIAGEM,
ABASTECIMENTO, PEDAGIO, DESPESA_EXTRA.

## 7. Configuração que o front-end precisa

| O quê | Valor |
|---|---|
| URL base da API em produção | `https://<URL-DA-API>` (**pendente**: depende da hospedagem) |
| Origem do front-end | Informar ao back-end a origem https **exata** (ex.: `https://app.exemplo.com`, sem barra final), que vai em `CORS_ORIGENS_PERMITIDAS` |
| Tela de ativação do convite | Informar a URL (ex.: `https://app.exemplo.com/ativar`). O back-end envia `?token=...` nela, e a tela faz `POST /api/convites/ativacao` |
| Cabeçalhos | `Authorization: Bearer ...` e `Content-Type: application/json`. Sem cookies (`withCredentials` desligado) |
| Proxy do Angular | Só em desenvolvimento. Em produção, URL absoluta da API |

## 8. Dependências externas

| Dependência | Situação |
|---|---|
| PostgreSQL de produção (16) | **Pendente**: banco exclusivo a provisionar |
| SMTP | **Pendente**: sem ele, cadastro e reenvio de convite de motorista respondem `503` |
| Hospedagem com HTTPS | **Pendente**: provedor não definido |
