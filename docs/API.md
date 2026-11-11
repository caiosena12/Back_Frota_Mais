# API do Frota+: endpoints e códigos de resposta

Referência dos contratos HTTP do back-end. O código é a fonte de verdade; os
testes em `src/test/java` comprovam cada regra com tokens reais.

## Convenções

- **Base:** `http://localhost:8080` no desenvolvimento. JSON em UTF-8.
- **Autenticação:** `Authorization: Bearer <token>`, obtido no registro, no
  login ou na ativação do convite. A cada requisição o usuário é recarregado do
  banco: precisa estar ativo, e a empresa do token precisa ser a dele. O perfil
  (DONO ou MOTORISTA) vem do banco, não do token.
- **Empresa:** nunca é enviada pelo cliente; vem do usuário autenticado. Recurso
  de outra empresa responde `404`, como se não existisse.
- **Datas:** `AAAA-MM-DD`; horas `HH:MM` ou `HH:MM:SS`; instantes em ISO-8601
  UTC. "Hoje" e "agora" seguem o fuso `America/Sao_Paulo`.
- **Dinheiro:** número decimal com até 2 casas (`"1500.00"` ou `1500.00`).
- **PUT é substituição integral:** envie todos os campos do recurso, inclusive
  os que não mudaram. Campo opcional ausente vira nulo. Não há `PATCH`.
- **Valores nulos** em cálculos significam "não calculável com os dados
  atuais", nunca zero.

### Formato de erro

Todas as respostas de erro, inclusive `401` e `403`, têm o mesmo corpo:

```json
{"status": 409, "erro": "Conflito de dados", "detalhes": ["O caminhão já está excluído."]}
```

| Código | Quando |
|---|---|
| `400` | Validação, JSON malformado, enum ou data inválidos, parâmetro com tipo errado, regra de dados (ex.: data fora do período) |
| `401` | Sem token, token inválido ou expirado, usuário inativo, login recusado |
| `403` | Perfil sem permissão para a operação |
| `404` | Recurso inexistente ou de outra empresa; rota inexistente |
| `405` / `415` | Método ou `Content-Type` não suportados |
| `409` | Conflito com o estado atual (duplicidade, viagem encerrada, sobreposição de períodos, caminhão excluído...) |
| `503` | Envio de e-mail indisponível (SMTP não configurado); nada é gravado |
| `500` | Erro inesperado: mensagem genérica; a causa fica só no log do servidor |

## Autenticação e conta

| Método e rota | Acesso | Corpo | Sucesso |
|---|---|---|---|
| `POST /api/auth/registro` | público | `nomeEmpresa`, `nome`, `email`, `senha` | `201` `{token, nome, perfil}`: cria a empresa e o DONO |
| `POST /api/auth/login` | público | `email`, `senha` | `200` `{token, nome, perfil}` |
| `POST /api/auth/senha` | autenticado | `senhaAtual`, `novaSenha` | `204` |
| `POST /api/convites/ativacao` | público | `token` (do e-mail), `senha` | `200` `{token, nome, perfil}`: ativa o motorista |

- Senha: de 8 caracteres a 72 bytes, com pelo menos uma letra e um número.
- E-mail único em todo o sistema (`409` no registro ou cadastro duplicado).
- Convite: token aleatório guardado só como hash, uso único, validade de
  24 h (`app.convites.validade-horas`); reenviar invalida o anterior, e o DONO
  pode cancelar o pendente. Token inválido, usado, expirado, invalidado ou
  cancelado: `400`, sem dizer qual dos casos.

## Caminhões: `/api/caminhoes`

| Método e rota | DONO | MOTORISTA |
|---|---|---|
| `GET /api/caminhoes?incluirExcluidos=false` | todos da empresa | só os vinculados a ele, não excluídos |
| `GET /api/caminhoes/{id}` | sim | só vinculado (senão `404`) |
| `POST /api/caminhoes` | `201` | `403` |
| `PUT /api/caminhoes/{id}` | `200` | `403` |
| `DELETE /api/caminhoes/{id}` | `204`, exclusão lógica | `403` |

Corpo: `placa`, `marca`, `modelo`, `ano`, `eixos` (1 a 20), `status`
(`ATIVO` ou `INATIVO`; obrigatório no `PUT`), `capacidadeTanqueLitros`
(opcional).

- Placa normalizada (maiúsculas, sem espaço ou hífen) e única entre os
  caminhões não excluídos da empresa (`409`).
- Inativar ou excluir com viagem em andamento: `409`. Caminhão excluído não é
  alterado nem excluído de novo (`409`) e não entra em viagens novas.

## Motoristas: `/api/motoristas` (só DONO)

| Método e rota | Corpo | Sucesso |
|---|---|---|
| `GET /api/motoristas` | | lista |
| `GET /api/motoristas/{id}` | | motorista |
| `POST /api/motoristas` | `nome`, `email`, `caminhaoIds` | `201`, envia o convite |
| `PUT /api/motoristas/{id}` | `nome`, `ativo` | `200` |
| `PUT /api/motoristas/{id}/caminhoes` | `caminhaoIds` (lista completa; vazia remove todos) | `200` |
| `POST /api/motoristas/{id}/convite` | | `200`, reenvia e invalida o anterior; conta já ativada: `409` |
| `DELETE /api/motoristas/{id}/convite` | | `200`, cancela o convite pendente (o link deixa de valer na hora; o registro fica); conta já ativada ou sem convite pendente: `409` |

Resposta: `id`, `nome`, `email`, `situacao` (`CONVITE_PENDENTE`, `ATIVO`, `INATIVO`),
`caminhaoIds`, `conviteExpiraEm` (nulo sem convite válido, por exemplo depois
do cancelamento: a conta continua `CONVITE_PENDENTE` até um novo convite ser
enviado e usado). Cancelar não envia e-mail.

Motorista de outra empresa: `404`. MOTORISTA nessas rotas: `403`.

## Viagens: `/api/viagens`

| Método e rota | DONO | MOTORISTA |
|---|---|---|
| `GET /api/viagens?status=&caminhaoId=` | todas | só de caminhões vinculados; sem custo, lucro e margem |
| `GET /api/viagens/{id}` | sim | vinculado; `rentabilidade` nula |
| `POST /api/viagens` | `201` | `201`, em seu próprio nome, caminhão vinculado |
| `PUT /api/viagens/{id}` | sim, inclusive encerrada (correção auditada) | só em andamento (`403` depois) |
| `POST /api/viagens/{id}/finalizar` | sim | sim |
| `POST /api/viagens/{id}/cancelar` | sim | `403` |
| `POST /api/viagens/historicas` | `201` | `403` |
| `GET /api/viagens/{id}/alteracoes` | sim | `403` |
| `GET /api/viagens/{id}/fotografias-anteriores` | sim | `403` |
| `GET /api/viagens/indicadores` | sim | `403` |
| `GET /api/viagens/indicadores/por-caminhao` | sim | `403` |

Viagem de caminhão não vinculado ao MOTORISTA responde `404`.

**Abertura** (`POST /api/viagens`): `caminhaoId`, `dataInicio`, `motoristaId`
(opcional; DONO pode indicar um motorista ativo e vinculado ao caminhão, a si
mesmo ou ninguém), `origem`, `destino`, `valorFrete` (opcional na abertura),
`kmInicialVazio` e/ou `kmInicialCarregado`, consumos médios e
`abastecimentos` (lista opcional, até 200 itens, no mesmo formato do
lançamento). A viagem e os abastecimentos são gravados na mesma transação,
depois de todos os itens validados; um item inválido recusa tudo (`400`) e
nada é gravado. É o caminho para registrar o diesel e o Arla abastecidos
antes de sair (ver as regras de data em **Lançamentos**). O
`POST .../abastecimentos` continua disponível depois da abertura.

**Alteração** (`PUT`): mesmos campos, sem `caminhaoId`; `dataFim` e
`kmFinalCarregado` só em viagem encerrada.

**Finalização:** `dataFim`, `kmFinalCarregado`, consumos (se ausentes, valem os
gravados). Exige valor do frete, origem e destino.

**Cancelamento:** `motivo` (obrigatório, texto livre), `dataFim` (padrão: hoje),
`kmFinalCarregado` e consumos opcionais. Só viagem em andamento; finalizada
não é cancelada (`409`). A viagem fica `CANCELADA`, com período fechado e
resultado consolidado; não é apagada. A `dataFim` é a **data do cancelamento**:
define o mês em que os valores entram no financeiro (ver **Indicadores**).
Receber valor ou ter despesas não muda o status.

**Histórica** (só DONO): viagem que já aconteceu, criada finalizada numa só
operação, com listas opcionais `abastecimentos`, `pedagios` e
`despesasExtras`. Caminhão `INATIVO`: só se a viagem terminou antes da
inativação. Caminhão `EXCLUIDO`: só se terminou antes da exclusão (ou da
inativação, se ele já estava inativo antes); o caminhão continua excluído.
Fora disso: `409`.

**Viagem finalizada:** não aceita lançamento novo nem exclusão (`409`). O DONO
corrige pelo `PUT` (viagem e lançamentos); o MOTORISTA recebe `403`.

**Viagem cancelada:** só o DONO registra ou corrige valores depois do
cancelamento: o valor recebido vai no `valorFrete` (pelo `PUT`), e as despesas,
pedágios e abastecimentos vão pelos lançamentos normais (`POST` e `PUT`), com
data dentro do período da viagem (`400` fora dele). Exclusão de lançamento:
`409`. MOTORISTA: `403` em qualquer alteração.

**Auditoria:** toda correção de viagem encerrada, e toda inclusão em viagem
cancelada, regrava a fotografia financeira só daquela viagem e fica em:
- `GET /alteracoes`: `id`, `alteradaEm`, `usuarioId`, `usuarioNome`, `perfil`
  (de quem alterou, no momento da alteração), `entidade` (`VIAGEM`,
  `ABASTECIMENTO`, `PEDAGIO`, `DESPESA_EXTRA`), `registroId`, `campo`,
  `valorAnterior` (nulo numa inclusão) e `valorNovo`;
- `GET /fotografias-anteriores`: `id`, `substituidaEm`, `substituidaPorId`,
  `substituidaPorNome`, `perfil`, `resultado` (a fotografia substituída) e
  `resultadoNovo` (a que a substituiu; nulo em registros anteriores à V11).

Regras de período: duas viagens do mesmo caminhão não se sobrepõem (datas
inclusivas; só o dia em que uma termina e a outra começa pode ser
compartilhado) e não começam no mesmo dia (`409`). Datas no futuro: `400`.

### Lançamentos: `/api/viagens/{viagemId}/...`

| Recurso | Corpo | Regras |
|---|---|---|
| `abastecimentos` | `tipo` (`DIESEL` ou `ARLA`), `data`, `hora` (opcional), `litros`, `valorTotal` | Diesel com preço plausível e litros até a capacidade do tanque, se cadastrada |
| `pedagios` | `data`, `valor` | |
| `despesas-extras` | `descricao` (livre), `data`, `valor` | Nenhuma palavra é bloqueada |

`POST` cria (`201`), `PUT /{id}` corrige (`200`), `DELETE /{id}` exclui
(`204`, só com a viagem em andamento).

- Pedágios e despesas ficam dentro do período da viagem.
- Abastecimento também pode ser **anterior ao início**, se foi feito entre
  viagens: a partir do dia em que a viagem anterior do caminhão terminou. Ele
  pertence à viagem seguinte, com data e hora reais. Sem viagem anterior
  (primeiro frete do caminhão), qualquer data anterior ao início é aceita, sem
  limite de dias. A data nunca é ajustada para coincidir com o início.
- A data final de uma viagem não pode passar dos abastecimentos antecipados
  da seguinte (`409`).

### Indicadores

`GET /api/viagens/indicadores` com um filtro de período (`ano`; `ano`+`mes`;
`semana` = qualquer data da semana, segunda a domingo; ou `de`/`ate`) e
`caminhaoId` opcional. Fretes entram pela data final; canceladas, pela data do
cancelamento (a `dataFim` delas); manutenções, pela data.

**Fretes concluídos** (finalizados com cálculo completo; incompletos listados à
parte em `viagensComCalculoIncompleto`, com `valorFreteIncompletasNaoIncluido`
só informativo): `viagensFinalizadas`, `viagensConsideradas`, `receitaTotal`,
custos por tipo, `custoOperacionalTotal`, `lucroTotal`, `margemPercentual`
(ponderada: lucro ÷ receita × 100), `kmVazio`, `kmCarregado`, `custoPorKm`,
`lucroPorKm`, `percentualKmVazio`, `consumoMedioKmPorLitro`, `dieselPago`,
`dieselEstimado` e `diferencaDieselPagoEstimado` (informativos: o pago nunca é
somado ao custo). Canceladas não entram em nenhum deles.

**Manutenções:** `quantidadeManutencoes`, `custoManutencoes` e
`resultadoAposManutencoes` (= `lucroTotal` − `custoManutencoes`).

**Canceladas** (bloco separado): `viagensCanceladas`, `receitaCanceladas`
(valores recebidos), `custoCanceladas` (custos conhecidos: Arla, pedágios,
despesas e o diesel quando calculável), `resultadoCanceladas` e
`canceladasComCustoIncompleto` (ids das que não tiveram o diesel calculado; esse
diesel fica fora da soma, nunca como zero).

**`resultadoFinanceiroPeriodo`** = `lucroTotal` + `resultadoCanceladas` −
`custoManutencoes`: o financeiro geral do período, sem dupla contagem.

Exemplo: frete concluído com receita 3000 e custo 1200 (lucro 1800, margem
60%); cancelada com 500 recebidos e 300 de custo (resultado 200); manutenção
de 150. Resultado: `resultadoAposManutencoes` 1650 e
`resultadoFinanceiroPeriodo` 1850; a margem continua 60%.

`GET /api/viagens/indicadores/por-caminhao`: os mesmos indicadores para cada
caminhão com frete finalizado, viagem cancelada ou manutenção no período.

## Manutenções: `/api/manutencoes` (só DONO)

| Método e rota | Sucesso |
|---|---|
| `GET /api/manutencoes?caminhaoId=&de=&ate=&incluirExcluidas=false` | lista |
| `GET /api/manutencoes/{id}` | manutenção |
| `POST /api/manutencoes` | `201` |
| `PUT /api/manutencoes/{id}` | `200` |
| `DELETE /api/manutencoes/{id}` | `204`, exclusão lógica |

Corpo: `caminhaoId`, `data` (não futura), `descricao` (livre, até 500),
`valor` (positivo). Caminhão excluído não recebe nem altera manutenções
(`409`); caminhão inativo aceita. Manutenção não altera nenhuma viagem.
