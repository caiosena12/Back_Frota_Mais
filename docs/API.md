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
- Convite: uso único, validade `app.convites.validade-horas` (padrão 24 h);
  reenviar invalida o anterior. Token inválido, usado, expirado ou
  invalidado: `400`, sem dizer qual dos casos.

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

Resposta: `id`, `nome`, `email`, `situacao` (`CONVITE_PENDENTE`, `ATIVO`, `INATIVO`),
`caminhaoIds`, `conviteExpiraEm`.

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
`kmInicialVazio` e/ou `kmInicialCarregado`, consumos médios.

**Alteração** (`PUT`): mesmos campos, sem `caminhaoId`; `dataFim` e
`kmFinalCarregado` só em viagem encerrada.

**Finalização:** `dataFim`, `kmFinalCarregado`, consumos (se ausentes, valem os
gravados). Exige valor do frete, origem e destino.

**Cancelamento:** `motivo` (obrigatório, texto livre), `dataFim` (padrão: hoje),
`kmFinalCarregado` e consumos opcionais. Só viagem em andamento. A viagem fica
`CANCELADA`, com período fechado e resultado consolidado; não é apagada.

**Histórica:** viagem que já aconteceu, criada finalizada numa só operação, com
listas opcionais `abastecimentos`, `pedagios` e `despesasExtras`.

**Viagem encerrada** (finalizada ou cancelada): não aceita lançamento novo nem
exclusão (`409`); o DONO corrige pelo `PUT`, e cada campo corrigido fica em
`/alteracoes`, com a fotografia anterior em `/fotografias-anteriores`.

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
  (primeiro frete do caminhão), é recusado (`400`): regra ainda não definida.
- A data final de uma viagem não pode passar dos abastecimentos antecipados
  da seguinte (`409`).

### Indicadores

`GET /api/viagens/indicadores` com um filtro de período (`ano`; `ano`+`mes`;
`semana` = qualquer data da semana, segunda a domingo; ou `de`/`ate`) e
`caminhaoId` opcional. Fretes entram pela data final; manutenções, pela data.

Totais só de fretes finalizados com cálculo completo (incompletos listados à
parte). Margem ponderada (lucro ÷ receita × 100). Também: custo e lucro por km,
percentual de km vazio, consumo médio, diesel pago × estimado (informativo,
nunca somado ao custo), quantidade e custo de manutenções e resultado após
manutenções. Viagens canceladas não entram.

`GET /api/viagens/indicadores/por-caminhao`: os mesmos indicadores para cada
caminhão com frete finalizado ou manutenção no período.

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
