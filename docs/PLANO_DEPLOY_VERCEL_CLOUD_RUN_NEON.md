# Frota+: plano de deploy em Vercel + Cloud Run + Neon + Brevo

Auditoria de viabilidade de 26/11/2026, feita sobre o `main` em `19d65be`. **Nenhum recurso
foi criado, implantado ou contratado.**

Este documento complementa `DEPLOYMENT_PRODUCTION.md`, que continua sendo a referência
genérica de deploy, variáveis e smoke test. Aqui não há credenciais, URLs de conexão reais
nem tokens.

Rótulos usados:
- **Ensaio local:** executado nesta máquina (Windows, JDK 21.0.13, PostgreSQL 16.16).
- **Documentação:** confirmado nas páginas oficiais dos provedores, consultadas em
  26/11/2026.
- **Pendente:** depende de conta, recurso ou decisão externa.

## 1. Arquitetura

```
Navegador ──HTTPS──> Vercel (Angular, estático)
    │
    └──HTTPS + JWT──> Cloud Run (API Spring Boot, container)
                          ├──TLS──> Neon (PostgreSQL 16, São Paulo)
                          └──587 STARTTLS──> Brevo (SMTP)
Código: GitHub (main) ──build──> imagem ──> Cloud Run
```

O front-end chama a API **direto**, por CORS. Um rewrite da Vercel (`/api/*` → Cloud Run)
também funcionaria, mas não é recomendado:
- o limite de tentativas por IP passaria a ver o IP dos servidores da Vercel, e todos os
  usuários dividiriam o mesmo balde;
- cada requisição ganharia mais um salto de rede.

## 2. Regiões

| Componente | Região | Situação |
|---|---|---|
| Cloud Run | `southamerica-east1` (São Paulo), preço **Tier 2** | Documentação |
| Neon | `aws-sa-east-1` (São Paulo), com PostgreSQL disponível | Documentação. **A região não muda depois de criado o projeto:** confira antes de criar |
| Vercel | Rede global; o ensaio foi servido por `gru1` (Guarulhos) | Ensaio (cabeçalho `X-Vercel-Id`) |
| Brevo | Serviço global | — |

## 3. Serviços e planos

| Fase | Vercel | Cloud Run | Neon | Brevo |
|---|---|---|---|---|
| Acadêmica | Hobby (só uso pessoal ou não comercial) | Por requisição, mínimo 0 instâncias | Free | Free (300 e-mails/dia) |
| Comercial | **Pro**: obrigatório quando houver exploração comercial | Mínimo de 1 instância quando o cold start incomodar | Launch | Free ou Starter, conforme o volume |

O Cloud Run exige uma **conta de faturamento vinculada**, mesmo dentro da franquia
gratuita. Vincular o faturamento é uma decisão do responsável.

## 4. Container e porta (Cloud Run)

Fonte: `Dockerfile` e ensaio do jar real no perfil `prod`.

**Imagem:**
- multi-stage: `eclipse-temurin:21-jdk` → `eclipse-temurin:21-jre`;
- usuário `frotamais`, sem root;
- `SPRING_PROFILES_ACTIVE=prod` e `-XX:MaxRAMPercentage=75`;
- **nenhum segredo na imagem**;
- logs na saída padrão, sem gravar nada em disco;
- o Maven não roda testes dentro da imagem; eles rodam no CI.

**Porta:** o `application-prod.properties` tem `server.port=${PORT:8080}`, sem
`server.address`, e o Tomcat escuta em todas as interfaces.
- Ensaio com `PORT=9090`: a API escutou em `::` (inclui IPv4) na 9090, e a 8080 ficou
  livre.
- O `EXPOSE 8080` é só informativo.

**Inicialização** (1 CPU, heap até 768 MB = 75% de 1 GiB; ensaio local):

| Medida | Valor |
|---|---|
| Spring pronto, banco novo com V1–V12 | 8,1 s (até o health responder: 9,4 s) |
| Spring pronto, banco já migrado | 8,5–9,3 s |
| Flyway: V1–V12 em banco vazio / só validação | 0,24 s / 0,04 s |
| Memória do processo depois do smoke test | cerca de 404 MB; heap usado cerca de 94 MB; metaspace cerca de 100 MB |
| Coletor escolhido com 1 CPU | Serial GC (o mesmo que o Cloud Run terá com 1 vCPU) |
| Latência local, `GET /api/caminhoes` autenticado | p50 27 ms, p95 34 ms |
| Login (BCrypt) | cerca de 80 ms |

Esses números foram medidos num notebook, não na nuvem. O Cloud Run e o Neon somam
latência de rede e o tempo de ativação do Neon.

**Configuração inicial recomendada:**

| Item | Acadêmica | Comercial inicial |
|---|---|---|
| CPU / memória | 1 vCPU / **1 GiB** (com 512 MiB, o processo de cerca de 404 MB fica sem folga) | 1 vCPU / 1 GiB, revendo pelo consumo |
| Instâncias mín. / máx. | 0 / 2 | 1 / 3 |
| Concorrência por instância | 20 (o pool de banco tem 5 conexões; acima disso, as requisições só esperariam na fila) | 20–40 |
| Startup CPU boost | ligado (padrão em serviços novos) | ligado |
| Timeout da requisição | 60 s | 60 s |
| Ingress | público, sem autenticação do IAM (a API autentica por JWT) | igual |
| Faturamento | por requisição | por instância, se o mínimo for 1 |

**Sondas:**
- **Startup:** HTTP `GET /actuator/health/readiness`, período 3 s, limite de 40 falhas
  (cerca de 120 s).
- **Liveness:** HTTP `GET /actuator/health/liveness`.
- Sem uma sonda de startup, o Cloud Run usa uma TCP padrão, que dá a instância como pronta
  quando a porta abre, antes do Flyway terminar.

**Desligamento:** o Cloud Run manda `SIGTERM` e espera cerca de 10 s. Como o perfil `prod`
usa `server.shutdown=graceful`, defina `SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE=8s`.

## 5. Health check

Respostas no ensaio do perfil `prod`:
- `/actuator/health` → `200 {"groups":["liveness","readiness"],"status":"UP"}`;
- `/actuator/health/liveness` e `/readiness` → `200 {"status":"UP"}`;
- `/actuator/env` → `401`.

Não aparecem banco, host, componentes nem detalhes. A lista `groups` só nomeia as duas
sondas e não expõe nada interno.

## 6. Banco Neon

| Item | Decisão |
|---|---|
| Projeto | Um projeto novo, criado em `aws-sa-east-1`, com PostgreSQL 16 |
| Banco | Banco próprio, por exemplo `frotamais_prod` (nada de `dev`, `test`, `local`, `homolog`, `demo`, `tmp`, `backup` etc.: o `GuardaProducao` recusa), com o mesmo nome em `DB_NOME_PRODUCAO` |
| Usuário | Papel dedicado da aplicação, dono do schema `public` (o Flyway cria as tabelas), com senha forte gerada pelo Neon |
| Branch | Só o branch principal (produção). Ensaios em branch separado, **nunca** com o nome de produção |

**Compatibilidade, conferida no código:**
- **Recursos usados:** só os comuns — `GENERATED BY DEFAULT AS IDENTITY`, `CHECK` e
  `SELECT … FOR UPDATE` (`@Lock(PESSIMISTIC_WRITE)`) dentro de transação.
- **Recursos ausentes:** extensões, `LISTEN`/`NOTIFY`, `SET` de sessão, tabelas
  temporárias, `PREPARE` em SQL e advisory locks no código da aplicação.
- **Stack:** pgjdbc 42.7.13, HikariCP 7.0.2, Flyway 12.4.0 e Hibernate do Spring Boot 4.1.1.

### 6.1 Conexão direta ou pooler

| | Direta (`ep-…`) | Pooler (`ep-…-pooler`, PgBouncer em modo transaction) |
|---|---|---|
| Flyway, `pg_dump`, `pg_restore` | **sim** (recomendação do Neon) | não |
| Runtime da API | sim, com pool pequeno | sim: só aceita prepared statements de protocolo, que é o que o pgjdbc usa; `SET` e `LISTEN` não funcionam, e o código não usa nenhum dos dois |

**Recomendação para as duas fases iniciais: usar a conexão direta para tudo**, numa
única `DB_URL`.

O Flyway roda na subida, pelo mesmo `DataSource` que o `GuardaProducao` confere. A conta de
conexões fecha com folga:
- **pior caso:** 3 instâncias × 5 conexões = 15;
- **limite do Neon com 0,25 CU:** cerca de 112 `max_connections` (cerca de 105 utilizáveis,
  porque 7 são reservadas).

**Não existe `SPRING_FLYWAY_URL` no `prod`.** Num ensaio de 26/11/2026, com
`SPRING_FLYWAY_URL` apontando para outro banco, o guarda aprovou a `DB_URL`, e o Flyway
migrou o outro banco sem validação.

A correção de 26/11/2026 (`GuardaProducao`, já no `main`) fecha esse caminho:
- **antes de abrir qualquer conexão:** o perfil `prod` recusa `SPRING_FLYWAY_URL`, `_USER`,
  `_PASSWORD` e `_DRIVER_CLASS_NAME`;
- **imediatamente antes do `migrate`:** a guarda exige o mesmo `DataSource` validado e
  reconfere o `current_database()`.

Por isso, **a `DB_URL` é a conexão direta**, e o Flyway roda por ela na subida.
Se um dia o pooler for necessário no runtime, é preciso outro desenho, que exige nova
decisão: por exemplo, migrations num Cloud Run Job separado, pela conexão direta, com a
mesma guarda. Nunca reabra a conexão própria do Flyway no serviço.

### 6.2 URL JDBC

Formato, sem valores reais:

```
jdbc:postgresql://<endpoint>.sa-east-1.aws.neon.tech/<banco>?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory&channelBinding=require
```

- **`sslmode=verify-full` + `DefaultJavaSSLFactory`:** valida o certificado do Neon pelo
  truststore da JVM, sem precisar de arquivo de CA.
- **Parâmetros recusados pelo guarda:** a URL não pode trocar banco ou servidor por
  parâmetro (`pgdbname`, `pghost`, `pgport`, `service`, `dbname`, ou chaves com `%`).
- **Ensaio local:** com `sslmode`, `channelBinding`, `sslfactory` e `connectTimeout`, a URL
  foi aceita pelo guarda e pelo driver.
- **Pendente:** o handshake TLS real com o Neon.

### 6.3 Pool (Hikari)

Variáveis de ambiente; nenhuma mudança de código:

| Variável | Valor | Motivo |
|---|---|---|
| `SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE` | `5` | 3 instâncias × 5 = 15, bem abaixo do limite do plano |
| `SPRING_DATASOURCE_HIKARI_MINIMUMIDLE` | `0` | o pool esvazia quando não há uso, e o Neon pode suspender |
| `SPRING_DATASOURCE_HIKARI_IDLETIMEOUT` | `120000` | 2 min, menos que os 5 min que o Neon espera antes de suspender |
| `SPRING_DATASOURCE_HIKARI_MAXLIFETIME` | `1800000` | 30 min |
| `SPRING_DATASOURCE_HIKARI_CONNECTIONTIMEOUT` | `30000` | dá tempo para o Neon acordar |

**Na homologação, confira** no painel do Neon que a compute vai para *Idle* depois de
cerca de 5 min sem tráfego. Uma instância do Cloud Run ociosa que mantenha conexões abertas
deixaria o Neon acordado, consumindo CU-horas.

## 7. Flyway e migrations V1–V12

- **Ensaio local:** V1–V12 aplicadas num banco novo, pelo jar `prod`, em 0,24 s. No banco já
  migrado, só validação.
- **Proteções do perfil `prod`:** `clean-disabled=true` e `baseline-on-migrate=false`. Nunca
  use `clean`, `baseline` ou `repair` para contornar uma falha.
- **Ordem na subida:** o `GuardaProducao` confere o banco **antes** do Flyway. No ensaio,
  todas as recusas pararam a subida e não migraram nada (seção 12).
- **Na primeira subida no Neon, confira no log:**
  `Perfil prod ativo; banco '<banco>'` e `Successfully applied 12 migrations … v12`.

## 8. Variáveis do Cloud Run

| Variável | Onde fica | Observação |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | variável comum | `prod` (já vem no Dockerfile) |
| `DB_URL` | **Secret Manager** | não contém senha, mas expõe o host |
| `DB_USER` | variável comum | papel da aplicação |
| `DB_PASSWORD` | **Secret Manager** | |
| `DB_NOME_PRODUCAO` | variável comum | igual ao banco da `DB_URL` |
| `JWT_SECRET` | **Secret Manager** | **novo e exclusivo**, 48 bytes aleatórios em Base64 (`openssl rand -base64 48`); nunca o local, nunca o do commit `5af35d7` |
| `JWT_HORAS` | variável comum | opcional (padrão: 8) |
| `CORS_ORIGENS_PERMITIDAS` | variável comum | `https://front-frota-mais.vercel.app`, **só depois que o responsável pelo front-end confirmar** que essa é a URL definitiva; sem barra, sem caminho, sem `*` |
| `SPRING_MAIL_HOST` / `SPRING_MAIL_PORT` | variável comum | `smtp-relay.brevo.com` / `587` |
| `SPRING_MAIL_USERNAME` | **Secret Manager** | login SMTP da Brevo |
| `SPRING_MAIL_PASSWORD` | **Secret Manager** | chave SMTP (`xsmtpsib-…`) |
| `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH`, `…_STARTTLS_ENABLE`, `…_STARTTLS_REQUIRED` | variável comum | `true` |
| `SMTP_TIMEOUT_MS` | variável comum | opcional (padrão: 10000) |
| `EMAIL_REMETENTE` | variável comum | remetente verificado na Brevo, de preferência de domínio próprio (seção 14) |
| `CONVITE_URL_ATIVACAO` | variável comum | `https://<front>/ativar-conta` |
| `CONVITE_VALIDADE_HORAS` | variável comum | opcional (padrão: 24) |
| `FORWARD_HEADERS_STRATEGY` | variável comum | `native` (padrão) |
| `SPRING_DATASOURCE_HIKARI_*` | variável comum | seção 6.3 |
| `SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE` | variável comum | `8s` |
| `PORT` | **não defina** | o Cloud Run injeta |

## 9. Secret Manager e permissões

- **Segredos:** `frotamais-db-url`, `frotamais-db-password`, `frotamais-jwt-secret`,
  `frotamais-smtp-username` e `frotamais-smtp-password`, ligados ao serviço por
  `--set-secrets` com a versão `latest`.
- **Service account dedicada** do serviço (por exemplo, `frotamais-api@…`), sem a conta
  padrão do Compute:
  - `roles/secretmanager.secretAccessor` concedido **em cada segredo**, nunca no projeto;
  - nenhum outro papel é necessário para a aplicação.
- **Quem implanta** precisa de `roles/run.admin` e `roles/iam.serviceAccountUser` sobre essa
  service account, além do papel para enviar a imagem ao Artifact Registry.
- **Nada de segredo** em `Dockerfile`, repositório, imagem, `--set-env-vars` ou log.

## 10. CORS

- **Configuração:** `ConfigCors` aceita só origens `https` exatas, e o guarda recusa lista
  vazia, `*`, `http` e origem com caminho.
- **Ensaio `prod`:**

| Preflight | Resposta |
|---|---|
| Origem do front | `200`, com `Access-Control-Allow-Origin` exato |
| Outra origem | `403` |

  Com barra final, o Spring trata como a mesma origem, e o navegador nunca envia essa barra.
- **Previews da Vercel** (`*-git-*.vercel.app`) não entram, a menos que sejam listados um a
  um.

## 11. JWT

- HS256, com emissor `frota-mais`, 8 h de validade, e usuário e empresa conferidos no banco
  a cada requisição.
- **Trocar o `JWT_SECRET` derruba todas as sessões.** É o procedimento em caso de vazamento.
- **Lacuna conhecida:** trocar a senha não invalida tokens já emitidos. A proposta V13 ainda
  aguarda decisão.

## 12. Proteção de produção (`GuardaProducao`): ensaio local com o jar real

| Caso | Resultado |
|---|---|
| Sem `DB_NOME_PRODUCAO` | não sobe |
| `DB_NOME_PRODUCAO` = banco de dev / de teste | não sobe (nome não produtivo) |
| `DB_URL` aponta para outro banco | não sobe |
| `?PGDBNAME=` ou `db%6Eame=` na URL | não sobe |
| CORS vazio / `*` | não sobe |
| Banco autorizado + parâmetros de TLS no formato do Neon | sobe |
| `SPRING_FLYWAY_URL` para outro banco (antes da correção) | subia e migrava o outro banco: desvio |
| `SPRING_FLYWAY_URL` para outro banco, para banco de QA ou para o próprio banco; só `SPRING_FLYWAY_USER`; só `_PASSWORD` (depois da correção, jar real) | não sobe; os bancos alternativos ficam com 0 tabelas |
| Banco autorizado com `qa` como termo próprio (`frotamais_cloudrun_qa`, `frotamais_qa_alvo`) | não sobe |

Nenhum caso recusado criou tabelas: os bancos de dev e de teste seguiram na V12, e o banco
descartável ficou intacto.

## 13. SMTP (Brevo)

| Etapa | Situação |
|---|---|
| AUTH em `smtp-relay.brevo.com:587` com STARTTLS, a partir do Windows | **`235`** em 26/11/2026; nenhum e-mail enviado |
| Configuração local | as 9 variáveis obrigatórias preenchidas; host, porta, STARTTLS e link `https://…/ativar-conta` conferidos sem imprimir valores; o remetente é diferente do login SMTP |
| Envio real, recebimento e spam | **pendente**: aguarda destinatário e autorização |
| Ativação pela página da Vercel | **pendente**: a página publicada não alcança uma API local; depende da API hospedada (e do front atualizado, seção 15) |
| Saída 587 a partir do Cloud Run | **bloqueado**: depende de um ambiente publicado. Pela documentação, o Google bloqueia só a porta 25; 587 e 465 com TLS são permitidas, salvo regra de firewall do próprio projeto |

**Bloqueio por IP (Brevo):** se *Settings → Security → Authorized IPs* bloquear IPs
desconhecidos para chaves SMTP, a Brevo responde `525`.
- O Cloud Run sai por IPs dinâmicos do Google. Para usar esse bloqueio, seria preciso um IP
  de saída fixo (Cloud NAT + VPC), **que tem custo**.
- Na fase acadêmica, deixe o bloqueio de SMTP desligado e proteja a chave no Secret
  Manager.
- **Não autorize IPs sem decisão do responsável.**

**Entregabilidade:** o remetente atual é de **domínio gratuito**.
- A Brevo não autentica domínios gratuitos (DKIM/DMARC) e pode trocar o endereço exibido.
- Mensagens para o Gmail tendem a ir para o spam.
- Para a fase comercial: domínio próprio autenticado na Brevo (código Brevo, DKIM e DMARC
  com `rua`).

**Roteiro do teste de SMTP no primeiro deploy:**
1. Suba com as variáveis de SMTP.
2. Como DONO, cadastre **um** motorista de QA para um endereço controlado pelo
   responsável.
3. Confira a resposta: `201` significa que a Brevo aceitou o envio.
   - `503` com `java.net.SocketTimeoutException`/`ConnectException` no log: bloqueio de rede.
   - `535`: credencial.
   - `525`: IP não autorizado.
4. Confira, no painel da Brevo (*Transactional → Logs*), a entrega.
5. Peça ao destinatário que verifique a caixa de entrada e o spam.
6. Abra o link, ative a conta e faça login como MOTORISTA.

## 14. Backup e restauração

**Neon (documentação):**

| Plano | Restauração instantânea (PITR) | Snapshots manuais |
|---|---|---|
| Free | até **6 h** (limite de 1 GB de alterações) | 1 |
| Launch | até **7 dias** (padrão 1 dia; US$ 0,20/GB-mês) | até 100 |

Isso não substitui uma cópia independente.

**Ensaio local de restauração** (26/11/2026), feito no banco `frotamais_cloudrun_qa`,
criado pelo jar `prod`, com conta fictícia:
1. `pg_dump -Fc --no-owner --no-privileges` → 44.615 bytes.
2. `pg_restore` num banco novo → exit 0.
3. Contagens, sequências e checksum do `flyway_schema_history` idênticos.
4. **O jar `prod` subiu no banco restaurado,** validou V12 sem migrar e aceitou o login da
   conta fictícia.

**Rotina proposta:**
- **Diário:** `pg_dump -Fc` pela **conexão direta**, com cliente 16 ou superior, enviado
  para um armazenamento fora do Neon. Retenção: 7 diários + 4 semanais.
- **Mensal:** restaurar o último dump num banco descartável, rodar o jar `prod` com
  `DB_NOME_PRODUCAO` apontando para ele, fazer login com a conta de QA e conferir contagens.
- **Antes de cada versão com migration nova:** um snapshot manual no Neon e um dump.

O dump contém dados pessoais e hashes de senha: guarde-o criptografado, com acesso
restrito.

## 15. Front-end (Vercel), levantado só por leituras HTTP do site publicado

- **Disponibilidade:**
  - `https://front-frota-mais.vercel.app` → `200`, título "Frota+", HTTPS com HSTS;
  - `http` → `308` para `https`;
  - certificado `*.vercel.app` válido até 29/01/2027.
- **Rotas:** `/ativar-conta`, `/ativar-conta?token=…` e uma rota inexistente → `200` com o
  `index.html`. O fallback de SPA já funciona.
- **Build:** os arquivos `main-<hash>.js` e `polyfills-<hash>.js` indicam o *application
  builder* do Angular 17+, cuja saída é `dist/<projeto>/browser`.
- **URL da API no bundle publicado:**
  `typeof FROTA_API_URL=="string" ? FROTA_API_URL : "/api"`.
  - Nenhuma URL absoluta está embutida, e não existe script de configuração (`env.js` e
    `config.js` devolvem o `index.html`).
  - Hoje o front chama `/api` no próprio domínio da Vercel, que responde `404`.
- **Contrato do bundle publicado:** monta o abastecimento com `hora`
  (`hora:t.hora?t.hora:null`) e não envia `quilometragem`, ou seja, segue o contrato 1.1.
  A API 1.2 recusa com `400 Campo desconhecido: 'hora'`. O código atual do front-end pode estar
  mais novo que o build publicado; só ele confirma.

**O que o responsável pelo front-end precisa conferir:**
- **Preset e build:** Framework Preset *Angular*, Build Command `ng build`
  (configuração de produção) e Output Directory `dist/<projeto>/browser`, conferido contra
  o `angular.json` dele.
- **Node:** uma versão suportada pela versão do Angular do projeto.
- **`FROTA_API_URL`:** definida com a URL do Cloud Run, sem `/` no fim, mais o sufixo `/api`
  se o código esperar. Duas formas possíveis:
  - um script carregado antes do `main` que defina `window.FROTA_API_URL`;
  - um `define` no build.
- **Sem rewrite de `/api` para o Cloud Run,** pelo motivo da seção 1.
- **Contrato 1.2:** `quilometragem` obrigatória, sem `hora`, datas `AAAA-MM-DD` (detalhes em
  `RELEASE_HANDOFF.md`).
- **URL definitiva:** confirmar se `https://front-frota-mais.vercel.app` vai para o CORS e
  para o link do convite.

## 16. Testes depois do deploy

1. **Health:** `GET /actuator/health` → `200 UP`; `/actuator/env` → `401`.
2. **Log da subida:** `Perfil prod ativo; banco '<banco>'` e Flyway em V12.
3. **Smoke test** do `DEPLOYMENT_PRODUCTION.md` §7, com conta `@exemplo.test`: registro,
   login, rota protegida, `401` sem token e CORS do front (`200`) e de outra origem
   (`403`).
4. **IP real no limite de tentativas:** 31 logins errados seguidos, vindos do mesmo cliente,
   precisam dar `429`; outro cliente segue livre. Isso confirma que o Cloud Run entrega o IP
   real por `X-Forwarded-For`.
5. **SMTP:** roteiro da seção 13.
6. **Pelo front-end:**
   - login;
   - viagem com abastecimento (diesel e Arla com quilometragem), pedágio e despesa;
   - finalização;
   - ativação pelo link do convite e login do MOTORISTA;
   - o MOTORISTA não vê custos.
7. **Neon:** a compute volta a *Idle* depois de cerca de 5 min sem uso.

## 17. Rollback

- **Aplicação:** no Cloud Run, mande 100% do tráfego para a revisão anterior.
- **Schema:** as migrations só avançam. Para voltar, restaure o snapshot ou o dump anterior,
  o que é destrutivo e precisa de autorização.
- **Segredo vazado:** crie uma nova versão do segredo e uma nova revisão. O `JWT_SECRET` novo
  derruba as sessões.

## 18. Custos estimados (consulta em 26/11/2026; **não são preços contratados**)

**Preços de referência:**

| Serviço | Preço | Fonte |
|---|---|---|
| Neon Free | 100 CU-h/mês por projeto, 1 GB por projeto (20 GB no total); sem custo | documentação oficial |
| Neon Launch | US$ 0,106/CU-h + US$ 0,35/GB-mês | documentação oficial |
| Vercel Pro | US$ 20/mês, com 1 assento e US$ 20 de crédito de uso | documentação oficial |
| Brevo Free | 300 e-mails/dia | documentação oficial |
| Brevo Starter | a partir de cerca de US$ 9/mês | documentação oficial |

**Cloud Run em São Paulo (Tier 2):** a página oficial só mostra a tabela de Iowa.

| Valor | Origem |
|---|---|
| Por requisição: cerca de US$ 0,0000336/vCPU-s e cerca de US$ 0,0000035/GiB-s | **fonte secundária**: confirme na calculadora oficial antes de decidir |
| Por instância: cerca de US$ 0,0000216/vCPU-s e cerca de US$ 0,0000024/GiB-s | **fonte secundária** |

A franquia gratuita do Cloud Run é um desconto calculado pelo **preço Tier 1**:
180.000 vCPU-s, 360.000 GiB-s e 2 milhões de requisições por mês, no modelo por
requisição. Em São Paulo, ela acaba antes do que em Iowa.

| Cenário | Premissas | Cloud Run | Neon | Vercel | Brevo | Total estimado |
|---|---|---|---|---|---|---|
| 1. Trabalho acadêmico | até cerca de 20 mil requisições/mês, mín. 0, até cerca de 40 h de CPU ativa, 1 GB, 0,25 CU acordada poucas horas | cerca de US$ 0–1 (dentro da franquia, se o uso ficar nesse nível) | US$ 0 (Free) | US$ 0 (Hobby, não comercial) | US$ 0 | **cerca de US$ 0–2/mês**, sem garantia de custo zero; Artifact Registry e Cloud Build podem somar alguns centavos |
| 2. Primeira transportadora | cerca de 100 mil requisições/mês, cerca de 5 usuários, horário comercial | mín. 0: cerca de US$ 1–5; **mín. 1 (sem cold start): cerca de US$ 55–65** | Launch, 0,25 CU por cerca de 80–180 CU-h: cerca de US$ 9–20 | Pro: US$ 20 | Free: US$ 0 | **cerca de US$ 30–105/mês** |
| 3. Várias transportadoras | cerca de 1 milhão de requisições/mês, cerca de 50 usuários, mín. 1, máx. 3 | cerca de US$ 70–90 | Launch, 0,5 CU sempre ativo (cerca de 365 CU-h) + 5 GB + PITR de 7 dias: cerca de US$ 42–45 | Pro: US$ 20–40 | Starter: cerca de US$ 9–25 | **cerca de US$ 140–200/mês** |

Os custos externos à nuvem estão fora da tabela: domínio próprio (anuidade do registrador)
e impostos ou IOF sobre cobrança em dólar.

**Controle de gastos** (o responsável configura; nada foi criado):
- **Orçamento:** um budget no Google Cloud de US$ 10 na fase acadêmica, com alertas em 50%,
  90% e 100%. **Um budget avisa, mas não bloqueia a cobrança.**
- **Limites:** máximo de instâncias do Cloud Run (2/3) e pool Hikari de 5 conexões.
- **Logs:** Cloud Logging com a retenção padrão do bucket `_Default`; evite logs de depuração
  em produção.
- **Recursos esquecidos:** revise a cada mês as imagens antigas no Artifact Registry e os
  branches e snapshots do Neon.

## 19. Pontos que dependem do front-end

1. Confirmar a URL definitiva do front (CORS e link do convite).
2. Publicar o front no contrato 1.2: `quilometragem` no abastecimento e nada de `hora`.
3. Definir `FROTA_API_URL` com a URL do Cloud Run e conferir que as chamadas saem para ela.
4. Conferir o preset, o comando de build, o diretório de saída e a versão do Node na Vercel.
5. Garantir que `/ativar-conta` lê o `token` da URL e chama
   `POST <API>/api/convites/ativacao`.
6. Rodar os testes da seção 16, item 6, quando a API estiver hospedada.
7. Planejar a mudança para o Vercel Pro (ou outra hospedagem estática comercial) antes de
   qualquer uso comercial.

## 20. Passos manuais (responsável), em ordem

1. Conferir que a imagem é construída de um `main` que contém a correção do `GuardaProducao`
   de 26/11/2026 (seção 21, itens 1 e 2).
2. **Google Cloud:**
   - criar o projeto e vincular o faturamento;
   - criar o budget e os alertas;
   - habilitar Cloud Run, Artifact Registry, Cloud Build e Secret Manager.
3. **Neon:**
   - criar o projeto **em `aws-sa-east-1`**, conferindo a região antes de confirmar;
   - criar o banco e o papel da aplicação;
   - guardar a URL direta.
4. **Brevo:**
   - confirmar o remetente verificado;
   - revogar a chave SMTP antiga;
   - decidir sobre o bloqueio por IP e sobre o domínio próprio.
5. **Segredos:** gerar um `JWT_SECRET` novo, criar os segredos e a service account com acesso
   só a eles.
6. **Build:** construir a imagem a partir do `main` (Cloud Build ou Docker) no Artifact
   Registry de `southamerica-east1`.
7. **Serviço Cloud Run:** criar com a configuração da seção 4, as variáveis da seção 8 e as
   sondas.
8. **Validar:** rodar a seção 16, e então entregar a URL da API ao responsável pelo front-end.

## 21. Riscos e correções

1. **`SPRING_FLYWAY_URL` desviava o `GuardaProducao`. Corrigido** em 26/11/2026
   (já no `main`).
   - A conexão própria do Flyway é recusada no `prod`.
   - A guarda é também o `FlywayMigrationStrategy`: antes do `migrate`, exige o mesmo
     `DataSource` validado e reconfere o `current_database()`.
   - Testes de regressão em `GuardaProducaoTest`, mais um ensaio com o jar real.
2. **Nome com `qa` passava como produtivo. Corrigido:** `qa` e `hml` como termo próprio
   (separados por `_` ou `-`, com número opcional) agora são não produtivos.
3. **Front publicado no contrato 1.1:** o abastecimento falha com `400`. A ação é do responsável pelo front-end.
4. **Remetente de domínio gratuito:** risco de spam. Recomendação: domínio próprio
   autenticado.
5. **Limite de tentativas em memória, por instância:** com 3 instâncias, o limite efetivo
   pode triplicar. Aceito na fase acadêmica.
6. **Cold start:** cerca de 9 s locais com 1 CPU; na nuvem, a medir. Mitigações: CPU boost
   agora; mínimo de 1 instância na fase comercial.
