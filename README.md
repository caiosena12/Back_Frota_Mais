# 🚛 Frota+

### Gestão que move resultados.

O **Frota+** é um sistema web de gestão financeira para caminhoneiros autônomos e pequenas frotas. A proposta é substituir o controle no papel e mostrar o lucro de cada viagem, considerando quilometragem, combustível, pedágios, receitas e despesas.

Este repositório contém a **API REST do back-end**. O front-end em Angular ainda não foi iniciado.

> **Projeto em desenvolvimento.** Hoje a API tem autenticação, separação por empresa e gestão de caminhões. Viagens, abastecimentos, despesas, relatórios e dashboards ainda não existem.

---

## 📌 Situação atual

| Implementado | Planejado, ainda sem código |
|---|---|
| Registro de empresa com o primeiro usuário DONO | Criação e gestão de motoristas |
| Login com JWT | Vínculo entre motorista e caminhão |
| Perfis DONO e MOTORISTA | Viagens e trechos |
| Separação de dados por empresa em caminhões | Abastecimentos de diesel e Arla |
| Cadastro, consulta, alteração e exclusão de caminhões | Despesas e manutenções |
| Testes automatizados em banco exclusivo | Análise financeira, dashboards e relatórios |
| | Front-end em Angular |

**Limitações conhecidas:**

- Ainda não há como criar um usuário MOTORISTA pela API.
- Todo usuário autenticado vê todos os caminhões da própria empresa. A regra de o motorista ver só os caminhões dele depende do vínculo, que ainda não existe.
- O e-mail é único em todo o sistema, embora a regra de negócio permita usar o mesmo e-mail em empresas diferentes.

## 🛠️ Tecnologias

| Tecnologia | Uso | Situação |
|---|---|---|
| **Java 21** | Linguagem do back-end | Em uso |
| **Spring Boot 4.1.1** | API REST | Em uso |
| **Spring Data JPA** | Persistência | Em uso |
| **PostgreSQL 16** | Banco de dados | Em uso |
| **Spring Security + JWT** | Autenticação e autorização | Em uso |
| **JUnit e MockMvc** | Testes automatizados | Em uso |
| **Angular** | Interface web | Planejado |

## 🏗️ Arquitetura

Monólito Spring Boot organizado por funcionalidade, em `br.com.frotamais`:

| Pacote | Responsabilidade |
|---|---|
| `auth` | Registro, login e tratamento de erros de validação |
| `caminhao` | Gestão de caminhões |
| `empresa` | Empresas |
| `seguranca` | Configuração do Spring Security e dos tokens |
| `usuario` | Usuários e perfis |

Cada funcionalidade segue o fluxo **Controller → Service → Repository → banco**, com entidades JPA separadas dos DTOs de entrada e saída.

O schema do banco é gerado pelo Hibernate (`ddl-auto=update`). Ainda não há migrations.

## 🔐 Segurança

### Autenticação

- As senhas são guardadas com BCrypt.
- O login devolve um JWT HS256, válido por 8 horas por padrão (`JWT_HORAS`, de 1 a 24).
- A chave vem da variável `JWT_SECRET`, em Base64, com pelo menos 32 bytes. Sem ela, a API não sobe.
- Rotas protegidas exigem o cabeçalho `Authorization: Bearer <token>`.

A cada requisição, a API valida a assinatura, a validade e o emissor do token, e também consulta o usuário no banco. Ele precisa estar ativo e pertencer à empresa indicada no token. O perfil usado na autorização vem **do banco**, então desativar um usuário ou mudar o perfil dele vale na hora, mesmo com um token já emitido.

### Permissões

| Rota | Acesso |
|---|---|
| `POST /api/auth/registro` | Público |
| `POST /api/auth/login` | Público |
| `GET /api/caminhoes` | Usuário autenticado |
| `GET /api/caminhoes/{id}` | Usuário autenticado |
| `POST /api/caminhoes` | Somente DONO |
| `PUT /api/caminhoes/{id}` | Somente DONO |
| `DELETE /api/caminhoes/{id}` | Somente DONO |

O registro público sempre cria uma empresa nova e o usuário DONO dela. O cliente não escolhe o perfil.

### Isolamento por empresa

Em caminhões, a empresa nunca vem do corpo da requisição: ela sai do usuário autenticado. Um caminhão de outra empresa responde `404`, como se não existisse.

Essa proteção é aplicada consulta a consulta, nos métodos do repositório de caminhões. Toda funcionalidade nova precisa fazer o mesmo e comprovar isso com testes.

## ▶️ Como executar

O ambiente recomendado é o **GitHub Codespaces**. O dev container já traz Java 21 e PostgreSQL 16, com o banco de desenvolvimento `frotamais` em `localhost:5432`.

Fora dele, são necessários Java 21 e PostgreSQL 16. As variáveis `DB_URL`, `DB_USER` e `DB_PASSWORD` configuram a conexão.

### 1. Crie a chave JWT

Rode uma vez. Se o arquivo já existir, o comando não faz nada:

```bash
[ -e .frotamais-jwt.env ] || printf 'JWT_SECRET=%s\n' "$(openssl rand -base64 32)" > .frotamais-jwt.env
chmod 600 .frotamais-jwt.env
```

O arquivo `.frotamais-jwt.env` é ignorado pelo Git. Nunca o publique.

### 2. Inicie a API

Em um terminal dedicado:

```bash
./run-dev-frotamais.sh
```

A API sobe na porta `8080`. Em outro terminal, confira:

```bash
curl -i http://localhost:8080/api/caminhoes
```

A resposta esperada é `401`, porque a rota exige token.

## 🧪 Como testar

### 1. Crie o banco de testes

Rode uma vez. O `psql` vai pedir a senha do PostgreSQL definida no dev container:

```bash
psql -h localhost -p 5432 -U postgres -d postgres -c "CREATE DATABASE frotamais_test;"
```

O schema é criado na primeira execução da suíte.

### 2. Execute os testes

```bash
./mvnw test
```

Para rodar uma classe só:

```bash
./mvnw test -Dtest=SegurancaCaminhaoTest
```

Os testes precisam do PostgreSQL ligado, mas não da API em execução nem de `JWT_SECRET`.

### Banco de desenvolvimento e banco de testes

| | `frotamais` | `frotamais_test` |
|---|---|---|
| **Uso** | Desenvolvimento, com a API em execução | Somente testes automatizados |
| **Criação** | Automática, pelo dev container | Manual, uma vez |
| **Dados** | Permanecem | Os testes desfazem o que gravam, com rollback |

A suíte nunca deve usar `frotamais`, e não há fallback:

- A URL do banco de testes é fixa em `src/test/resources/config/application.properties`, então `DB_URL` não a altera.
- `GuardaBancoDeTeste` pergunta ao PostgreSQL em qual banco a conexão chegou, antes de o Hibernate agir. Se for outro banco, ou se `frotamais_test` não existir ou estiver indisponível, a execução falha.

Os dois bancos ficam no mesmo servidor e usam o mesmo usuário.

## 📚 Documentação

Regras de negócio, decisões, roadmap e pendências estão em [`docs/CONTEXTO_FROTA_MAIS.md`](docs/CONTEXTO_FROTA_MAIS.md).

---

**📚 Projeto Integrador · 2026**

*Frota+ — Gestão que move resultados.*
