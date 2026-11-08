# Contexto técnico e regras de negócio do Frota+

**Última revisão:** 08/11/2026, branch `feat/viagens` (módulo de viagens e correções pós-auditoria). Revisão anterior: 02/11/2026, commit `bfd02ea`.
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

Implementadas: **autenticação**, **multiempresa**, **gestão de caminhões** (restrição por motorista ainda pendente) e, desde 08/11/2026, o **módulo de viagens** (seção 4.9): fretes com deslocamento vazio, abastecimentos de diesel e Arla, pedágios, despesas extras, rentabilidade por frete e indicadores por período. Hoje só o DONO acessa viagens.

Planejadas, **ainda sem código**:
- Manutenções.
- Custo e lucro por km; consumo médio agregado.
- Dashboards e relatórios (a API de indicadores já fornece os totais).
- Criação e gestão de motoristas, e liberação das viagens ao MOTORISTA.

**[IDEIA FUTURA]:** exportação em PDF/Excel; análise de km vazio entre fretes.

---

## 2. Regras de negócio

### 2.1 Viagens (fretes)

**Regras confirmadas em 08/11/2026**, que **substituem** o modelo anterior de "viagem composta por trechos" e as regras de combustível inicial e final:

- **[CONFIRMADO]** **Cada frete é uma viagem independente.** A viagem começa quando o caminhão sai para buscar a carga e termina quando descarrega essa carga.
- **[CONFIRMADO]** Exemplo: Frete 1, Cruzália → Santos, carregado, termina ao descarregar em Santos. Frete 2, Santos → São Paulo vazio e São Paulo → Palmital carregado. **O Frete 2 absorve os custos do deslocamento vazio anterior.**
- **[CONFIRMADO]** Não se mantém uma única viagem aberta enquanto o caminhão realiza vários contratos.
- **[CONFIRMADO]** O frete pode começar com um **deslocamento vazio**, que pode passar por várias cidades sem cadastro de cada parada. Importa o total acumulado até o carregamento.
- **[CONFIRMADO]** Pedágios e despesas do deslocamento vazio pertencem ao próximo frete; os do percurso carregado, ao frete atual. Como o frete começa no vazio, tudo é lançado no próprio frete.
- **[CONFIRMADO]** A viagem tem início e finalização. **[IMPLEMENTADO]** Status `EM_ANDAMENTO` e `FINALIZADA`.
- **[CONFIRMADO]** Fluxo de tela: **"+ Nova Viagem"** abre um pop-up com os dados básicos; abastecimento, pedágios, frete e custos extras entram depois, pelo menu **Ações**.
- **[CONFIRMADO]** **Quem edita:** o motorista edita o que lançou enquanto a viagem está em andamento; depois, só o dono corrige. O dono pode alterar qualquer dado.
- **[CONFIRMADO]** Não permitir alterações que comprometam viagens finalizadas sem regra explícita, e evitar exclusões que prejudiquem o histórico financeiro.

**Regras confirmadas na correção pós-auditoria (08/11/2026)** (seção 4.9):

- **[CONFIRMADO][IMPLEMENTADO]** **Duas viagens do mesmo caminhão não podem ter períodos sobrepostos.** Vale para abertura, correção de datas e finalização, com viagens finalizadas ou em andamento. As datas são **inclusivas**: compartilhar um dia já é sobreposição. Uma viagem em andamento tem **período aberto** (sem fim). Resposta: `409`.
- **[CONFIRMADO][IMPLEMENTADO]** **Depois da finalização, o dono só corrige lançamentos existentes** (`PUT`). Incluir (`POST`) ou excluir (`DELETE`) responde `409`. A correção não pode tirar o lançamento do período da viagem.
- **[CONFIRMADO][IMPLEMENTADO]** Caminhão `INATIVO` não abre viagem, e caminhão com viagem em andamento não pode ser inativado (`409`).
- **[IMPLEMENTADO]** Uma única viagem `EM_ANDAMENTO` por caminhão. Isso já decorre da regra de sobreposição, mas tem mensagem própria.
- **[IMPLEMENTADO]** Viagem **não é excluída** (não existe `DELETE`).
- **[IMPLEMENTADO]** O dono corrige viagem finalizada pelo `PUT`, que exige os mesmos dados completos da finalização. Não existe reabertura.
- **Limitação conhecida:** os períodos usam **datas sem horário**. Por isso o mesmo caminhão **não pode ter dois contratos no mesmo dia**: o frete seguinte começa no dia seguinte à descarga. Permitir isso exige migrar `data_inicio` e `data_fim` para data e hora, o que não foi feito.
- **Consequência da regra a confirmar (P16):** como a viagem nova nasce em andamento, com período aberto, **não é possível cadastrar uma viagem esquecida** com data anterior a fretes já existentes do caminhão.
- **[PENDENTE]** Status "Cancelada" (Anexo A, P8) e campo motorista na viagem (P9, P12).

### 2.2 Quilometragem carregada e vazia

- **[CONFIRMADO]** Registrar km inicial e final do **deslocamento vazio** e do **percurso carregado**, com o total de cada um e o consumo médio de cada um.
- **[IMPLEMENTADO]** Três leituras do odômetro: `kmInicialVazio` (saída vazia, opcional), `kmInicialCarregado` (carregamento, que é também o fim do vazio) e `kmFinalCarregado` (descarga). `kmFinalVazio` é exibido e é igual a `kmInicialCarregado`. Isso impede que km fiquem fora dos dois percursos.
- **[CONFIRMADO 08/11/2026] O motorista reinicia os contadores para a próxima etapa.** Não se exige continuidade do odômetro entre fretes, e o final de um frete não é comparado com o início do próximo. **Dentro de cada viagem**, as leituras precisam ser estritamente crescentes, e o vazio pode não existir. A duplicação de km entre fretes, achada na auditoria, é evitada pela regra de não sobreposição de períodos (seção 2.1).
- **[CONFIRMADO]** Os relatórios mostram **Km Vazio** e **Rodado Vazio (R$)**. **[IMPLEMENTADO]** `custoDieselVazio` por frete e no total do período.
- **[PROPOSTO]** Indicador de **% de km rodados vazios** no período.
- **Descartado em 08/11/2026:** conferir a continuidade do odômetro entre fretes, porque os contadores são reiniciados.

### 2.3 Combustível e abastecimentos

- **[CONFIRMADO]** Dois combustíveis: **diesel e Arla**, independentes.
- **[CONFIRMADO]** Abastecimento de diesel: data, litros e valor total pago. Vários abastecimentos parciais por viagem.
- **[CONFIRMADO]** **Preço médio ponderado do diesel por viagem** = total pago nos abastecimentos de diesel da viagem ÷ total de litros.
- **[CONFIRMADO]** Sem abastecimento na viagem, usar o **último preço médio conhecido daquele caminhão**. Sem histórico, **não inventar preço**: indicar a ausência de referência e não apresentar custo estimado como real.
- **[CONFIRMADO]** O consumo médio (km/L) de cada percurso é **informado pelo computador de bordo**. Litros estimados = km ÷ consumo médio, separadamente para vazio e carregado.
- **[CONFIRMADO]** Custo do diesel de cada percurso = litros estimados × preço médio. **Os valores pagos no diesel não são somados de novo como despesa**: isso seria dupla contagem.
- **[CONFIRMADO]** Arla: data, litros e valor pago; **opcional**; entra como custo operacional pelo valor pago e não interfere no cálculo do diesel.
- **[CONFIRMADO]** Não existe consumo fixo por caminhão. A capacidade do tanque é atributo do caminhão.
- **[IMPLEMENTADO]** "Último preço conhecido" = média ponderada da viagem **anterior** mais recente do mesmo caminhão e da mesma empresa que teve diesel com preço **dentro dos limites de plausibilidade**, em ordem de data de início e, no empate, de id. Uma viagem posterior nunca altera o preço de uma anterior.
- **[CONFIRMADO][IMPLEMENTADO] A referência de preço é congelada na finalização.** O frete grava os totais (valor pago e litros), a origem, a viagem de referência e o instante da consolidação. A partir daí, seu resultado **não muda** por viagens novas, correções em outros fretes ou abastecimentos de outras viagens.
  - Só uma **correção explícita do próprio frete** recalcula a referência, e apenas dele: o `PUT` da viagem ou o `PUT` de um abastecimento de diesel dela. Outros fretes finalizados nunca são recalculados automaticamente.
  - Corrigir Arla, pedágio ou despesa extra de um frete finalizado atualiza esses custos, mas **não** reabre a referência de preço.
  - Viagem em andamento não tem referência congelada: o preço é calculado a cada consulta.
  - Motivo: na auditoria, uma viagem retroativa mudou o lucro de um frete finalizado de "sem referência" para R$ −280, e a correção do diesel de um frete mudou o lucro de outro de 40,00 para −120,00.
- **[CONFIRMADO][IMPLEMENTADO] Limites de plausibilidade**, configuráveis (seção 4.9). Valores padrão: diesel de **R$ 1,00 a R$ 30,00 por litro** e consumo médio de **0,50 a 10,00 km/L**. São barreiras contra erro de digitação, não referências de mercado.
  - Entrada fora dos limites: `400`.
  - Dado já gravado fora dos limites (por exemplo, após mudar a configuração): cálculo incompleto, com o motivo nas pendências. Nunca vira custo zero.
- **[IMPLEMENTADO]** Diesel acima da **capacidade do tanque** responde `400`, se a capacidade estiver cadastrada no caminhão. Sem capacidade cadastrada, não há limite de litros: nenhuma capacidade é inventada. Arla não segue esse limite (tanque próprio).
- **Substituído:** combustível inicial e final da viagem (antiga P1) deixou de ser usado.
- **[PENDENTE]** Abastecimento fora de viagem (Anexo A, P2). Hoje todo abastecimento pertence a uma viagem.

### 2.4 Pedágios

- **[CONFIRMADO]** Pedágios são **custo da viagem** e entram pelas **Ações** da viagem.
- **[CONFIRMADO 08/11/2026]** Cada pedágio tem **data e valor efetivamente pago**. Não há horário, valor original, desconto, porcentagem nem cálculo da tag. Eixos também não são modelados.
- **[CONFIRMADO]** Despesas extras: **descrição, data e valor**. **Alimentação não é categoria de custo extra.** Manutenção não é despesa do frete.
- **[CONFIRMADO][IMPLEMENTADO 08/11/2026]** Despesa extra cuja descrição **contenha "alimentação"** é recusada (`400`) na inclusão e na correção. A comparação ignora acentos, maiúsculas e espaços extras. Outras palavras, como "refeição" ou "lanche", **não** são bloqueadas (P17).
- **[IMPLEMENTADO]** Pedágios e despesas extras na tabela `despesas_viagem`, com `tipo` `PEDAGIO` ou `EXTRA`.

### 2.5 Fretes, receitas, despesas e rentabilidade

- **[CONFIRMADO]** O **valor do frete é receita**, e **o motorista pode vê-lo**: muitas vezes o frete cai na conta dele como adiantamento, e depois ele repassa ao dono já descontando a comissão (geralmente 10%, varia por empresa).
- **[CONFIRMADO]** **A comissão do motorista NÃO entra na conta.** O sistema não calcula comissão.
- **[CONFIRMADO]** Custos considerados: **combustível, Arla, pedágios, custos extras e manutenção**.
- **[CONFIRMADO]** **Seguro e parcelas do caminhão NÃO entram.**
- **[CONFIRMADO]** **Manutenção** é uma tela de **histórico**, **não ligada a uma viagem**. O valor é **abatido da receita total do mês**. Há meses sem manutenção e meses com várias.
- **[CONFIRMADO 08/11/2026]** Manutenções são despesas do caminhão e **não reduzem a margem de um frete específico**.
- **[CONFIRMADO 08/11/2026]** Cada frete apresenta: receita, km vazios e carregados, custo de diesel vazio e carregado, Arla, pedágios, despesas extras, custo operacional total, lucro e margem percentual. **[IMPLEMENTADO]**
- **[CONFIRMADO, protótipo]** Indicadores: Receita Total, Custos Totais, Lucro Líquido/Total, **Margem Média**, custo e lucro por km, Km Vazio, Rodado Vazio (R$), Consumo Médio.
- **[CONFIRMADO]** Os números dos protótipos são **exemplos**, não dados reais.
- **[IMPLEMENTADO]** Lucro do frete = `frete − diesel vazio − diesel carregado − Arla − pedágios − extras`; margem = lucro ÷ receita × 100. Fórmulas na seção 4.9. Manutenção entra no resultado **mensal**, sem rateio, quando o módulo existir.
- **[IMPLEMENTADO]** Valores em `BigDecimal`/`numeric(15,2)`, nunca `double`; datas de "hoje" no fuso `America/Sao_Paulo`.
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

**Proteção do histórico [CONFIRMADO][IMPLEMENTADO 08/11/2026]:**
- Caminhão com viagem em andamento não pode ser inativado (`409`).
- Caminhão com **qualquer** viagem não pode ser excluído (`409`): deve ser inativado. Não há exclusão em cascata.
- Se uma viagem surgir entre a checagem e a exclusão, a chave estrangeira do banco recusa, e a resposta também é `409`.
- Inativação e exclusão usam o mesmo bloqueio do caminhão da abertura de viagem.
- `DELETE` de caminhão **sem** viagens continua apagando a linha (P8).

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

**[IMPLEMENTADO, provisório]** Todas as rotas de viagens exigem **DONO**; o MOTORISTA recebe `403`. Sem o vínculo motorista–caminhão (P12), liberar o MOTORISTA daria a ele acesso a todos os caminhões da empresa, contrariando a regra acima. A tabela de permissões continua sendo o alvo; a liberação depende da etapa 8 do roadmap.

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

- **Cada frete é uma viagem independente**, com deslocamento vazio opcional atribuído a ele (08/11/2026, substitui "viagem composta por trechos").
- Vários abastecimentos parciais por viagem; preço médio ponderado do diesel por viagem, com último preço do caminhão como alternativa.
- Km vazio e carregado com consumo médio de cada um, vindo do computador de bordo.
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
- Não iniciar **viagens antes de consolidar a base**: autenticação, controle de acesso, isolamento e testes. Em 08/11/2026, o responsável autorizou explicitamente o módulo de viagens antes das etapas 4, 7 e 8 do roadmap.

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
- Decisões do módulo de viagens listadas em P15 (Anexo A).
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
    │   ├── comum/      (RegraNegocioException)
    │   ├── caminhao/   (Caminhao, StatusCaminhao, CaminhaoRepository, CaminhaoRequest, CaminhaoResponse, CaminhaoService, CaminhaoController)
    │   ├── empresa/    (Empresa, EmpresaRepository)
    │   ├── seguranca/  (SecurityConfig, TokenService, UsuarioLogado)
    │   ├── usuario/    (Perfil, Usuario, UsuarioRepository)
    │   └── viagem/     (seção 4.9)
    ├── main/resources/application.properties
    ├── test/java/br/com/frotamais/
    │   ├── BackendApplicationTests.java
    │   ├── seguranca/  (SegurancaCaminhaoTest, IsolamentoEmpresaCaminhaoTest, AutenticacaoFluxoRealTest)
    │   ├── viagem/     (ApiViagemTeste, CalculadoraRentabilidadeTest, ViagemFluxoTest, ViagemValidacaoTest,
    │   │                IndicadoresViagemTest, IsolamentoEmpresaViagemTest, PermissaoViagemTest)
    │   └── suporte/    (GuardaBancoDeTeste, GuardaBancoDeTesteTest)
    └── test/resources/
        ├── config/application.properties    # fixa o banco frotamais_test
        └── META-INF/spring.factories        # registra GuardaBancoDeTeste
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
| `viagens` | id, empresa_id, caminhao_id, status, origem, destino, data_inicio, data_fim, valor_frete, km_inicial_vazio, km_inicial_carregado, km_final_carregado, consumo_medio_vazio, consumo_medio_carregado, criado_por_id, criada_em, atualizada_em, finalizada_em; referência congelada: origem_preco_diesel, preco_diesel_valor_pago (17,2), preco_diesel_litros (14,3), preco_diesel_medio (10,4), viagem_referencia_preco_id, referencia_consolidada_em | [IMPLEMENTADO] |
| `abastecimentos` | id, viagem_id, tipo (DIESEL/ARLA), data, litros, valor_total, criado_por_id, criado_em | [IMPLEMENTADO]; empresa pela viagem |
| `despesas_viagem` | id, viagem_id, tipo (PEDAGIO/EXTRA), descricao, data, valor, criado_por_id, criado_em | [IMPLEMENTADO]; empresa pela viagem |
| `trechos` | — | **Descartado** em 08/11/2026 (cada frete é uma viagem) |
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
- **[IMPLEMENTADO]** Em viagens, toda consulta de `ViagemRepository` recebe o `empresaId`, e as de `AbastecimentoRepository` e `DespesaRepository` filtram por `viagem.empresa.id` **e** pelo id da viagem. Comprovado por `IsolamentoEmpresaViagemTest` com tokens reais (seção 4.9).
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
- Erros de regra de negócio de viagens já usam `RegraNegocioException`, com mensagem no corpo (seção 4.9). Caminhões e autenticação continuam com `ResponseStatusException`.

### 4.8 Front-end e integrações

- **Angular**, **responsivo**, foco em celular, exigindo internet. **[CONFIRMADO]** Há protótipos de 4 telas (Dashboard, Caminhões, Viagens, Relatórios). Faltam Login, pop-up "Nova viagem", menu de Ações, formulário de caminhão, Manutenções e trechos.
- **CORS e proxy:** o código **não tem** configuração de CORS, e o `@CrossOrigin` foi removido do controller. **[PROPOSTO, não implementado]** Usar o proxy do Angular (`proxy.conf.json`, `/api` → `http://localhost:8080`, `ng serve --host 0.0.0.0`), porque no Codespaces a URL encaminhada muda. Sem proxy, o navegador será bloqueado.
- **Integrações:** nenhuma confirmada.

### 4.9 Módulo de viagens [IMPLEMENTADO em 08/11/2026]

Pacote `br.com.frotamais.viagem`, no padrão Controller → Service → Repository. Regras de negócio nas seções 2.1 a 2.5.

**Arquitetura**

| Classe | Papel |
|---|---|
| `ViagemController`, `LancamentoController` | Rotas. `@PreAuthorize("hasRole('DONO')")` na classe inteira. |
| `ViagemService` | Abertura, alteração, finalização, consulta e listagem. Valida um "estado candidato" antes de alterar a entidade. |
| `LancamentoService` | Abastecimentos, pedágios e despesas extras. |
| `RentabilidadeService` | Reúne, em poucas consultas filtradas pela empresa, os dados de um conjunto de viagens e chama a calculadora. Usa a referência congelada dos fretes finalizados e só consulta o histórico para viagens em andamento. |
| `LimitesPlausibilidade`, `ViagemConfig` | Limites configuráveis de preço do diesel e de consumo (`app.viagens.limites.*`), validados na subida. |
| `CalculadoraRentabilidade` | Função pura com todas as fórmulas. Não acessa banco, para ser testada isoladamente. |
| `IndicadorService` | Totais por período. |
| `RegraNegocioException` (`comum/`) | Erro de negócio com mensagem; tratado pelo `ApiExceptionHandler` no formato `{"status", "erro", "detalhes": [...]}`. |

**Endpoints** (todos exigem token de um DONO; MOTORISTA → `403`; sem token → `401`; viagem, caminhão ou lançamento de outra empresa ou inexistente → `404`)

| Método e rota | Corpo | Resposta |
|---|---|---|
| `GET /api/viagens?status=&caminhaoId=` | — | `200`, lista de `ViagemResumoResponse`, mais recentes primeiro |
| `GET /api/viagens/{id}` | — | `200`, `ViagemResponse` com lançamentos e rentabilidade |
| `POST /api/viagens` | `ViagemCriacaoRequest` | `201`, `ViagemResponse` |
| `PUT /api/viagens/{id}` | `ViagemAtualizacaoRequest` | `200`, `ViagemResponse` |
| `POST /api/viagens/{id}/finalizar` | `FinalizacaoRequest` | `200`, `ViagemResponse`; `409` se já finalizada |
| `GET /api/viagens/indicadores?ano=&mes=&de=&ate=&caminhaoId=` | — | `200`, `IndicadoresResponse` |
| `POST /api/viagens/{id}/abastecimentos` | `AbastecimentoRequest` | `201`, `AbastecimentoResponse` |
| `PUT` / `DELETE /api/viagens/{id}/abastecimentos/{lancamentoId}` | `AbastecimentoRequest` / — | `200` / `204` |
| `POST /api/viagens/{id}/pedagios` | `PedagioRequest` | `201`, `PedagioResponse` |
| `PUT` / `DELETE /api/viagens/{id}/pedagios/{lancamentoId}` | `PedagioRequest` / — | `200` / `204` |
| `POST /api/viagens/{id}/despesas-extras` | `DespesaExtraRequest` | `201`, `DespesaExtraResponse` |
| `PUT` / `DELETE /api/viagens/{id}/despesas-extras/{lancamentoId}` | `DespesaExtraRequest` / — | `200` / `204` |

Em viagem finalizada, `POST` e `DELETE` de lançamento → `409`; `PUT` é permitido, dentro do período da viagem. Período sobreposto ao de outra viagem do caminhão → `409`. Não existe `DELETE` de viagem.

Em caminhões: `PUT` que inativa caminhão com viagem em andamento → `409`; `DELETE` de caminhão com viagens → `409`.

**DTOs de entrada** (datas `AAAA-MM-DD`; dinheiro com até 2 casas; km inteiro de 0 a 9.999.999; consumo em km/L, maior que 0 e até 99,99)

| DTO | Campos |
|---|---|
| `ViagemCriacaoRequest` | `caminhaoId`*, `dataInicio`*, `origem`, `destino`, `valorFrete` (> 0), `kmInicialVazio`, `kmInicialCarregado`, `consumoMedioVazio`, `consumoMedioCarregado`. Exige ao menos um km inicial. |
| `ViagemAtualizacaoRequest` | Os mesmos, sem `caminhaoId`, mais `dataFim` e `kmFinalCarregado`, aceitos só em viagem finalizada. Substitui todos os campos: campo ausente é apagado. |
| `FinalizacaoRequest` | `dataFim`*, `kmFinalCarregado`*, `consumoMedioVazio`, `consumoMedioCarregado`. Consumo nulo mantém o já gravado. |
| `AbastecimentoRequest` | `tipo`* (`DIESEL`/`ARLA`), `data`*, `litros`* (> 0, 3 casas), `valorTotal`* (> 0) |
| `PedagioRequest` | `data`*, `valor`* (> 0) |
| `DespesaExtraRequest` | `descricao`* (até 200), `data`*, `valor`* (> 0) |

\* obrigatório. Nenhum DTO tem `empresaId`, status ou valor calculado; campos extras enviados pelo cliente são ignorados.

**DTOs de saída**

- `ViagemResponse`: dados da viagem, `kmFinalVazio` derivado, listas `abastecimentosDiesel`, `abastecimentosArla`, `pedagios`, `despesasExtras` e `rentabilidade`.
- `ViagemResumoResponse`: id, caminhão, placa, status, rota, datas, frete, km vazio e carregado, custo total, lucro, margem e `calculoCompleto`.
- `RentabilidadeResponse`: `receita`, `kmVazio`, `kmCarregado`, `litrosEstimadosVazio`, `litrosEstimadosCarregado`, `precoMedioDiesel`, `origemPrecoDiesel` (`ABASTECIMENTOS_DA_VIAGEM`, `ULTIMO_PRECO_DO_CAMINHAO`, `SEM_REFERENCIA`), `viagemReferenciaPrecoDieselId`, `referenciaPrecoConsolidadaEm` (preenchido quando o preço está congelado), `custoDieselVazio`, `custoDieselCarregado`, `custoArla`, `pedagios`, `despesasExtras`, `custoOperacionalTotal`, `lucro`, `margemPercentual`, `calculoCompleto` e `pendencias`. Campo nulo significa "não calculável"; o motivo está em `pendencias`.
- `IndicadoresResponse`: `de`, `ate`, `caminhaoId`, `criterioPeriodo` (`DATA_FIM`), `viagensFinalizadas`, `viagensConsideradas`, `viagensComCalculoIncompleto` (ids), os totais financeiros, `margemPercentual`, `kmVazio` e `kmCarregado`.

**Cálculos** (`CalculadoraRentabilidade`)

```text
kmVazio            = kmInicialCarregado − kmInicialVazio      (0 se não houve vazio)
kmCarregado        = kmFinalCarregado − kmInicialCarregado
precoMedioDiesel   = Σ valor pago no diesel ÷ Σ litros de diesel   (da viagem; senão, da anterior do caminhão)
litrosEstimados(p) = km(p) ÷ consumoMedio(p)                   (p = vazio ou carregado)
custoDiesel(p)     = km(p) × Σ valor pago ÷ (consumoMedio(p) × Σ litros)
custoOperacional   = custoDieselVazio + custoDieselCarregado + Arla + pedágios + despesas extras
lucro              = valorFrete − custoOperacional
margem (%)         = lucro ÷ valorFrete × 100
```

- O custo do diesel é calculado com uma única divisão e arredondado só no fim (2 casas, `HALF_UP`). Arredondar antes o preço e os litros pode mudar o resultado em centavos (há teste disso).
- Exibição: preço médio com 4 casas, litros estimados com 3 casas, dinheiro e margem com 2.
- Sem referência de preço, os custos de diesel, o custo total, o lucro e a margem ficam **nulos**, e `calculoCompleto = false`.
- Preço médio ou consumo fora dos limites de plausibilidade: o custo afetado (e, com ele, o total, o lucro e a margem) fica nulo, com o motivo em `pendencias`. Litros e custo do percurso com consumo implausível também ficam nulos.
- Fretes finalizados usam os totais congelados: `custoDiesel(p) = km(p) × valor pago congelado ÷ (consumoMedio(p) × litros congelados)`.
- Arla, pedágios e extras entram pelo valor pago. Manutenção não entra.

**Indicadores:** somam só fretes `FINALIZADA` com `dataFim` no período. Filtros: `ano`; `ano` e `mes`; ou `de`/`ate`, que cobre a semana sem o servidor definir "semana" (P7); sem filtro, todo o histórico; `caminhaoId` opcional. Fretes com cálculo incompleto ficam **fora de todos os totais** e são listados por id, para que um custo desconhecido nunca vire zero.

**Validações**

- Datas: início e fim não podem estar no futuro; fim ≥ início; todo lançamento precisa estar entre o início e o fim da viagem.
- Km: `kmInicialCarregado > kmInicialVazio`; `kmFinalCarregado > kmInicialCarregado`; consumo vazio só com deslocamento vazio.
- Finalização exige: frete, origem, destino, data final, km do carregamento, km final, consumo carregado e, se houve vazio, consumo vazio. A resposta lista tudo o que falta.
- O caminhão precisa ser da empresa e estar `ATIVO`, sem outra viagem `EM_ANDAMENTO` e sem viagem com período sobreposto.
- Consumo dentro de `app.viagens.limites.consumo-*`; diesel com preço por litro dentro de `app.viagens.limites.preco-diesel-*` e litros até a capacidade do tanque, se cadastrada.
- Despesa extra não pode conter "alimentação" na descrição.

**Configuração** (`application.properties`; podem ser sobrescritas por variável de ambiente, como `APP_VIAGENS_LIMITES_CONSUMO_MAXIMO`):

```properties
app.viagens.limites.preco-diesel-minimo=1.00
app.viagens.limites.preco-diesel-maximo=30.00
app.viagens.limites.consumo-minimo=0.50
app.viagens.limites.consumo-maximo=10.00
```

Valor ausente, não positivo, ou mínimo ≥ máximo impede a aplicação de subir (verificado: `APPLICATION FAILED TO START` com mensagem clara). Os limites são inclusivos.

**Decisões técnicas**

- **Concorrência:** abrir uma viagem, alterar ou finalizar uma viagem, inativar ou excluir um caminhão bloqueiam a linha do caminhão (`PESSIMISTIC_WRITE`, que no PostgreSQL vira `SELECT ... FOR NO KEY UPDATE`). Alterar ou finalizar bloqueiam depois a linha da viagem, sempre nessa ordem. Lançamentos bloqueiam só a viagem. A checagem de sobreposição roda com o caminhão bloqueado, então duas requisições simultâneas não criam períodos sobrepostos. **Não há garantia equivalente no banco**: um índice parcial ou uma restrição de exclusão por período exigiriam migration (etapa 4). A concorrência real **não foi exercitada por teste**, porque isso exigiria gravar dados permanentes e apagá-los depois (ver 6.4).
- **Referência congelada em colunas da própria viagem:** é a forma mais simples, porque o resto do cálculo (km, consumo, Arla, pedágios, extras e frete) já pertence ao frete e só muda por correção explícita dele.
- **Validar antes de alterar:** uma requisição recusada nunca deixa a entidade alterada pela metade.
- **Isolamento sem `empresa_id` nos lançamentos:** abastecimentos e despesas herdam a empresa da viagem, e toda consulta deles filtra por `viagem.empresa.id`.
- **Auditoria mínima:** `criado_por_id` e datas de criação e alteração. Ainda não há histórico de alterações.
- **Enums com `CHECK` no banco:** o Hibernate criou `CHECK` para `status` e `tipo` (por exemplo, `viagens_status_check`). O `ddl-auto=update` **não altera** esse `CHECK`: um valor novo, como `CANCELADA`, vai exigir ajuste manual no banco ou migration (etapa 4 do roadmap).
- **`ddl-auto=update`:** as três tabelas novas são criadas sozinhas. Até 08/11/2026, existiam só em `frotamais_test`; o banco `frotamais` as recebe na próxima vez que a API subir contra ele.

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
| 02/11 | `fdeb23f` | `.gitignore` passa a ignorar `contexto-local/` |
| 02/11 | `40f92ba`, `0b1fa02` | Este documento e o `CLAUDE.md` |
| 02/11 | (sem commit) | Rotação do `JWT_SECRET` no Codespaces. Fluxo validado com a chave em uso; um token anterior à rotação **não foi testado** |
| 02/11 | `f039e6a`, PR #1, merge `a4507bd` | `AutenticacaoFluxoRealTest`: 9 testes com o filtro real; a suíte passa a 14 testes |
| 02/11 | `640e1e9`, PR #2, merge `bfd02ea` | Testes isolados em `frotamais_test`, com `GuardaBancoDeTeste`; a suíte passa a 17 testes |
| 02/11 | `5461d6f`, `b090674`, `1d2b4bb`, branch `chore/base-confiavel` | Limpeza de dependências, causa e correção da falha do `pom.xml` com CRLF; merge no PR #3 (`c9dcaf9`) |
| 08/11 | branch `feat/viagens` | Módulo de viagens (seção 4.9), autorizado pelo responsável. Suíte com 56 testes. Sem push nem PR |
| 08/11 | branch `feat/viagens` | Auditoria e correções: sobreposição de períodos, referência de preço congelada, limites de plausibilidade, bloqueio de lançamentos após a finalização, alimentação, proteção de caminhões. Suíte com 85 testes |

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

- **`main` local e no GitHub: `c9dcaf9` (merge do PR #3).** Em 08/11/2026, o Codespace estava em `## main...origin/main`, sem alterações.
- **Branch `feat/viagens`**, criada a partir de `c9dcaf9`, com o módulo de viagens. **Só local**: sem push, PR ou merge.

### 6.2 Implementado e nível de evidência

Conferido nos testes em 02/11/2026. A coluna manual se refere a verificações feitas nessa data, sem teste automatizado equivalente.

| Item | Teste automatizado | Verificação manual |
|---|---|---|
| Registro `201` | `IsolamentoEmpresaCaminhaoTest`, `AutenticacaoFluxoRealTest` | `curl` no backend em execução |
| Login `200`, com token aceito na rota protegida | `AutenticacaoFluxoRealTest.loginValidoEmiteTokenAceitoNaRotaProtegida` | `curl` no backend em execução |
| `GET /api/caminhoes` sem token → `401` | `SegurancaCaminhaoTest.deveBloquearListagemSemToken` | `curl` no backend em execução e no JAR |
| Token malformado → `401` | `SegurancaCaminhaoTest.deveBloquearListagemComTokenInvalido` | `curl` no JAR |
| `POST /api/caminhoes` → `201` e listagem → `200` | `IsolamentoEmpresaCaminhaoTest` | Só a listagem, com `curl` |
| Isolamento entre empresas em caminhões: listagem vazia e `404` em consulta, alteração e exclusão | `IsolamentoEmpresaCaminhaoTest` | Não |
| Token com outra chave, expirado, de outro emissor, com `empresaId` divergente ou de usuário inexistente → `401` | `AutenticacaoFluxoRealTest` | Só outra chave: token forjado com a chave pública de teste |
| Usuário desativado perde o acesso e não consegue fazer login | `AutenticacaoFluxoRealTest.desativarUsuarioInvalidaTokenJaEmitidoEBloqueiaLogin` | Não |
| MOTORISTA recebe `403` ao criar caminhão | Simulado em `SegurancaCaminhaoTest` e com filtro real em `AutenticacaoFluxoRealTest.perfilVemDoBancoEMotoristaNaoCadastraCaminhao`, usando perfil alterado no banco | Não |
| `alg=none` e assinatura adulterada → `401` | Não | `curl` no backend em execução |
| Validação do login → `400` | Não | `curl` no JAR |
| Suíte com 17 testes, 0 falhas e 0 erros | `./mvnw test` em `frotamais_test`, no commit `8e4f719` | Não se aplica |
| Viagens: cadastro, finalização, vazio no próximo frete, preço ponderado, último preço, sem referência, Arla, pedágios, extras, indicadores | `ViagemFluxoTest`, `CalculadoraRentabilidadeTest`, `IndicadoresViagemTest` | Não |
| Viagens: isolamento entre empresas em todas as rotas, com tokens reais | `IsolamentoEmpresaViagemTest` | Não |
| Viagens: MOTORISTA (perfil do banco) → `403`; sem token → `401` | `PermissaoViagemTest` | Só o `401`, com `curl` na API em execução contra `frotamais_test` |
| Suíte com 56 testes, 0 falhas e 0 erros | `./mvnw test` em `frotamais_test`, na branch `feat/viagens` (08/11/2026) | Não se aplica |
| Correções da auditoria: sobreposição, referência congelada (cenários A2 e A3), plausibilidade, lançamentos após finalização, alimentação, caminhões | `SobreposicaoViagemTest`, `ReferenciaCongeladaTest`, `PlausibilidadeViagemTest`, `CalculadoraRentabilidadeTest`, `LancamentoViagemFinalizadaTest`, `AlimentacaoDespesaTest`, `CaminhaoHistoricoTest` | Subida da API com limites inválidos recusada |
| Suíte com 85 testes, 0 falhas e 0 erros | `./mvnw test` em `frotamais_test`, após as correções (08/11/2026) | Não se aplica |

**Não comprovado:** não existe forma de criar MOTORISTA pela API, então um MOTORISTA criado pelo fluxo real nunca foi testado. O relato de 5 testes sem falhas em 30/10 continua **[NÃO VERIFICADO]**. A lista completa está na seção 6.4.

Testes na revisão original (5; hoje a suíte tem 17, seção 6.3): `BackendApplicationTests.contextLoads`; `SegurancaCaminhaoTest` (sem token, token inválido, motorista simulado); `IsolamentoEmpresaCaminhaoTest` (1 teste com duas empresas reais). Hoje, as cinco classes com `@SpringBootTest` usam chave exclusiva de teste, que não deve ser reutilizada fora delas.

### 6.3 Build, `pom.xml` e testes

- O `pom.xml` teve uma **linha em branco antes de `<?xml`** de `631358f` até a correção `1d2b4bb`. Um parser XML estrito (expat) rejeitou as duas cópias testadas abaixo.
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
- **Correção aplicada em `1d2b4bb`:** o byte inicial do `pom.xml` foi removido, e o restante do arquivo ficou idêntico byte a byte. A versão corrigida passou em LF e em CRLF, no Maven 3.10.0, no 3.9.9 e no `expat`. Não foi necessário mudar o fim de linha nem o `.gitattributes`.
- **Clones reais com `core.autocrlf=true`, no Linux:** o commit `abf2bd3` falhou com `Non-parseable POM`, e o `1d2b4bb` passou. **Limite:** não foi testado em uma máquina Windows.
- **`validate` no Codespaces (02/11/2026, commit `0b1fa02`):** o `./mvnw validate` passou com BUILD SUCCESS no arquivo real, em LF e com a linha em branco, usando Java 21 e o wrapper configurado para Maven 3.10.0. Isso reproduz o resultado da cópia LF, mas não explica a diferença para a cópia CRLF.
- O notebook Windows não é ambiente de build nem de testes (seção 4.2).
- **Os testes usam só o banco exclusivo `frotamais_test`.** `src/test/resources/config/application.properties` fixa a URL e vale para todo teste, sem perfil nem anotação; `DB_URL` não a altera. `GuardaBancoDeTeste` consulta `current_database()` assim que o `DataSource` é criado, antes do `ddl-auto`, e interrompe a execução se o banco for outro ou estiver indisponível. Esses arquivos existem só no classpath de teste.
- **O banco precisa existir:** a suíte não o cria. Crie uma vez com `CREATE DATABASE frotamais_test`; o schema vem do `ddl-auto=update`. Os testes que gravam dados usam `@Transactional` com rollback, então não deixam registros.
- **Verificado em 02/11/2026 no Codespaces:** `./mvnw test` executado duas vezes, 17 testes, 0 falhas, 0 erros. Inserções, atualizações, exclusões e sessões de `frotamais` ficaram iguais em cada execução, e as contagens de `frotamais_test` não cresceram. Forçar `frotamais` com `SPRING_DATASOURCE_URL` bloqueou os 17 testes, sem escrita nem DDL; banco inexistente e servidor indisponível falharam com mensagem clara; `DB_URL` apontando para `frotamais` foi ignorado.
- **Limites do isolamento:** usuário e senha são os mesmos do banco de desenvolvimento, no mesmo servidor, e o guarda precisa abrir uma conexão de leitura para verificar o banco. O rollback não exercita falhas que só aparecem no commit. O guarda não cobre código executado fora de um contexto Spring.
- O dev container **não tem Docker disponível** para Testcontainers.
- **Dependências limpas em 02/11/2026:** removidos `spring-boot-starter-web` (obsoleto no próprio POM publicado, em favor de `spring-boot-starter-webmvc`), `spring-boot-starter-jackson`, `spring-boot-starter-security`, `spring-security-oauth2-resource-server`, `spring-security-oauth2-jose` e `spring-boot-starter-validation-test`. Todos já chegavam por outras dependências: as 82 bibliotecas do JAR ficaram idênticas por CRC, nenhuma versão mudou, os 17 testes passaram e o JAR subiu e respondeu `401` e `400` nas verificações manuais.
- `spring-boot-starter-data-jpa-test` foi **mantido**: nenhum teste o usa hoje, mas ele traz 5 artefatos exclusivos e é necessário para usar `@DataJpaTest`.

### 6.4 Comprovado e não comprovado

Atualizada em 02/11/2026, com a suíte de 17 testes executada no Codespaces.

**Funcionalidades implementadas**

- Registro de empresa com usuário DONO e login com JWT.
- Validação do token e do usuário a cada requisição, com perfil lido do banco.
- CRUD de caminhões com isolamento por empresa e escrita só para DONO.
- Módulo de viagens (seção 4.9), só para DONO.

**Testes existentes**

| Classe | Testes | O que cobre |
|---|---|---|
| `BackendApplicationTests` | 1 | Subida do contexto |
| `SegurancaCaminhaoTest` | 3 | Sem token, token inválido e MOTORISTA simulado com `jwt()` |
| `IsolamentoEmpresaCaminhaoTest` | 1 | Duas empresas com tokens reais: listagem, consulta, alteração e exclusão entre empresas |
| `AutenticacaoFluxoRealTest` | 9 | Filtro real: login, tokens recusados, usuário desativado e perfil lido do banco |
| `GuardaBancoDeTesteTest` | 3 | Banco efetivo da suíte e recusa de outro banco |
| `CalculadoraRentabilidadeTest` | 14 | Fórmulas sem banco: consumo separado, sem dupla contagem, Arla, sem preço, arredondamento, prejuízo, limites de plausibilidade |
| `SobreposicaoViagemTest` | 5 | Períodos sobrepostos (total, interno, parcial, dia compartilhado), período aberto, odômetro reiniciado |
| `ReferenciaCongeladaTest` | 4 | Cenários A2 e A3 da auditoria; correção explícita recalcula só o frete corrigido |
| `PlausibilidadeViagemTest` | 4 | Preço e consumo fora dos limites, capacidade do tanque, referência histórica implausível |
| `LancamentoViagemFinalizadaTest` | 5 | `POST` e `DELETE` recusados nos quatro tipos; `PUT` do dono; MOTORISTA recusado |
| `AlimentacaoDespesaTest` | 2 | Variações de "alimentação" recusadas na inclusão e na correção |
| `CaminhaoHistoricoTest` | 4 | Inativação com viagem aberta e exclusão com histórico recusadas |
| `ViagemFluxoTest` | 13 | Fluxo HTTP completo dos fretes e lançamentos |
| `ViagemValidacaoTest` | 10 | Recusas de dados, datas, km, estados e lançamentos |
| `IndicadoresViagemTest` | 4 | Totais por mês, ano, intervalo e caminhão; exclusão dos incompletos |
| `IsolamentoEmpresaViagemTest` | 1 | Todas as rotas de viagens entre duas empresas, com tokens reais |
| `PermissaoViagemTest` | 2 | `401` sem token e `403` para MOTORISTA |

**Alterações por PR**

- **PR #1** (`f039e6a`): adicionou `AutenticacaoFluxoRealTest`, sem mudar o código da aplicação.
- **PR #2** (`640e1e9`): isolou os testes em `frotamais_test`, adicionou o guarda, o rollback em `IsolamentoEmpresaCaminhaoTest` e os testes do guarda, sem mudar o código da aplicação.

**Comprovado por teste automatizado**

- `GET /{id}` no fluxo feliz, pela empresa dona do caminhão.
- Token recusado: outra chave, expirado, emissor errado, `empresaId` divergente e usuário inexistente.
- Usuário desativado perde o acesso com token já emitido e não consegue fazer login.
- Login recusado por senha incorreta ou e-mail inexistente.
- Usuário com perfil MOTORISTA no banco recebe `403` ao criar caminhão, mesmo com token que diz DONO.

**Verificado manualmente, sem teste automatizado**

- `alg=none`, assinatura adulterada e token forjado com a chave pública de teste: `401` no backend em execução.
- Corpo do `400` de validação no login: `{"status":400,"detalhes":[...],"erro":"Dados inválidos"}`.
- Tabelas `caminhoes`, `empresas` e `usuarios` e colunas de `usuarios` no banco de desenvolvimento.

**Ainda não comprovado**

- Concorrência real na abertura de viagens e na sobreposição de períodos: garantida pelo bloqueio do caminhão (SQL `FOR NO KEY UPDATE` confirmado no log), sem teste com duas transações gravando de verdade.
- Conversão em `409` da violação de chave estrangeira ao excluir caminhão: o caminho existe no código, mas não há teste que force a corrida.

- `PUT` e `DELETE` no fluxo feliz; `400` de validação e `409` de placa duplicada em caminhões.
- Subject não numérico no token.
- Corpo real das respostas `401`, `403`, `404` e `409`.
- Restrições e índices reais do banco de desenvolvimento.
- Que a chave anterior à rotação foi recusada: falta testar um token antigo.

**Pendências abertas**

- Decisões do Anexo A e etapas seguintes do roadmap (seção 6.6).

### 6.5 Status das frentes

**Implementado**
- Ambiente Codespaces + PostgreSQL.
- Registro e login JWT, com perfis lidos do banco.
- Isolamento por empresa em caminhões e viagens.
- CRUD de caminhões, com escrita só para DONO.
- Módulo de viagens e correções da auditoria, na branch `feat/viagens`, aguardando revisão.
- 85 testes automatizados.

**Em desenvolvimento**
- Revisão do módulo de viagens e das decisões listadas na seção 4.9 e no Anexo A.

**Planejado:** seção 6.6.

**Depende de decisão:** Anexo A.

### 6.6 Roadmap

As etapas são cumulativas. **Segurança, testes e validações evoluem junto com cada funcionalidade.**

1. **Base confiável: concluída em 02/11/2026 na branch `chore/base-confiavel`, aguardando merge.** Dependências limpas, README atualizado e falha do `pom.xml` corrigida (seção 6.3).
2. **Testes seguros: concluída em 02/11/2026.** `./mvnw test` usa só `frotamais_test`, sem fallback para `frotamais` (seção 6.3).
3. **Testes de segurança** com o filtro real: token inválido e expirado, usuário desativado, `empresaId` divergente, isolamento entre empresas.
4. **Migrations e CI:** Flyway com o schema atual como V1, e CI. Isso precede a etapa 7, porque a restrição de e-mail precisa ser removida do banco.
5. **Tratamento de erros:** formato único, causa registrada em log e mensagens que chegam ao cliente.
6. **Padrão de acesso por empresa** para todos os módulos novos.
7. **Autenticação com o mesmo e-mail em empresas diferentes.** Depende de P11. O fluxo só é concluído com isso.
8. **Vínculo motorista–caminhão e restrição de visualização.** Depende de P12. A restrição é implementada **antes** de disponibilizar a funcionalidade ao MOTORISTA, e só então o login dele é liberado.
9. **Viagens: implementada em 08/11/2026** (branch `feat/viagens`), por autorização do responsável, antes das etapas 4, 7 e 8. As regras de 08/11/2026 resolveram P1, P3, P4 e P5. O acesso do MOTORISTA continua dependendo da etapa 8.
10. **Abastecimentos e despesas: implementados com as viagens.** Manutenções ainda não.
11. **Rentabilidade por frete e indicadores por período: implementados.** Faltam custo e lucro por km e consumo médio agregado.
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

**Resolvidas em 08/11/2026** pelas regras da seção 2.1 a 2.5

- ~~P1.~~ Combustível inicial e final substituídos pelo consumo médio do computador de bordo.
- ~~P3.~~ Cada frete é uma viagem; a receita é o valor do frete.
- ~~P4.~~ Quilometragem por odômetro: saída vazia, carregamento e descarga.
- ~~P5.~~ Rodado vazio = km vazio ÷ consumo vazio × preço médio do diesel.
- ~~P6.~~ Pedágio = valor pago digitado; sem eixos nem desconto de tag.

**Ainda abertas**

- **P2. Abastecimento fora de viagem:** é permitido? Onde é lançado? Existirá menu "Abastecimentos"? Hoje todo abastecimento pertence a uma viagem.
- **P7.** Definição de "semana" e limites do período. Implementado como intervalo `de`/`ate` livre, aplicado à **data final** do frete: confirmar o critério.
- **P8.** Excluir caminhão **sem** viagens: apagar de fato ou apenas inativar? Com viagens, a exclusão já é recusada (`409`, testado). Viagem: status "Cancelada"? Hoje a viagem não pode ser excluída, e lançamentos só podem ser excluídos em viagem em andamento.
- **P15.** Decisões do módulo de viagens ainda a confirmar: frete obrigatório só na finalização; fretes com cálculo incompleto fora de **todos** os totais, inclusive da receita; margem do período ponderada (lucro total ÷ receita total) ou média das margens; ordem de duas viagens no mesmo dia pelo id (raro, porque a regra de sobreposição já impede). **Confirmadas em 08/11/2026:** uma viagem em andamento por caminhão (via não sobreposição), caminhão inativo não abre viagem, odômetro sem continuidade entre fretes e lançamentos só corrigidos após a finalização.
- **P16.** Como cadastrar uma **viagem esquecida**, com data anterior a fretes já existentes do caminhão? A regra do período aberto impede isso hoje. Opções: criar a viagem já finalizada (com data final) ou outra regra.
- **P17.** A regra da alimentação deve cobrir sinônimos ("refeição", "lanche", "almoço")? Hoje bloqueia só o termo "alimentação".
- **P18.** Limites de plausibilidade: os padrões (R$ 1,00 a 30,00/L; 0,50 a 10,00 km/L) servem? Deve haver limite para Arla? Se um limite mudar depois, fretes antigos fora dele passam a aparecer como incompletos (sem mudar valores). Isso é desejado?
- **P19.** Pedágio ou despesa do deslocamento vazio lançados enquanto o frete anterior ainda está aberto (esquecido) caem no frete errado. Vale mitigar?
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
