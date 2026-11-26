# Frota+: deploy em produção

Como publicar a API com segurança. Os contratos estão em
[`RELEASE_HANDOFF.md`](RELEASE_HANDOFF.md).

> **Situação em 25/11/2026: NÃO publicado.** A aplicação está pronta para
> produção (build, testes, perfil `prod`, Dockerfile, CORS e health check), mas
> faltam a hospedagem, o banco exclusivo de produção, o SMTP e a URL do
> front-end. Esses itens dependem de conta, possível custo e decisão do
> responsável (seção 8). A URL do Codespaces **não** é produção.

## 1. Artefatos

| Arquivo | Para quê |
|---|---|
| `Dockerfile` | Imagem em 2 etapas (JDK para compilar, JRE para rodar), sem root, com `SPRING_PROFILES_ACTIVE=prod` |
| `.dockerignore` | Impede que `contexto-local/`, `.frotamais-jwt.env`, `.backup-auth-frotamais/` e `*.env` entrem na imagem |
| `.env.example` | Nomes das variáveis, todas com valores **fictícios** |
| `application-prod.properties` | Perfil de produção, sem nenhum segredo |
| `seguranca/GuardaProducao` | Fail-closed: o perfil `prod` só sobe no banco autorizado em `DB_NOME_PRODUCAO` (nunca de desenvolvimento, teste, integração ou ensaio), confirmado pelo próprio PostgreSQL; também exige CORS https e recusa o `dev` junto. Age **antes** do Flyway |

## 2. Variáveis de ambiente (só os nomes)

Cadastre-as no painel de segredos da hospedagem, nunca em arquivo versionado.

| Variável | Obrigatória | Observação |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | sim | `prod` (o Dockerfile já define) |
| `DB_URL` | sim | `jdbc:postgresql://host:5432/banco?sslmode=require`; banco **exclusivo** de produção, com o nome exatamente igual a `DB_NOME_PRODUCAO`, em minúsculas, sem `%` e sem parâmetros `PGDBNAME`, `PGHOST`, `PGPORT`, `service` ou `dbname` |
| `DB_NOME_PRODUCAO` | sim | Nome do banco de produção autorizado (letras minúsculas, números e `_`). Não pode ser `frotamais` nem conter `test`, `dev`, `local`, `integracao`, `missao`, `ensaio`, `copia`, `homolog`, `staging`, `sandbox`, `demo`, `exemplo`, `tmp`, `temp`, `backup`, `bkp` ou `descartavel`. **Sem ele o `prod` não sobe**; ainda não há banco de produção contratado |
| `DB_USER`, `DB_PASSWORD` | sim | Usuário próprio da aplicação, não o superusuário |
| `JWT_SECRET` | sim | Base64 de ≥ 32 bytes, **novo** (nunca o de desenvolvimento): `openssl rand -base64 48` |
| `CORS_ORIGENS_PERMITIDAS` | sim | Origem https exata do front-end; várias separadas por vírgula; nunca `*` |
| `PORT` | não | Porta HTTP (padrão 8080); muitas hospedagens definem sozinhas |
| `JWT_HORAS` | não | 1 a 24 (padrão 8) |
| `EMAIL_REMETENTE`, `CONVITE_URL_ATIVACAO`, `CONVITE_VALIDADE_HORAS` | para convites | Sem eles, os convites respondem `503` |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH`, `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE` | para convites | SMTP: guia e provedores em [`CONFIGURACAO_SMTP.md`](CONFIGURACAO_SMTP.md) |
| `SMTP_TIMEOUT_MS` | não | Timeout do SMTP (padrão 10000) |
| `FORWARD_HEADERS_STRATEGY` | não | `native` (padrão no prod): IP real pelo `X-Forwarded-For` só de proxies confiáveis. `none` sem proxy |
| `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` | depende da hospedagem | Regex dos IPs do proxy, se ele não vier de rede privada |

**Nunca em produção:** `EMAIL_CAPTURA_LOCAL` e `EMAIL_CAPTURA_DIR`. A
propriedade só existe no perfil `dev`; em `prod` são ignoradas (ensaio de
25/11: a API subiu com "Envio de e-mail não configurado").

Falta de configuração obrigatória = a API **não sobe**, com mensagem clara no
log (ex.: `CORS_ORIGENS_PERMITIDAS é obrigatória no perfil prod`,
`Could not resolve placeholder 'JWT_SECRET'`).

## 3. Build e inicialização

| Ação | Comando |
|---|---|
| Testes + build (Codespaces/CI) | `./mvnw clean verify` |
| Jar | `./mvnw clean package -DskipTests` → `target/backend-0.0.1-SNAPSHOT.jar` |
| Imagem | `docker build -t frotamais-api .` |
| Rodar a imagem | `docker run --env-file <arquivo-fora-do-git> -p 8080:8080 frotamais-api` |
| Rodar o jar | `SPRING_PROFILES_ACTIVE=prod java -jar target/backend-0.0.1-SNAPSHOT.jar` (variáveis no ambiente) |

Hospedagem que constrói a partir do Git: use o `Dockerfile` da raiz. Health
check: `GET /actuator/health`.

## 4. Banco de produção e Flyway

1. Banco **novo, vazio e exclusivo** (PostgreSQL 16), com usuário próprio e
   backup automático habilitado na hospedagem.
2. Na primeira subida, o Flyway aplica V1 a V12. O perfil `prod` fixa
   `clean-disabled=true` e `baseline-on-migrate=false`.
3. **Nunca** apontar para `frotamais`, `frotamais_dev_local`,
   `frotamais_integracao*`, `frotamais_test*`, `frotamais_missao_*`,
   `frotamais_ensaio*` ou `frotamais_descartavel*`. A `GuardaProducao` recusa
   todos eles: só o banco de `DB_NOME_PRODUCAO` é aceito, e só se a conexão
   chegar a ele (`current_database()`). O `frotamais` do Codespaces tem tabelas
   sem histórico do Flyway e não deve ser adotado sem o plano de
   `docs/MIGRATIONS.md`.
4. Banco já com tabelas e sem `flyway_schema_history`: o Flyway recusa subir
   ("non-empty schema"). **Pare**: não use baseline nem repair por tentativa e
   erro (ver `docs/MIGRATIONS.md`).
5. Conferência depois da subida (somente leitura):
   `select version, success from flyway_schema_history order by installed_rank;`
   Espera-se 1 a 11, todas `t`.

## 5. Health check e logs

- `GET /actuator/health` → `{"status":"UP"}` (inclui o banco; `DOWN` com `503`
  se o banco cair). `/actuator/health/liveness` e `/readiness` para sondas
  separadas. O SMTP fica fora do status de propósito.
- Nenhum outro endpoint do Actuator é exposto (`/actuator/env`, `beans` etc.:
  `401` sem token, `404` com token).
- Sem detalhes, componentes, URL do banco ou variáveis na resposta.
- Logs: sem senha, token, link de convite ou credencial. Erros 500 registram o
  stack trace **só no servidor**. No ensaio de 25/11, 0 linhas `ERROR` e
  nenhuma ocorrência de senha, token ou credencial.

## 6. Reinício e rollback

- **Reinício:** pelo painel da hospedagem. `server.shutdown=graceful` conclui
  as requisições em andamento. Os tokens continuam válidos se o `JWT_SECRET`
  não mudar.
- **Trocar o `JWT_SECRET`** invalida todos os tokens (todos refazem login). É
  o procedimento se o segredo vazar.
- **Rollback da aplicação:** reimplantar a imagem ou o commit anterior.
- **Rollback de schema:** as migrations só avançam. Antes de publicar uma
  versão com migration nova, faça o backup do banco. Para voltar, restaure o
  backup (operação destrutiva, só com autorização). V1 a V12 rodam uma única
  vez, no banco novo.

## 7. Verificação após o deploy (smoke test)

Use uma conta fictícia (domínio `.test`), nunca dados reais. Não imprima o token.

```bash
API=https://<URL-DA-API>
FRONT=https://<URL-DO-FRONT>
curl -s $API/actuator/health                                   # {"status":"UP"}
curl -s -o /dev/null -w '%{http_code}\n' $API/api/caminhoes    # 401
curl -s -o /dev/null -w '%{http_code}\n' -X OPTIONS $API/api/auth/login \
  -H "Origin: $FRONT" -H 'Access-Control-Request-Method: POST'  # 200
curl -s -o /dev/null -w '%{http_code}\n' -X OPTIONS $API/api/auth/login \
  -H 'Origin: https://outra.exemplo.test' -H 'Access-Control-Request-Method: POST'  # 403
curl -s -o /dev/null -w '%{http_code}\n' -X POST $API/api/auth/registro \
  -H 'Content-Type: application/json' \
  -d '{"nomeEmpresa":"Smoke","nome":"Smoke","email":"smoke-AAAAMMDD@exemplo.test","senha":"<SENHA-FORTE>"}'  # 201
TOKEN=$(curl -s -X POST $API/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"smoke-AAAAMMDD@exemplo.test","senha":"<SENHA-FORTE>"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
curl -s -o /dev/null -w '%{http_code}\n' $API/api/caminhoes -H "Authorization: Bearer $TOKEN"  # 200
```

`403` de perfil exige um MOTORISTA, que só nasce por convite (depende do SMTP).
Ele está coberto pela suíte com tokens reais (`PermissaoViagemTest`,
`MotoristaViagemTest`).

### Ensaio local do perfil `prod` (25/11/2026)

Jar real com `SPRING_PROFILES_ACTIVE=prod` no Codespaces, contra o banco novo
`frotamais_ensaio_prod`, com `JWT_SECRET` aleatório gerado na sessão e não
gravado (ensaio anterior à guarda fail-closed de 26/11/2026: hoje esse nome é
recusado, e o ensaio precisa de um banco autorizado em `DB_NOME_PRODUCAO`):

| Verificação | Resultado |
|---|---|
| Flyway | V1 a V11 aplicadas, 0 falhas |
| Health, liveness, readiness | `200 UP` |
| Registro, login, operação protegida (caminhão, indicadores) | `201`, `200`, `201`/`200` |
| Sem token / token inválido / login errado | `401` no formato padrão |
| Validação | `400` em português (`placa: não pode ficar em branco`) |
| Convite sem SMTP | `503`, nada gravado |
| CORS: origem autorizada / outra origem | `200` com `Access-Control-Allow-Origin` / `403` |
| `/actuator/env` com token | `404` |
| Recusas da guarda: banco descartável, CORS vazio, `http`, `*`, com caminho | não sobe e **não migra** (banco continua com 0 tabelas) |
| Limite de tentativas desligado (`APP_TENTATIVAS_ATIVO=false`) | não sobe |
| `EMAIL_CAPTURA_LOCAL=true` no prod | ignorada: "Envio de e-mail não configurado" |
| 30 logins com `X-Forwarded-For` forjado e variando no prefixo | todos contados para o mesmo IP real; o 31º recebe `429`; outro cliente real segue livre |
| Sem `JWT_SECRET` | não sobe |

Bancos criados para o ensaio (vazios ou só com a conta fictícia; podem ser
removidos com autorização): `frotamais_ensaio_prod`,
`frotamais_ensaio_prod_cors`, `frotamais_descartavel_guarda_prod`.

## 8. O que falta para publicar (bloqueios)

| # | Item | Quem decide | Observação |
|---|---|---|---|
| 1 | **Provedor de hospedagem** com HTTPS e deploy por Docker ou por Git | Responsável | Nenhum provedor está configurado no repositório nem no ambiente. Qualquer PaaS que rode o `Dockerfile` serve. Confira o custo atual e os limites do plano gratuito antes de criar a conta: planos gratuitos costumam "dormir" sem uso e demorar a acordar, o que afeta uma apresentação |
| 2 | **PostgreSQL de produção** exclusivo, com backup | Responsável | Pode ser o banco gerenciado do mesmo provedor. Confira a retenção e a expiração do plano gratuito |
| 3 | **URL definitiva do front-end** | Equipe do front-end | Vai em `CORS_ORIGENS_PERMITIDAS` e define `CONVITE_URL_ATIVACAO` |
| 4 | **SMTP** e remetente | Responsável | Sem ele, o fluxo de motorista (convite → ativação → login) não funciona: `503` |
| 5 | **Rate limit** | — | Implementado na aplicação (em memória, por instância). Na hospedagem: conferir de onde vem o proxy (`SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES`) e usar o rate limit/WAF dela com mais de uma instância |
| 6 | **Domínio próprio** (opcional) | Responsável | A URL padrão do provedor, com HTTPS, atende |

Com 1 e 2 disponíveis, o deploy leva: criar o serviço a partir do repositório
(Dockerfile), cadastrar as variáveis da seção 2, subir, conferir o Flyway
(seção 4) e rodar a seção 7.

## 9. Riscos de segurança conhecidos

| Risco | Situação | Mitigação |
|---|---|---|
| Força bruta em login, registro e ativação | **Mitigado** | Limite por IP e por e-mail (`429`); IP real só de proxies confiáveis. Limitação: memória da instância (reinício zera; várias instâncias contam separado); ataques distribuídos exigem WAF da hospedagem |
| Enumeração de e-mail no registro (`409 E-mail já cadastrado`) | Aceito | É o contrato atual; mudar exige alinhamento |
| Diferença de tempo no login | **Corrigido** | Uma verificação BCrypt em todas as falhas (hash fictício quando não há senha) e a mesma resposta `401` (`LoginEnumeracaoTest`) |
| Token sem revogação (JWT sem estado) | Aceito | Validade de 8 h; usuário desativado perde o acesso na hora (checagem no banco) |
| LGPD (dados pessoais e financeiros) | Pendente | Avaliar antes de uso com dados reais |

## 10. Registro de publicação

| Data | Commit | URL da API | Origem CORS | Migrations | Responsável |
|---|---|---|---|---|---|
| (não publicado) | | | | | |
