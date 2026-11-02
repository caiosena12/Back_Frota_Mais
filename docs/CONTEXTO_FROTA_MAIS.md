# Contexto técnico e regras de negócio do Frota+

**Última revisão:** 02/11/2026, com base no commit `d0797b7`.
**Repositório do back-end:** `https://github.com/caiosena12/Back_Frota_Mais` (no Codespaces: `/workspaces/Back_Frota_Mais`).

---

## 0. Leia primeiro

### 0.1 Como usar este documento

- Ele registra o que foi decidido e o estado conhecido do projeto. **O código e o Git são a fonte de verdade sobre o que existe.** Se algo aqui divergir deles, reporte a divergência; não altere o código para coincidir com o documento.
- As **regras de negócio confirmadas** são a fonte de verdade sobre o que deve existir. Não as substitua por sugestões.
- Antes de qualquer alteração: rode `git status`, `git diff` e `git log --oneline -8`, leia os arquivos envolvidos e apresente um diagnóstico.
- Este documento é **público**. Nunca inclua senhas, tokens JWT, chaves, conteúdo de `.frotamais-jwt.env`, dados pessoais ou informações comerciais privadas.

### 0.2 Legenda

| Marca | Significado |
|---|---|
| **[CONFIRMADO]** | Decidido ou aprovado pelo responsável pelo produto. |
| **[IMPLEMENTADO]** | Existe no código, verificado por leitura do repositório. |
| **[PROPOSTO]** | Sugestão ainda não confirmada. Não tratar como regra. |
| **[PENDENTE]** | Depende de decisão. Perguntar antes de modelar ou implementar. |
| **[IDEIA FUTURA]** | Possibilidade sem decisão nem prazo. |
| **[NÃO VERIFICADO]** | Relatado, sem evidência reproduzida. |

### 0.3 Limites da verificação

Build completo e testes **não foram executados** nesta revisão. Os testes foram executados depois, em 02/11/2026, no banco exclusivo `frotamais_test` (seção 6.3). O `pom.xml` foi verificado com `mvn validate`, sobre cópias, fora do repositório. Resultados de testes citados vêm de relatos do responsável pelo produto.

---

## 1. Visão do produto

### 1.1 O que é

- **Frota+** é um sistema web de **gestão financeira de transporte rodoviário de cargas**: controla viagens, quilometragem, combustível, pedágios, receitas e despesas, e mostra o **lucro de cada viagem**. **[CONFIRMADO]**
- **Slogan oficial:** "Gestão que move resultados." **[CONFIRMADO]**
- Nome anterior: **FleetControl**. Os protótipos do Figma ainda mostram esse nome e precisam ser trocados. **[CONFIRMADO]**
- Existiu uma versão anterior em outra stack, com dashboard, histórico, financeiro, viagens, custos, pedágios e conceitos de lucro, margem, km vazio, consumo e custo por km, testada com viagens reais. O projeto atual é uma **reconstrução** em Java/Spring Boot e Angular. **Não propor migração do front-end anterior sem pedido explícito.** **[CONFIRMADO]**

### 1.2 Público-alvo

- Caminhoneiros autônomos e donos de pequenas frotas. **[CONFIRMADO]**
- O sistema é para **qualquer caminhoneiro** e **não controla** os hábitos de cada um (por exemplo, encher o tanque ou abastecer valores fechados). **[CONFIRMADO]**
- Dois tipos de usuário: **DONO** e **MOTORISTA** (seção 2.7). **[CONFIRMADO]**

### 1.3 Problema que resolve

- Controle da frota feito no papel e falta de visão de **lucro por viagem**. **[CONFIRMADO]**
- Nem todas as viagens são registradas, por falta de tempo. **[CONFIRMADO como fato]** Implicação: o lançamento precisa ser rápido, com poucos campos e pensado para celular. **[PROPOSTO]**
- O combustível é difícil de ratear: o abastecimento nem sempre acontece antes da viagem, e **não se sabe quanto resta no tanque**. **[CONFIRMADO]**
- Trechos **vazios** geram custo sem receita e precisam entrar no cálculo. **[CONFIRMADO]**
- Dificuldade de enxergar resultados por **semana, mês e ano**. **[CONFIRMADO]**

### 1.4 Funcionalidades do produto

Implementadas: **autenticação**, **multiempresa** e **gestão de caminhões**, esta última com a restrição por motorista ainda pendente.

Planejadas, **ainda sem nenhum código**:
- Controle de viagens e trechos.
- Abastecimentos de diesel e Arla, com cálculo de consumo.
- Despesas: pedágios e outros custos.
- Manutenções.
- Análise financeira: lucro, prejuízo e custo por km.
- Dashboards e relatórios por semana, mês e ano.
- Criação e gestão de motoristas.

**[IDEIA FUTURA]:** exportação em PDF/Excel; análise de km vazio entre fretes.

---

## 2. Regras de negócio

### 2.1 Viagens e trechos

- **[CONFIRMADO]** **A viagem é o conjunto da operação** e pode ser composta por **vários trechos**: ida, volta e outros.
- **[CONFIRMADO]** Exemplo: `Palmital → Santos` e `Santos → Palmital` podem representar **uma única viagem concluída**.
- **[CONFIRMADO]** Cada trecho tem suas próprias informações, especialmente **data, quilometragem e situação da carga: carregado ou vazio**.
- **[CONFIRMADO]** **A volta não é automaticamente vazia.** A situação de cada trecho é registrada individualmente.
- **[CONFIRMADO]** Um trecho **não é** obrigatoriamente uma viagem independente.
- **[CONFIRMADO]** A viagem tem **início** e **finalização**, com status **"Em andamento"** e **"Concluída"** (nomes do protótipo).
- **[CONFIRMADO]** Fluxo de tela: **"+ Nova Viagem"** abre um **pop-up com os dados mais básicos**. Depois, no menu **Ações** da viagem, o usuário adiciona **abastecimento, pedágios, frete e custos extras**.
- **[CONFIRMADO]** Campos da viagem: caminhão, **motorista**, rota, data de início, km total, **frete**, pedágios, custos extras. Lista do protótipo: Caminhão, Rota, Data Início, Km Total, Status, Ações.
- **[CONFIRMADO]** **Quem edita:** o motorista edita o que lançou **enquanto a viagem está em andamento**. Depois disso, **só o dono** corrige. O dono pode alterar qualquer dado.
- **[PROPOSTO]** Validações: só uma viagem em andamento por caminhão; o motorista pertence à empresa e está vinculado ao caminhão; frete não negativo.
- **[PROPOSTO]** Status "Cancelada" em vez de excluir, e registro de quem criou e alterou cada lançamento.

**Ponto para revisão:** a regra antiga "cada viagem corresponde a um frete" foi **substituída** pela regra de trechos. Falta definir como frete e receita se relacionam com os trechos (Anexo A, P3).

### 2.2 Quilometragem carregada e vazia

- **[CONFIRMADO]** O sistema deve identificar os **quilômetros percorridos carregado e vazio**, respeitando os dados de cada trecho.
- **[CONFIRMADO]** O deslocamento vazio até o local de carga faz parte da viagem e deve ser registrado.
- **[CONFIRMADO]** Os relatórios mostram **Km Vazio** e **Rodado Vazio (R$)**.
- **[PROPOSTO]** Indicador de **% de km rodados vazios** no período.
- **[PROPOSTO, a revisar]** Cálculo por três odômetros (saída, carregamento e final). Ele foi pensado para uma viagem de um único carregamento e **pode não servir** para trechos (Anexo A, P4).
- **[PROPOSTO, a revisar]** Custo do rodado vazio = `km vazio ÷ consumo médio × preço médio do litro`. Fórmula não confirmada (Anexo A, P5).

### 2.3 Combustível e abastecimentos

- **[CONFIRMADO]** Dois combustíveis: **diesel e Arla**.
- **[CONFIRMADO]** É possível registrar **vários abastecimentos parciais durante uma mesma viagem**.
- **[CONFIRMADO]** O motorista **não é obrigado a completar o tanque** em cada abastecimento.
- **[CONFIRMADO]** Cada abastecimento permite registrar **os litros e o valor gasto**.
- **[CONFIRMADO]** A viagem preserva as informações de **combustível inicial, abastecimentos e combustível final**.
- **[CONFIRMADO]** **Não existe consumo fixo por caminhão.** O consumo é calculado conforme os dados reais da viagem. A capacidade do tanque é atributo do caminhão.
- **[CONFIRMADO]** O relatório mostra **Consumo Médio (km/L)**.
- **[CONFIRMADO]** O sistema atende quem **enche o tanque** e quem abastece **valores fechados**.
- **[CONFIRMADO]** O abastecimento é adicionado pelo menu **Ações** da viagem.

Não criar regras adicionais de preço ou cálculo por abastecimento sem aprovação.

**Pontos para revisão, sem substituir as regras acima:**
1. Combustível inicial e final convivem com o fato confirmado de que **nem sempre se sabe quanto resta no tanque**. Falta definir como esses valores são obtidos (Anexo A, P1).
2. Também está confirmado que o abastecimento **nem sempre acontece antes da viagem**, e não está decidido onde registrar um abastecimento feito fora de viagem (Anexo A, P2).

**[PROPOSTO, não adotado]** Em conversas anteriores surgiu um modelo de abastecimento ligado ao caminhão, com odômetro, marcação de tanque cheio e consumo estimado em janelas de registros. Ele não foi aprovado e não deve guiar a modelagem.

### 2.4 Pedágios

- **[CONFIRMADO]** Pedágios são **custo da viagem** e entram pelas **Ações** da viagem.
- **[CONFIRMADO como objetivo]** Controle com **múltiplas praças, localidade e valor**.
- **[PENDENTE]** Categoria/quantidade de eixos e descontos de tag. **Não modelar sem confirmação** (Anexo A, P6).
- **[PROPOSTO]** Pedágios e custos extras na mesma tabela de despesas, com uma categoria.

### 2.5 Fretes, receitas, despesas e rentabilidade

- **[CONFIRMADO]** O **valor do frete é receita**, e **o motorista pode vê-lo**: muitas vezes o frete cai na conta dele como adiantamento, e depois ele repassa ao dono já descontando a comissão (geralmente 10%, varia por empresa).
- **[CONFIRMADO]** **A comissão do motorista NÃO entra na conta.** O sistema não calcula comissão.
- **[CONFIRMADO]** Custos considerados: **combustível, Arla, pedágios, custos extras e manutenção**.
- **[CONFIRMADO]** **Seguro e parcelas do caminhão NÃO entram.**
- **[CONFIRMADO]** **Manutenção** é uma tela de **histórico**, **não ligada a uma viagem**. O valor é **abatido da receita total do mês**. Há meses sem manutenção e meses com várias.
- **[CONFIRMADO, protótipo]** Indicadores: Receita Total, Custos Totais, Lucro Líquido/Total, **Margem Média**, custo e lucro por km, Km Vazio, Rodado Vazio (R$), Consumo Médio.
- **[CONFIRMADO]** Os números dos protótipos são **exemplos**, não dados reais.
- **[PROPOSTO]** Lucro da viagem = `frete − combustível − Arla − pedágios − extras`. Manutenção entra no resultado **mensal**, sem rateio. Margem = lucro ÷ receita.
- **[PROPOSTO]** Valores em `BigDecimal`/`numeric(15,2)`, nunca `double`; períodos no fuso `America/Sao_Paulo`.
- **[PROPOSTO]** Renomear a coluna "R$/Km" para "Lucro/km" (Anexo A, P10).
- **[IDEIA FUTURA]** Tela de "acerto de contas".

### 2.6 Cadastro de empresas e usuários

- **[CONFIRMADO]** **Um mesmo e-mail pode estar associado a empresas diferentes.** O e-mail **não** é necessariamente único no sistema.
- **[CONFIRMADO]** **O CNPJ não é obrigatório** para cadastrar uma empresa. Não criar validações ou restrições adicionais sem aprovação.
- **[CONFIRMADO]** O registro público cria uma empresa e seu primeiro usuário DONO. O cliente nunca escolhe o perfil.

**Lacuna no código:** o modelo atual **impede** a primeira regra (seção 4.5). A entidade `Empresa` não tem CNPJ, portanto a segunda regra já é atendida.

### 2.7 Caminhões e motoristas

**Caminhão**

- **[CONFIRMADO]** Campos: **placa, modelo, ano, status (Ativo/Inativo), capacidade do tanque (litros)**. O apelido foi removido.
- **[IMPLEMENTADO]** Placa normalizada (trim + maiúsculas) e **única por empresa**.
- **[PROPOSTO]** "Caminhões ativos" do dashboard calculado pelo status.

**Motoristas**

- **[CONFIRMADO]** **Não haverá tela específica de motoristas.**
- **[CONFIRMADO]** O motorista é **vinculado ao caminhão**, o **login é gerado pela tela de Caminhões** e, **ao retirá-lo do caminhão, o login é desativado**.
- **[CONFIRMADO]** **Desativar, nunca apagar.** Se o motorista ainda tiver outro caminhão, o acesso **continua**.
- **[CONFIRMADO]** O pop-up "Adicionar motorista" permite **vincular um existente** ou **criar um novo**. O dono cadastra nome e e-mail e define o acesso inicial.
- **[CONFIRMADO]** Normalmente é **um motorista por caminhão**, com **exceções**: o vínculo deve permitir mais de um.
- **[CONFIRMADO]** A troca de senha é **opcional**. O **dono pode redefinir** a senha do motorista.
- **[CONFIRMADO]** **O motorista só visualiza os caminhões vinculados a ele.** Não visualiza caminhões de outros motoristas, mesmo da mesma empresa. A associação deve ser respeitada nas consultas e nas operações da API.
- **[CONFIRMADO]** O motorista pode lançar os dados das viagens.

**Lacuna no código:** não existe associação entre motorista e caminhão, nem forma de criar MOTORISTA. Hoje, qualquer usuário autenticado lê todos os caminhões da empresa (seção 4.6).

**Permissões [CONFIRMADO]**

| Ação | DONO | MOTORISTA |
|---|---|---|
| Ver caminhões | todos da empresa | **somente os vinculados a ele** |
| Dashboards e relatórios financeiros | sim | **não** |
| Histórico de viagens, km, pedágios, custos extras e combustível | sim | sim |
| Valor do frete | sim | sim |
| Lançar viagens, abastecimentos e despesas | sim | sim |
| Editar lançamentos | todos, sempre | os próprios, com a viagem em andamento |
| Criar, alterar e excluir caminhões | sim | **não** |

**[PROPOSTO]** Dono como opção de "motorista" nas viagens. **Menu:** dono: Dashboard, Caminhões, Viagens, Manutenções e Relatórios; motorista: Viagens. Um menu de Abastecimentos depende do Anexo A, P2.

### 2.8 Relatórios e dashboard

- **[CONFIRMADO]** O **dashboard** aparece **por padrão para o dono**, com **lucro total** e **filtro por ano, mês e semana**.
- **[CONFIRMADO, protótipo]** Dashboard: cards **Receita Total, Custos Totais, Lucro Líquido, Caminhões Ativos**; gráfico de barras **Receitas vs Custos**; gráfico de rosca **Custos**.
- **[CONFIRMADO]** **Relatórios são a última parte do desenvolvimento**, depois que tudo estiver calculado.
- **[CONFIRMADO, protótipo]** Relatórios: cards Receita Total, Custos Totais, Lucro Total, Margem Média, **Rodado Vazio (R$)**, **Km Vazio** e **Consumo Médio**; tabela **Performance por Caminhão** (viagens, km total, receita, custos, lucro, margem, R$/km); tabela **Rentabilidade por Viagem** (caminhão, rota, data, receita, custos, lucro, margem).
- **[CONFIRMADO]** O motorista **não** vê dashboards financeiros.
- **[PROPOSTO]** Rosca de custos: combustível, Arla, pedágios, manutenção e extras.
- **[IDEIA FUTURA]** Exportação em PDF/Excel.

---

## 3. Decisões de produto

### 3.1 Escolhidas [CONFIRMADO]

- Viagem como conjunto da operação, composta por **trechos**, com início e finalização.
- Vários abastecimentos parciais por viagem, com combustível inicial e final.
- Km carregado e vazio identificados por trecho.
- **Manutenções** como histórico por caminhão, abatido no mês.
- Dashboard do dono; Relatórios por último.
- Perfis **DONO** e **MOTORISTA**; motorista vinculado ao caminhão, vendo só os seus.
- Mesmo e-mail permitido em empresas diferentes; CNPJ não obrigatório.
- **Frete visível ao motorista.**
- Site **responsivo para celular**, exigindo internet.
- Sistema **multiempresa**, com dados isolados por empresa.

### 3.2 Descartadas [CONFIRMADO]

- Comissão do motorista no cálculo.
- Seguro e parcelas do caminhão.
- Tela de Motoristas ou de "Equipe".
- Troca de senha obrigatória.
- Apelido do caminhão.
- Consumo fixo por caminhão.
- App nativo e modo offline no MVP.
- Migração do front-end anterior sem pedido.

### 3.3 Restrições de trabalho [CONFIRMADO]

- Não colocar senhas, tokens ou segredos em conversas, commits ou documentos.
- Não expor a porta 5432 publicamente só para testar a API.
- Não iniciar **viagens antes de consolidar a base**: autenticação, controle de acesso, isolamento e testes.

### 3.4 Não alterar sem autorização

1. **Segurança:** não enfraquecer a validação do JWT (assinatura, expiração, emissor), a conferência de usuário ativo, a comparação do `empresaId` do token com o do banco nem os `@PreAuthorize`. **Não desabilitar segurança para fazer testes passarem.**
2. **`empresaId` sempre vem do usuário autenticado**, nunca do corpo da requisição.
3. **Credenciais e dados:** não alterar senhas, não recriar o PostgreSQL, não apagar dados nem mudar a autenticação do banco sem pedido.
4. **Segredos:** `.frotamais-jwt.env` fica fora do Git; não imprimir nem copiar o conteúdo; não confundir `JWT_SECRET` com a senha do PostgreSQL.
5. **Stack:** Java + Spring Boot, Angular e PostgreSQL.
6. **Regras de negócio confirmadas** da seção 2.
7. **Permissões** dos perfis: não alterar por conta própria.
8. **Não sobrescrever código existente sem revisar `git diff` e os arquivos atuais.**

### 3.5 Propostas sem decisão (não tratar como regra)

- Status "Cancelada", auditoria de alterações e `@Version`.
- Validações de viagem da seção 2.1.
- Tela de acerto de contas; renomear "R$/Km".
- Flyway com `ddl-auto=validate`; CI no GitHub Actions; banco e perfil exclusivos de teste.
- Proxy do Angular em vez de CORS.
- Rate limiting: tratar antes de a API ficar pública (seção 6.6).

---

## 4. Arquitetura e tecnologias

### 4.1 Stack

| Camada | Tecnologia | Situação |
|---|---|---|
| Linguagem | Java 21 | **[IMPLEMENTADO]** |
| Framework | Spring Boot **4.1.1** (Maven 3.10.0 via wrapper) | **[IMPLEMENTADO]** |
| Persistência | Spring Data JPA / Hibernate 7.4.5 | **[IMPLEMENTADO]** |
| Segurança | Spring Security 7.1.1 (OAuth2 Resource Server + Nimbus) | **[IMPLEMENTADO]** |
| Validação | Jakarta Validation | **[IMPLEMENTADO]** |
| Banco | PostgreSQL **16** | **[IMPLEMENTADO]** |
| Testes | **JUnit 6.0.3**, Spring Boot Test, MockMvc, `spring-security-test` | **[IMPLEMENTADO]** |
| Front-end | **Angular** | **[CONFIRMADO]**, não iniciado |
| Protótipos | Figma | **[CONFIRMADO]** |

As versões de Hibernate, Spring Security e JUnit são as gerenciadas pelo Spring Boot 4.1.1, sem sobrescrita no `pom.xml`.

Observações do Spring Boot 4: o **Jackson é 3.x** (pacote `tools.jackson.databind`), e `@AutoConfigureMockMvc` fica em `org.springframework.boot.webmvc.test.autoconfigure`. **Não trocar esses imports sem checar a versão efetiva.**

### 4.2 Estrutura do repositório

```text
Back_Frota_Mais/
├── .devcontainer/                 # app (Java 21) + db (postgres:16)
├── docs/CONTEXTO_FROTA_MAIS.md    # este documento
├── contexto-local/                # privado, ignorado pelo Git, não existe em outros clones
├── run-dev-frotamais.sh           # carrega .frotamais-jwt.env e roda spring-boot:run (Bash)
├── pom.xml, README.md, mvnw, mvnw.cmd, .mvn/wrapper
└── src/
    ├── main/java/br/com/frotamais/
    │   ├── BackendApplication.java
    │   ├── auth/       (AuthController, AuthService, LoginRequest, RegistroRequest, TokenResponse, ApiExceptionHandler)
    │   ├── caminhao/   (Caminhao, StatusCaminhao, CaminhaoRepository, CaminhaoRequest, CaminhaoResponse, CaminhaoService, CaminhaoController)
    │   ├── empresa/    (Empresa, EmpresaRepository)
    │   ├── seguranca/  (SecurityConfig, TokenService, UsuarioLogado)
    │   └── usuario/    (Perfil, Usuario, UsuarioRepository)
    ├── main/resources/application.properties
    └── test/java/br/com/frotamais/
        ├── BackendApplicationTests.java
        └── seguranca/  (SegurancaCaminhaoTest, IsolamentoEmpresaCaminhaoTest)
```

- **Padrão de cada funcionalidade:** pacote por funcionalidade; **Controller → Service → Repository → banco**; entidade JPA separada de **DTOs** (`XRequest`/`XResponse`, como `record`); nomes de domínio em português.
- O Spring Boot fica na raiz. O local do Angular está **[PENDENTE]**.
- **Ambientes [CONFIRMADO]:**
  - **Principal:** GitHub Codespaces (Linux, Java 21), para desenvolvimento, execução do backend, build e testes.
  - **Auxiliar:** notebook Windows, para usar o Claude Code, ler documentos, planejar e alterar arquivos quando necessário. Ele tem Java 17, e o projeto exige Java 21, então não se deve presumir que execute ou teste o backend. Não atualizar o Java, não instalar PostgreSQL, não configurar credenciais e não preparar esse ambiente.
  - **Sincronização:** GitHub.
  - Build e testes são feitos no Codespaces, respeitando os cuidados com o banco (seção 6.3).

### 4.3 Banco de dados

- PostgreSQL 16 roda como serviço `db` do Docker Compose do dev container, com dados no volume `postgres-data`. Responde em `localhost:5432` dentro do Codespace. **[IMPLEMENTADO]**
- Banco `frotamais`, usuário `postgres`. Os valores de senha **não** são reproduzidos aqui. `DB_URL`, `DB_USER` e `DB_PASSWORD` só têm valores padrão no `application.properties`.
- `ddl-auto=update` e `show-sql=true` estão ativos, **sem migrations**. **[PROPOSTO]** Flyway com `ddl-auto=validate`.
- Ao consultar `usuarios`, **não selecionar `senha_hash`**.

**Modelo de dados:**

| Tabela | Campos principais | Situação |
|---|---|---|
| `empresas` | id, nome, criada_em | [IMPLEMENTADO] |
| `usuarios` | id, nome, e-mail **único global**, senha_hash, perfil, ativo, empresa_id | [IMPLEMENTADO], unicidade **a mudar** (seção 4.5) |
| `caminhoes` | id, empresa_id, placa, modelo, ano, status, capacidade_tanque_litros; único (empresa_id, placa) | [IMPLEMENTADO] |
| vínculo motorista–caminhão | muitos-para-muitos, com ativo/inativo | [CONFIRMADO] como necessidade; desenho [PENDENTE] |
| `viagens` | empresa_id, caminhão, motorista, início/fim, status, combustível inicial e final | [PROPOSTO] |
| `trechos` | viagem, origem, destino, data, quilometragem, situação da carga | [CONFIRMADO] como conceito; colunas [PROPOSTO] |
| `abastecimentos` | empresa_id, viagem, data, litros, valor, tipo diesel/Arla | [CONFIRMADO] como conceito; demais vínculos [PENDENTE] |
| despesas da viagem | viagem, categoria (pedágio/extra), descrição, valor | [PROPOSTO] |
| `manutencoes` | empresa_id, caminhão, data, descrição, valor | [PROPOSTO] |
| (todas) | Isolamento por empresa em todas as consultas e operações, com vínculo direto à empresa ou por relacionamento com outra entidade que tenha esse vínculo. Os campos listados acima não tornam `empresa_id` obrigatório em toda entidade | [CONFIRMADO] |

### 4.4 Autenticação [IMPLEMENTADO]

- `POST /api/auth/registro`: recebe `nomeEmpresa` (≤120), `nome` (≤120), `email` (≤160), `senha` (8 a 72 caracteres e ≤72 bytes UTF-8). Cria a **empresa** e o usuário **DONO** com **BCrypt** e responde `201` com `{token, nome, perfil}`. Hoje, e-mail já existente responde `409`, em qualquer empresa, o que diverge da regra da seção 2.6.
- `POST /api/auth/login`: e-mail normalizado (`trim` + minúsculas, `Locale.ROOT`), só usuário ativo. Sucesso: `200` com `{token, nome, perfil}`.
  - E-mail inexistente, usuário inativo ou senha errada: `401` com a razão "E-mail ou senha inválidos."
  - Senha acima de 72 bytes: `401` com **outra** razão, "A senha ultrapassa o limite permitido pelo BCrypt." (`AuthService.java:81`).
  - As razões são definidas em `ResponseStatusException`. O padrão do Spring Boot (`spring.web.error.include-message=never`) indica que elas **não chegam ao corpo da resposta**. **[NÃO VERIFICADO em execução]**
  - A busca usa `findByEmail`, que retorna no máximo um usuário e depende do e-mail único global.
- **JWT HS256**: `iss=frota-mais`, `sub` = id do usuário, `empresaId`, `perfil`, `iat`, `exp`. Validade por `app.jwt.horas` (padrão 8, de 1 a 24).
- **Segredo:** `app.jwt.secret` vem de `JWT_SECRET`, em Base64, com mínimo de 32 bytes. A aplicação não sobe sem ele.
- O conversor **consulta o usuário no banco a cada requisição**: exige usuário ativo, compara o `empresaId` do token com o do banco e monta `ROLE_DONO` ou `ROLE_MOTORISTA` **a partir do banco**.
- Rotas públicas: `POST /api/auth/registro`, `POST /api/auth/login` e `/error`. O resto exige token. Sessão stateless. CSRF desativado porque a API usa Bearer no header.
- `@EnableMethodSecurity` ativo; `UsuarioLogado` expõe `usuarioId()` e `empresaId()`, lidos do JWT.

### 4.5 Mesmo e-mail em empresas diferentes: lacuna

**[CONFIRMADO]** como regra e **não suportado** pelo código:

- `Usuario.email` tem `unique = true`, e o banco bloqueia o segundo cadastro.
- `AuthService.registrar` rejeita e-mail existente com `409`.
- `findByEmail` retorna `Optional`. Com dois usuários de mesmo e-mail, a consulta falharia.
- O login recebe só e-mail e senha, sem informação para identificar a empresa.
- Remover `unique = true` da entidade **não remove** a restrição do banco: `ddl-auto=update` não apaga constraints.

A busca do login precisa considerar a identidade do usuário sem pressupor e-mail globalmente único. Modelo em decisão: Anexo A, P11.

### 4.6 Isolamento por empresa

- **[CONFIRMADO]** Isolamento por empresa é **regra fundamental**. Um usuário não pode acessar dados de outra empresa sem autorização.
- **[IMPLEMENTADO]** A filtragem por empresa está confirmada no **`CaminhaoRepository`** (`findAllByEmpresaId`, `findByIdAndEmpresaId`, `existsByEmpresaIdAndPlaca`). O `CaminhaoService` obtém o `empresaId` de `UsuarioLogado`, nunca do JSON. Recurso de outra empresa responde `404`.
- **Não é garantia geral.** `UsuarioRepository` e `EmpresaRepository` não filtram por empresa, e todo `JpaRepository` herda `findById`, `findAll` e `deleteById` sem filtro. O isolamento depende de cada serviço usar os métodos corretos.
- **Toda funcionalidade nova deve garantir isolamento por empresa em todas as consultas e operações.** Não é obrigatório que toda entidade tenha a coluna `empresa_id`: o isolamento pode vir do vínculo direto com a empresa ou do relacionamento com outra entidade que tenha esse vínculo, desde que seja verificável e testado.

### 4.7 Segurança

Implementado: BCrypt; validação de assinatura, tempo e emissor do JWT; verificação do usuário ativo e da empresa a cada requisição; perfis lidos do banco; `@PreAuthorize("hasRole('DONO')")` em POST, PUT e DELETE de caminhões; DTOs sem entidade `Empresa` nem hash.

Pontos de atenção (**[PROPOSTO]** correções, nenhuma feita):

- `GET /api/caminhoes` e `GET /api/caminhoes/{id}` não respeitam a regra do motorista (seção 2.7).
- `ApiExceptionHandler` transforma **toda** `DataIntegrityViolationException` em `409` **sem registrar a causa**, o que já escondeu um bug da coluna `ano`.
- Formatos de erro diferentes entre o handler e o `/error`; `401`/`403` sem corpo JSON padronizado.
- Placa sem validação de formato: `ABC-1234` e `ABC1234` contam como placas diferentes.
- PUT de caminhão sem `status` volta o caminhão para `ATIVO`.
- `toUpperCase()` sem `Locale.ROOT` em `CaminhaoService`.
- Registro e login sem limite de tentativas; login sem equalização de tempo para e-mail inexistente.
- `DELETE` de caminhão apaga a linha do banco (Anexo A, P8).
- `DB_PASSWORD` tem valor padrão; não há perfis `dev`/`prod`.
- Não existe forma de criar MOTORISTA.

### 4.8 Front-end e integrações

- **Angular**, **responsivo**, foco em celular, exigindo internet. **[CONFIRMADO]** Há protótipos de 4 telas (Dashboard, Caminhões, Viagens, Relatórios). Faltam Login, pop-up "Nova viagem", menu de Ações, formulário de caminhão, Manutenções e trechos.
- **CORS e proxy:** o código **não tem** configuração de CORS, e o `@CrossOrigin` foi removido do controller. **[PROPOSTO, não implementado]** Usar o proxy do Angular (`proxy.conf.json`, `/api` → `http://localhost:8080`, `ng serve --host 0.0.0.0`), porque no Codespaces a URL encaminhada muda. Sem proxy, o navegador será bloqueado.
- **Integrações:** nenhuma confirmada.

---

## 5. Histórico do desenvolvimento

### 5.1 Linha do tempo

| Data | Commit | O quê |
|---|---|---|
| 22/10 | `4f5b41e`, `c158aa2` | Commit inicial e README |
| 22/10 | `926d98e`, `51ec95f` | Dev container e Docker Compose |
| 23/10 | `d722a4c` | `.devcontainer` movido para a pasta correta |
| 23/10 | `b11a59b`, `dea8899` | Dependências e primeira versão de `Caminhao` (apelido e placa única global) |
| 28/10 | `c9b21ea` | Dependências de segurança |
| 28/10 | `5af35d7` | Entidades e repositórios de `Empresa` e `Usuario` |
| 28/10 | `ff60fa1` | Cadastro e autenticação JWT |
| 28/10 | `9956a93` | Caminhões por empresa (ano, status, isolamento, `@PreAuthorize`) |
| 28/10 | `631358f` | Testes de segurança e isolamento; ajustes no `pom.xml` |
| 30/10 | (sem commit) | Relato: **5 testes, 0 falhas, BUILD SUCCESS** **[NÃO VERIFICADO]** |
| 02/11 | `d0797b7` | Chave exclusiva de teste em `BackendApplicationTests` |

### 5.2 Problemas encontrados e soluções

| Problema | Solução / situação |
|---|---|
| `devcontainer.json` e `docker-compose.yml` soltos na raiz | Movidos para `.devcontainer/` |
| "Rebuild Container" inexistente | Recarregar a janela ou criar um Codespace novo |
| `apt-get update` falhava por chave expirada do repositório do Yarn | Remover `/etc/apt/sources.list.d/yarn.list` e reinstalar o cliente |
| Colunas novas em `caminhoes` | Schema de desenvolvimento recriado com `DROP SCHEMA public CASCADE; CREATE SCHEMA public;` |
| `POST /api/caminhoes` retornou `409` por `ano` nulo | `mvn clean package` e reinício resolveram. **Causa raiz não comprovada.** |
| Porta 8080 ocupada | Manter **uma única instância** e usar outro terminal para `curl` |
| `ModuleNotFoundError: xml` em um instalador | Reinstalação de `libpython3.11-stdlib` |
| `contextLoads` falhava sem `JWT_SECRET` | Chave de teste na anotação, publicada em `d0797b7` |
| Teste de isolamento colidia por placa fixa | Placa única por UUID |
| `409` no registro | E-mail já cadastrado (comportamento atual; ver seção 4.5) |

### 5.3 Decisões arquiteturais anteriores

- Pacotes por funcionalidade; DTOs como `record`; regra de negócio nos serviços.
- `empresaId` extraído do JWT e validado contra o banco.
- JWT stateless, com usuário recarregado do banco a cada requisição.
- Segredo em Base64 via variável de ambiente; falha na inicialização se inválido. Nunca reutilizar valores antigos encontrados no histórico do Git.
- Registro público só cria **DONO** e uma empresa nova.
- BCrypt com checagem de 72 bytes.
- Placa **única por empresa**.

---

## 6. Estado atual

### 6.1 Último estado conhecido

- **`main` local e no GitHub: `d0797b7` (02/11/2026).**
- Codespaces em 02/11/2026: `## main...origin/main`, com `git diff --stat` vazio. **Não há evidência de divergência** entre o Codespace e o GitHub.

### 6.2 Implementado e nível de evidência

| Item | Evidência |
|---|---|
| Registro `201` e login `200` | Código + relato (Postman/curl) |
| `GET /api/caminhoes` sem token → `401` | Teste automatizado + relato |
| `POST /api/caminhoes` → `201` e listagem → `200` | Teste de isolamento + relato |
| Isolamento entre empresas em caminhões | Código + teste de integração; aprovação relatada |
| MOTORISTA recebe `403` ao criar caminhão | Teste com JWT **simulado**: cobre o `@PreAuthorize`, não o fluxo real |
| Suíte com 5 testes sem falhas (30/10) | Relato **[NÃO VERIFICADO]** |

Testes na revisão original (5; hoje a suíte tem 17, seção 6.3): `BackendApplicationTests.contextLoads`; `SegurancaCaminhaoTest` (sem token, token inválido, motorista simulado); `IsolamentoEmpresaCaminhaoTest` (1 teste com duas empresas reais). As três classes usam chave exclusiva de teste, que não deve ser reutilizada fora delas.

### 6.3 Build, `pom.xml` e testes

- O `pom.xml` tem uma **linha em branco antes de `<?xml`** desde `631358f`. Um parser XML estrito (expat) rejeitou as duas cópias testadas abaixo.
- Resultado do `mvn validate` em **cópias do arquivo**, fora do repositório:

| Conteúdo | Maven 3.9.9 | Maven 3.10.0 |
|---|---|---|
| Cópia LF (conteúdo do commit `d0797b7`) | passou | passou |
| Cópia CRLF (clone Windows com `core.autocrlf=true`) | falhou: `Non-parseable POM` | falhou: `Non-parseable POM` |

- **Causa comprovada em 02/11/2026:** o que decide é a **quantidade de bytes antes de `<?xml`**, não o tipo de quebra de linha do arquivo. O Maven aceita **no máximo 1 byte** antes da declaração. Em LF, a linha em branco é 1 byte (`\n`) e passa; em CRLF, é 2 bytes (`\r\n`) e falha.
- **Limites do teste da tabela acima:** só o `validate` foi executado, apenas nessas cópias e fora do Codespaces. Compilação e testes não foram executados.
- **Matriz da investigação:** 12 cópias temporárias, com resultado igual no Maven 3.10.0 e no 3.9.9.

| Bytes antes de `<?xml` | Corpo do arquivo | `validate` |
|---|---|---|
| nenhum | LF ou CRLF | passou |
| 1 (`\n`, `\r`, espaço ou tab) | LF | passou |
| 1 (`\n`) | CRLF | passou |
| 2 (`\r\n`) | LF ou CRLF | falhou |
| 2 (`\n\n` ou dois espaços) | LF | falhou |
| 3 (`\n\n\n`) | LF | falhou |

- **Mecanismo:** o `MXParser` do `plexus-utils` 3.6.2, usado pelo Maven 3.10.0, só rejeita a declaração quando o nome dela começa depois da posição 3 do buffer: o bytecode de `parsePI` compara a posição com 3 (`if_icmple`). Com N bytes antes de `<?xml`, o nome começa na posição N+2. A mensagem é `processing instruction can not have PITarget with reserved xml name`, seguida de `Non-parseable POM`.
- **O arquivo é XML inválido nos dois casos.** O `expat` rejeitou as 10 cópias com qualquer byte antes de `<?xml` e aceitou as 2 sem prefixo. O Maven apenas tolera 1 byte.
- **Correção recomendada, ainda não aplicada:** remover o byte inicial do `pom.xml`, de modo que o arquivo comece em `<?xml`. As cópias sem prefixo passaram em LF e em CRLF, no Maven e no `expat`. Não é necessário mudar o fim de linha nem o `.gitattributes`.
- **Limite da investigação:** a conversão do Git no Windows foi reproduzida nos bytes do arquivo, não em um clone real com `core.autocrlf=true`.
- **`validate` no Codespaces (02/11/2026, commit `0b1fa02`):** o `./mvnw validate` passou com BUILD SUCCESS no arquivo real, em LF e com a linha em branco, usando Java 21 e o wrapper configurado para Maven 3.10.0. Isso reproduz o resultado da cópia LF, mas não explica a diferença para a cópia CRLF.
- O notebook Windows não é ambiente de build nem de testes (seção 4.2).
- **Os testes usam só o banco exclusivo `frotamais_test`.** `src/test/resources/config/application.properties` fixa a URL e vale para todo teste, sem perfil nem anotação; `DB_URL` não a altera. `GuardaBancoDeTeste` consulta `current_database()` assim que o `DataSource` é criado, antes do `ddl-auto`, e interrompe a execução se o banco for outro ou estiver indisponível. Esses arquivos existem só no classpath de teste.
- **O banco precisa existir:** a suíte não o cria. Crie uma vez com `CREATE DATABASE frotamais_test`; o schema vem do `ddl-auto=update`. Os testes que gravam dados usam `@Transactional` com rollback, então não deixam registros.
- **Verificado em 02/11/2026 no Codespaces:** `./mvnw test` executado duas vezes, 17 testes, 0 falhas, 0 erros. Inserções, atualizações, exclusões e sessões de `frotamais` ficaram iguais em cada execução, e as contagens de `frotamais_test` não cresceram. Forçar `frotamais` com `SPRING_DATASOURCE_URL` bloqueou os 17 testes, sem escrita nem DDL; banco inexistente e servidor indisponível falharam com mensagem clara; `DB_URL` apontando para `frotamais` foi ignorado.
- **Limites do isolamento:** usuário e senha são os mesmos do banco de desenvolvimento, no mesmo servidor, e o guarda precisa abrir uma conexão de leitura para verificar o banco. O rollback não exercita falhas que só aparecem no commit. O guarda não cobre código executado fora de um contexto Spring.
- O dev container **não tem Docker disponível** para Testcontainers.
- **Dependências limpas em 02/11/2026:** removidos `spring-boot-starter-web` (obsoleto no próprio POM publicado, em favor de `spring-boot-starter-webmvc`), `spring-boot-starter-jackson`, `spring-boot-starter-security`, `spring-security-oauth2-resource-server`, `spring-security-oauth2-jose` e `spring-boot-starter-validation-test`. Todos já chegavam por outras dependências: as 82 bibliotecas do JAR ficaram idênticas por CRC, nenhuma versão mudou, os 17 testes passaram e o JAR subiu e respondeu `401` e `400` nas verificações manuais.
- `spring-boot-starter-data-jpa-test` foi **mantido**: nenhum teste o usa hoje, mas ele traz 5 artefatos exclusivos e é necessário para usar `@DataJpaTest`.
- O README ainda descreve a segurança como "planejada", fala em separação por usuário, não explica como rodar o projeto e usa outro slogan no rodapé.

### 6.4 Não comprovado

- `GET /{id}`, `PUT` e `DELETE` no fluxo feliz; `400` de validação e `409` de placa duplicada pela API.
- MOTORISTA com token real.
- Token expirado, com assinatura de outra chave, `alg=none`, emissor errado, subject inválido, usuário desativado e `empresaId` divergente.
- Corpo real das respostas de erro.
- Schema real do banco de desenvolvimento.

### 6.5 Status das frentes

**Implementado**
- Ambiente Codespaces + PostgreSQL.
- Registro e login JWT, com perfis lidos do banco.
- Isolamento por empresa em caminhões.
- CRUD de caminhões, com escrita só para DONO.
- 17 testes automatizados.

**Em desenvolvimento**
- Alinhamento da documentação e configuração do modo de trabalho (`CLAUDE.md`).
- Nenhuma funcionalidade está em desenvolvimento no código.

**Planejado:** seção 6.6.

**Depende de decisão:** Anexo A.

### 6.6 Roadmap

As etapas são cumulativas. **Segurança, testes e validações evoluem junto com cada funcionalidade.**

1. **Base confiável:** dependências limpas, README atualizado e causa da falha do `pom.xml` comprovada (seção 6.3). **Pendência:** aprovar e aplicar a remoção do byte inicial do `pom.xml`.
2. **Testes seguros: concluída em 02/11/2026.** `./mvnw test` usa só `frotamais_test`, sem fallback para `frotamais` (seção 6.3).
3. **Testes de segurança** com o filtro real: token inválido e expirado, usuário desativado, `empresaId` divergente, isolamento entre empresas.
4. **Migrations e CI:** Flyway com o schema atual como V1, e CI. Isso precede a etapa 7, porque a restrição de e-mail precisa ser removida do banco.
5. **Tratamento de erros:** formato único, causa registrada em log e mensagens que chegam ao cliente.
6. **Padrão de acesso por empresa** para todos os módulos novos.
7. **Autenticação com o mesmo e-mail em empresas diferentes.** Depende de P11. O fluxo só é concluído com isso.
8. **Vínculo motorista–caminhão e restrição de visualização.** Depende de P12. A restrição é implementada **antes** de disponibilizar a funcionalidade ao MOTORISTA, e só então o login dele é liberado.
9. **Viagens e trechos.** Depende de P1 a P4.
10. **Abastecimentos, despesas e manutenções.**
11. **Rentabilidade e consumo.**
12. **Dashboard.**
13. **Relatórios**, por último.

**Antes de disponibilizar a API publicamente:** rate limit em registro e login, HTTPS, backup, perfis de produção sem credenciais padrão e avaliação de LGPD, já que o sistema guardará dados pessoais e financeiros de terceiros.

---

## 7. Modo de trabalho

- **Diagnosticar antes de alterar:** ler os arquivos reais, explicar a mudança e só depois aplicar.
- **Preservar o que funciona:** mudanças pequenas e testáveis.
- **Não presumir:** separar o que foi verificado do que falta testar e reportar divergências.
- **Esclarecer as regras de negócio antes** de desenhar o modelo de dados.
- **Explicar o motivo** das decisões, em português, com exemplos do domínio.
- **Commits separados por funcionalidade**, no estilo `feat(auth): ...`, `feat(caminhoes): ...`, `chore(security): ...`, `test: ...`, sem `.frotamais-jwt.env`, `target/`, `.backup-auth-frotamais/` ou `contexto-local/`.
- **Testes:** priorizar segurança e multiempresa; não usar só JWT simulado para afirmar que o fluxo HTTP real foi testado.
- **Nunca** iniciar duas instâncias do back-end na mesma porta.
- Revisar sempre: tratamento de erros, configuração do JWT, credenciais e origem do `empresaId`.

---

## Anexo A. Pendências de decisão

**Bloqueiam Viagens**

- **P1. Combustível inicial e final:** como são obtidos, se nem sempre se sabe quanto resta no tanque? Em que unidade? Vale também para Arla? Qual a fórmula do consumo da viagem?
- **P2. Abastecimento fora de viagem:** é permitido? Onde é lançado? Existirá menu "Abastecimentos"?
- **P3. Frete e trechos:** uma viagem pode ter mais de um frete? A receita fica na viagem ou no trecho? Como a volta carregada entra na conta?
- **P4. Quilometragem do trecho:** é digitada ou calculada por odômetro? O que a "rota" da lista mostra quando há vários trechos?

**Definem cálculos e telas**

- **P5.** Fórmula do "Rodado Vazio (R$)".
- **P6.** Pedágios e eixos: valor digitado ou calculado? Eixos do caminhão, do conjunto ou da viagem? Desconto de tag?
- **P7.** Definição de "semana" e limites do período.
- **P8.** Excluir caminhão: apagar de fato ou apenas inativar? Viagem: status "Cancelada" ou exclusão? Auditoria desde o início?
- **P9.** Senha inicial do motorista: gerada ou definida pelo dono? O dono aparece como motorista nas viagens?
- **P10.** Renomear "R$/Km"? Em que etapa entra a exportação PDF/Excel?

**Decisões técnicas**

- **P11.** Modelo de identidade e login para o mesmo e-mail em empresas diferentes, inclusive o efeito sobre a redefinição de senha pelo dono.
- **P12.** Modelo da associação motorista–caminhão.
- **P13.** Local do Angular e estratégia de CORS ou proxy em desenvolvimento e produção.
- **P14.** Validar a lista da seção 3.4, que é uma consolidação, não uma lista ditada pelo responsável pelo produto.

---

## Anexo B. Ambiente e comandos (sem credenciais)

- **Caminho:** `/workspaces/Back_Frota_Mais` (Codespaces).
- **Variáveis:** `JWT_SECRET` (Base64, ≥32 bytes) e `JWT_HORAS` ficam em `.frotamais-jwt.env`, fora do Git. `DB_URL`, `DB_USER` e `DB_PASSWORD` só para sobrescrever os padrões.
- **Rodar a API:** `./run-dev-frotamais.sh`, em um terminal dedicado.
- **Testar sem token:** `curl -i http://localhost:8080/api/caminhoes` deve dar `401`.
- **Build e testes:** `./mvnw clean package -DskipTests` e `./mvnw test`. Os testes usam só `frotamais_test` e falham se ele não existir (seção 6.3).
- **Banco:** `psql -h localhost -p 5432 -U postgres -d frotamais`; `\dt`, `\conninfo`; `pg_isready -h localhost -p 5432`.
- **Portas:** 8080 (API), 4200 (Angular), 5432 (PostgreSQL). **Deixar a 5432 privada.** A 8080 só fica pública temporariamente, para testes externos.
- **Postman:** usar a URL encaminhada da porta 8080; rotas protegidas com *Bearer Token*; corpo `raw` → `JSON`.
- **Exemplo de caminhão:** `{"placa":"ABC1D23","modelo":"Volvo FH","ano":2024,"status":"ATIVO","capacidadeTanqueLitros":300}`. Placa duplicada na mesma empresa: `409`; id de outra empresa ou inexistente: `404`; validação: `400`; MOTORISTA em escrita: `403`.
- **Nunca** colar tokens ou capturas com JWT visível.
