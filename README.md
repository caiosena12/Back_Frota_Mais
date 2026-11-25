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
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Conexão com o PostgreSQL | obrigatórias, exceto no perfil `dev` |
| `SPRING_PROFILES_ACTIVE` | `dev` para desenvolvimento local; `prod` em produção | o `run-dev-frotamais.sh` usa `dev` |
| `CORS_ORIGENS_PERMITIDAS` | Origens exatas do front-end, separadas por vírgula (nunca `*`) | vazio (nenhuma); obrigatória e só https no `prod` |
| `PORT` | Porta HTTP no perfil `prod` | `8080` |
| `EMAIL_REMETENTE` | Remetente dos convites | vazio (e-mail desligado) |
| `CONVITE_URL_ATIVACAO` | Endereço da tela de ativação; o token vai como parâmetro | vazio (e-mail desligado) |
| `CONVITE_VALIDADE_HORAS` | Validade do convite (1 a 720) | `24` |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | Servidor SMTP | vazio |
| `EMAIL_CAPTURA_LOCAL`, `EMAIL_CAPTURA_DIR` | Só perfil `dev`: grava os e-mails em `contexto-local/emails-dev` em vez de enviar | desligada |
| `JPA_SHOW_SQL` | Mostra o SQL no log | `false` |

- **Perfil `dev`** (`application-dev.properties`): conecta ao `frotamais` local com as credenciais do dev container. Nunca use em produção.
- **Perfil `prod`** (`application-prod.properties`): produção. Recusa subir com banco de teste, de integração ou descartável, sem CORS https ou junto com o `dev`. Health check público em `GET /actuator/health` (só o status). Deploy, Dockerfile e variáveis: [`docs/DEPLOYMENT_PRODUCTION.md`](docs/DEPLOYMENT_PRODUCTION.md); contratos congelados: [`docs/RELEASE_HANDOFF.md`](docs/RELEASE_HANDOFF.md).
- **Sem perfil:** nenhuma credencial tem valor padrão (use `prod` em produção).
- **E-mail:** sem SMTP, remetente e URL de ativação, as operações que enviam convite respondem `503` e não gravam nada. Nenhum serviço de e-mail vem configurado.
- **Captura local de e-mails** (só desenvolvimento): com o perfil `dev` e `EMAIL_CAPTURA_LOCAL=true`, os convites viram arquivos privados em `contexto-local/emails-dev/` e nada é enviado. Fora do `dev` ou junto com SMTP, a API não sobe. Detalhes em [`docs/AMBIENTE_INTEGRACAO_FRONTEND.md`](docs/AMBIENTE_INTEGRACAO_FRONTEND.md).
- **Mensagens de validação:** em português, centralizadas em `ValidationMessages.properties`.

## ▶️ Como executar

O ambiente recomendado é o **GitHub Codespaces**, com Java 21 e PostgreSQL 16 em `localhost:5432`.

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

Em um terminal dedicado:

```bash
./run-dev-frotamais.sh
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

| | `frotamais` | `frotamais_test_flyway` |
|---|---|---|
| **Uso** | Desenvolvimento, com a API em execução | Somente testes automatizados |
| **Dados** | Permanecem | Os testes desfazem o que gravam, com rollback |

A suíte nunca usa `frotamais`:

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
