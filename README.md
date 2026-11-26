# 🚛 Frota+

### Gestão que move resultados.

O **Frota+** é um sistema web de gestão financeira para caminhoneiros autônomos e pequenas frotas. A proposta é substituir o controle no papel e mostrar o lucro de cada viagem, considerando quilometragem, combustível, pedágios, receitas, despesas e manutenções.

Este repositório contém a **API REST do back-end**. O front-end em Angular ainda não foi iniciado.

> **Projeto em desenvolvimento.** A API já cobre empresas, usuários, caminhões, motoristas, viagens, lançamentos, manutenções e indicadores financeiros. Dashboards e relatórios visuais dependem do front-end.

---

## 📌 Situação atual

| Implementado | Planejado ou pendente |
|---|---|
| Registro de empresa com o primeiro usuário DONO; login com JWT | Front-end em Angular |
| Perfis DONO e MOTORISTA, com isolamento por empresa | Dashboards e relatórios visuais, exportação |
| Caminhões com marca, eixos, inativação e exclusão lógica | Rate limit em login, registro e ativação |
| Motoristas por convite de e-mail (24 h, reenvio e cancelamento), com vínculo aos caminhões | Publicação em produção (hospedagem, banco e SMTP a definir: [`docs/DEPLOYMENT_PRODUCTION.md`](docs/DEPLOYMENT_PRODUCTION.md)) |
| Viagens: abertura (com abastecimentos anteriores), finalização, cancelamento, viagem histórica, motorista responsável | |
| Abastecimentos de diesel e Arla (inclusive entre viagens e antes do primeiro frete), pedágios e despesas extras | |
| Rentabilidade por frete, com fotografia financeira e auditoria das correções | |
| Viagens canceladas no financeiro do mês do cancelamento, separadas dos fretes concluídos | |
| Manutenções por caminhão e indicadores por período e por caminhão | |
| Schema versionado (Flyway), CI e testes em banco exclusivo | |

Os endpoints estão em [`docs/API.md`](docs/API.md).

## 🛠️ Tecnologias

| Tecnologia | Uso |
|---|---|
| **Java 21** e **Spring Boot 4.1.1** | API REST |
| **Spring Data JPA** e **PostgreSQL 16** | Persistência |
| **Flyway** | Migrations do schema |
| **Spring Security + JWT** | Autenticação e autorização |
| **Spring Mail** | Convites por e-mail (SMTP opcional) |
| **JUnit e MockMvc** | Testes automatizados |
| **GitHub Actions** | Integração contínua |

## 🏗️ Arquitetura

Monólito Spring Boot organizado por funcionalidade, em `br.com.frotamais`:

| Pacote | Responsabilidade |
|---|---|
| `auth` | Registro, login e troca de senha |
| `caminhao` | Caminhões e acesso por vínculo do motorista |
| `comum` | Erros padronizados, datas e fuso |
| `convite` | Convites de ativação de motoristas |
| `email` | Envio de e-mail (SMTP ou indisponível) |
| `empresa` | Empresas |
| `manutencao` | Manutenções dos caminhões |
| `motorista` | Cadastro de motoristas e vínculos |
| `seguranca` | Spring Security e tokens |
| `usuario` | Usuários, perfis e política de senha |
| `viagem` | Viagens, lançamentos, rentabilidade, auditoria e indicadores |

Cada funcionalidade segue o fluxo **Controller → Service → Repository → banco**, com entidades JPA separadas dos DTOs de entrada e saída.

O schema é versionado pelo **Flyway** (`src/main/resources/db/migration`), e o Hibernate só confere se as entidades batem com ele (`ddl-auto=validate`). Detalhes em [`docs/MIGRATIONS.md`](docs/MIGRATIONS.md).

## 🔐 Segurança

- Senhas com BCrypt, de 8 caracteres a 72 bytes, com letra e número.
- Login devolve um JWT HS256, válido por 8 horas por padrão (`JWT_HORAS`, de 1 a 24). A chave vem de `JWT_SECRET` (Base64, ≥ 32 bytes); sem ela, a API não sobe.
- A cada requisição, a API valida o token e recarrega o usuário do banco: ele precisa estar ativo e pertencer à empresa do token. O perfil usado na autorização vem **do banco**.
- A empresa nunca vem do corpo da requisição. Recurso de outra empresa responde `404`.
- O MOTORISTA só alcança os caminhões vinculados a ele e as viagens desses caminhões, e não vê informações financeiras gerenciais.
- Convites: token aleatório de uso único, guardado só como hash, com validade configurável.
- Todos os erros seguem o formato `{"status","erro","detalhes"}`, sem stack trace.

## ⚙️ Configuração

| Variável | Uso | Padrão |
|---|---|---|
| `JWT_SECRET` | Chave do JWT (Base64, ≥ 32 bytes) | nenhum: obrigatória |
| `JWT_HORAS` | Validade do token | `8` |
| `DB_URL` | URL do PostgreSQL | nenhum: obrigatória em todos os perfis |
| `DB_USER`, `DB_PASSWORD` | Credenciais do PostgreSQL | `postgres`/`postgres` só no perfil `dev`; obrigatórias nos demais |
| `DB_NOME_PRODUCAO` | Só `prod`: nome do banco de produção autorizado (a `DB_URL` tem de apontar exatamente para ele) | nenhum: sem ele o `prod` não sobe |
| `SPRING_PROFILES_ACTIVE` | `dev` para desenvolvimento local; `prod` em produção | os scripts `run-dev-frotamais.*` usam `dev` |
| `CORS_ORIGENS_PERMITIDAS` | Origens exatas do front-end, separadas por vírgula (nunca `*`) | vazio (nenhuma); obrigatória e só https no `prod` |
| `PORT` | Porta HTTP no perfil `prod` | `8080` |
| `EMAIL_REMETENTE` | Remetente dos convites | vazio (e-mail desligado) |
| `CONVITE_URL_ATIVACAO` | Endereço da tela de ativação; o token vai como parâmetro | vazio (e-mail desligado) |
| `CONVITE_VALIDADE_HORAS` | Validade do convite (1 a 720) | `24` |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | Servidor SMTP | vazio |
| `EMAIL_CAPTURA_LOCAL`, `EMAIL_CAPTURA_DIR` | Só perfil `dev`: grava os e-mails em `contexto-local/emails-dev` em vez de enviar | desligada |
| `SMTP_TIMEOUT_MS` | Timeout de conexão, leitura e escrita do SMTP | `10000` |
| `FORWARD_HEADERS_STRATEGY`, `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` | Só `prod`: IP real atrás do proxy da hospedagem (limite de tentativas) | `native` e redes privadas |
| `JPA_SHOW_SQL` | Mostra o SQL no log | `false` |

- **Perfil `dev`** (`application-dev.properties`): credenciais locais padrão, mas **sem banco padrão**: informe `DB_URL` (o `run-dev-frotamais.ps1` usa `frotamais_dev_local`). Nunca use em produção.
- **Perfil `prod`** (`application-prod.properties`): produção. Só sobe no banco autorizado em `DB_NOME_PRODUCAO`, confirmado pelo PostgreSQL antes do Flyway, e nunca num banco de desenvolvimento, teste, integração ou ensaio; recusa também CORS sem https e o `dev` junto. Health check público em `GET /actuator/health` (só o status). Deploy, Dockerfile e variáveis: [`docs/DEPLOYMENT_PRODUCTION.md`](docs/DEPLOYMENT_PRODUCTION.md); contratos congelados: [`docs/RELEASE_HANDOFF.md`](docs/RELEASE_HANDOFF.md).
- **Sem perfil:** nenhuma credencial tem valor padrão (use `prod` em produção).
- **E-mail:** sem SMTP, remetente e URL de ativação, as operações que enviam convite respondem `503` e não gravam nada. Nenhum serviço de e-mail vem configurado.
- **Captura local de e-mails** (só desenvolvimento): com o perfil `dev` e `EMAIL_CAPTURA_LOCAL=true`, os convites viram arquivos privados em `contexto-local/emails-dev/` e nada é enviado. Fora do `dev` ou junto com SMTP, a API não sobe. Detalhes em [`docs/AMBIENTE_INTEGRACAO_FRONTEND.md`](docs/AMBIENTE_INTEGRACAO_FRONTEND.md).
- **Mensagens de validação:** em português, centralizadas em `ValidationMessages.properties`.
- **Limite de tentativas** em login, registro e ativação (`429` com `Retry-After`) e login sem diferença de resposta ou de tempo entre e-mail inexistente e senha errada. SMTP, provedores e teste do fluxo de motorista: [`docs/CONFIGURACAO_SMTP.md`](docs/CONFIGURACAO_SMTP.md).

## ▶️ Como executar

O ambiente principal é o **Windows** (PowerShell), com Java 21, PostgreSQL 16 em `localhost:5432` e o Maven Wrapper. O **GitHub Codespaces** continua como alternativa (comandos Bash abaixo de cada passo).

### Windows (PowerShell)

1. **Chave JWT** (uma vez; não sobrescreve um arquivo existente e não mostra a chave):

   ```powershell
   if (-not (Test-Path .frotamais-jwt.env)) { $b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [IO.File]::WriteAllText("$PWD\.frotamais-jwt.env", "JWT_SECRET=$([Convert]::ToBase64String($b))`nJWT_HORAS=8`n") }
   ```

2. **Banco de desenvolvimento** (uma vez, vazio; o Flyway cria as tabelas):

   ```powershell
   psql -h localhost -U postgres -d postgres -c "CREATE DATABASE frotamais_dev_local"
   ```

3. **API** em um terminal dedicado (captura local de e-mails por padrão; `-Email smtp` para envio real com `.frotamais-smtp.env`, ver [`docs/CONFIGURACAO_SMTP.md`](docs/CONFIGURACAO_SMTP.md)):

   ```powershell
   .\run-dev-frotamais.ps1
   ```

   Se o PowerShell bloquear scripts: `powershell -ExecutionPolicy Bypass -File .\run-dev-frotamais.ps1`.

4. **Testes** (banco `frotamais_test_flyway` criado vazio, como abaixo):

   ```powershell
   .\mvnw.cmd clean verify
   ```

Os scripts `.sh` rodam no Git Bash, não no PowerShell.

### Codespaces (Bash)

### 1. Crie a chave JWT

Rode uma vez. Se o arquivo já existir, o comando não faz nada:

```bash
[ -e .frotamais-jwt.env ] || printf 'JWT_SECRET=%s\n' "$(openssl rand -base64 32)" > .frotamais-jwt.env
chmod 600 .frotamais-jwt.env
```

O arquivo `.frotamais-jwt.env` é ignorado pelo Git. Nunca o publique.

### 2. Prepare o banco de desenvolvimento

Num banco **novo e vazio**, o Flyway cria tudo na primeira subida. Num banco `frotamais` criado antes do Flyway, siga **antes** o procedimento de adoção de [`docs/MIGRATIONS.md`](docs/MIGRATIONS.md). Sem isso, a API recusa a subida.

### 3. Inicie a API

Em um terminal dedicado, sempre informando o banco (o perfil `dev` não tem banco padrão):

```bash
DB_URL=jdbc:postgresql://localhost:5432/frotamais ./run-dev-frotamais.sh
```

A API sobe na porta `8080`. Em outro terminal:

```bash
curl -i http://localhost:8080/api/caminhoes
```

A resposta esperada é `401`, porque a rota exige token.

## 🧪 Como testar

### 1. Crie o banco de testes

Rode uma vez:

```bash
psql -h localhost -p 5432 -U postgres -d postgres -c "CREATE DATABASE frotamais_test_flyway;"
```

O banco começa vazio: as migrations montam o schema na primeira execução da suíte, como no CI.

### 2. Execute os testes

```bash
./mvnw test
```

Para rodar uma classe só:

```bash
./mvnw test -Dtest=SegurancaCaminhaoTest
```

Os testes precisam do PostgreSQL ligado, mas não da API em execução, de `JWT_SECRET` nem de SMTP (o envio de e-mail é capturado em memória).

### Bancos

| | `frotamais_dev_local` (Windows) / `frotamais` (Codespaces) | `frotamais_test_flyway` |
|---|---|---|
| **Uso** | Desenvolvimento, com a API em execução | Somente testes automatizados (nunca para rodar a API) |
| **Dados** | Permanecem | Os testes desfazem o que gravam, com rollback |

A suíte nunca usa os bancos de desenvolvimento:

- A URL do banco de testes é fixa em `src/test/resources/config/application.properties`; `DB_URL` não a altera.
- `GuardaBancoDeTeste` confere em qual banco a conexão chegou, antes do Flyway e do Hibernate. Se for outro, a execução falha.

## 📚 Documentação

- [`docs/API.md`](docs/API.md): endpoints, permissões e códigos de resposta.
- [`docs/MIGRATIONS.md`](docs/MIGRATIONS.md): migrations e adoção do Flyway em bancos existentes.
- [`docs/AMBIENTE_INTEGRACAO_FRONTEND.md`](docs/AMBIENTE_INTEGRACAO_FRONTEND.md): como subir a API para o front-end (banco isolado, proxy do Angular, acesso no Codespaces); coleção em [`docs/frotamais-api.http`](docs/frotamais-api.http).
- [`docs/CONTEXTO_FROTA_MAIS.md`](docs/CONTEXTO_FROTA_MAIS.md): contexto técnico, histórico, roadmap e pendências.

---

**📚 Projeto Integrador · 2026**

*Frota+ — Gestão que move resultados.*
