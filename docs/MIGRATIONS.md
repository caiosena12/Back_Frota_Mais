# Migrations do banco (Flyway)

O schema do Frota+ é versionado pelo Flyway. As migrations ficam em
`src/main/resources/db/migration` e rodam automaticamente quando a aplicação
sobe. O Hibernate está em `ddl-auto=validate`: ele só confere se as entidades
batem com o banco e **nunca altera o schema**.

## Regras

- Nunca edite uma migration já aplicada. Mudou o modelo? Crie `V{n+1}__descricao.sql`.
- Toda mudança de entidade JPA precisa da migration correspondente, senão a
  aplicação não sobe (o `validate` acusa a diferença).
- Valores novos em enums persistidos exigem atualizar o `CHECK` da coluna na
  migration (o Hibernate não faz mais isso).
- Migrations destrutivas (remover coluna ou tabela com dados) só com decisão
  explícita do responsável e backup.

## Versões

| Versão | Conteúdo |
|---|---|
| V1 | Schema no commit `3e5b9c1`, quando era gerado pelo Hibernate. Baseline dos bancos antigos. |
| V2 | Índices nas chaves estrangeiras (`abastecimentos.viagem_id`, `despesas_viagem.viagem_id`, `usuarios.empresa_id`) e `viagens (caminhao_id, data_inicio)`. |
| V3 | Caminhões: `marca`, `eixos` (nulos nos antigos), status `EXCLUIDO`, `inativado_em`, `excluido_em`; placa única só entre os não excluídos da empresa. |
| V4 | `motorista_caminhoes`: vínculo motorista–caminhão. |
| V5 | Convites: `usuarios.senha_hash` anulável (ativação pendente) e tabela `convites` (só o hash do token). |
| V6 | `viagens.motorista_id`: motorista responsável. |
| V7 | Cancelamento: status `CANCELADA` e colunas `cancelada_em`, `cancelada_por_id`, `motivo_cancelamento`. |
| V8 | Auditoria: `viagem_alteracoes` e `viagem_fotografias_anteriores`. |
| V9 | `abastecimentos.hora` (opcional; registros antigos ficam sem hora). |
| V10 | `manutencoes`. |

Detalhes no comentário do topo de cada arquivo. Nenhuma versão apaga dados.

## Cenários

### 1. Instalação nova ou banco vazio

Crie o banco vazio e suba a aplicação. O Flyway aplica todas as versões. Fora
do perfil `dev`, informe `DB_URL`, `DB_USER` e `DB_PASSWORD`.

```bash
psql -h localhost -U postgres -c "CREATE DATABASE frotamais"
./run-dev-frotamais.sh
```

### 2. Banco de testes

Os testes usam o banco exclusivo **`frotamais_test_flyway`**, montado só pelas
migrations, igual ao CI. O `GuardaBancoDeTeste` interrompe a suíte se a conexão
efetiva for outra (inclusive o antigo `frotamais_test`, montado pelo
`ddl-auto=update`, que não é mais usado e pode ser apagado quando o
responsável autorizar).

```bash
psql -h localhost -U postgres -c "CREATE DATABASE frotamais_test_flyway"
./mvnw test
```

### 3. Banco existente criado pelo antigo `ddl-auto=update`

É o caso do banco de desenvolvimento `frotamais` do Codespaces. Um banco assim
não tem a tabela `flyway_schema_history`, e o Flyway se recusa a migrar um
schema não vazio sem histórico. Esse banco também pode divergir da V1. O
`frotamais` verificado em 10/11/2026 tinha estas divergências:

- `UNIQUE (placa)` global, sobra da primeira versão de `Caminhao`. Ela impede
  duas empresas de cadastrar a mesma placa, o que contraria a regra de placa
  única por empresa;
- a coluna `caminhoes.apelido`, vazia em todos os registros;
- ausência das tabelas `viagens`, `abastecimentos` e `despesas_viagem`;
- nomes de restrições gerados pelo Hibernate.

O script `scripts/db/adequar-banco-legado-v1.sql` leva um banco assim ao estado
exato da V1, numa única transação. Ele **aborta sem alterar nada** se:
- `apelido` tiver algum valor;
- o banco já tiver histórico do Flyway;
- o nome do banco contiver `test`;
- faltar alguma restrição esperada.

Ele foi conferido em cópias descartáveis do `frotamais` e de um banco completo
gerado pelo Hibernate. Nos dois casos, o schema resultante ficou idêntico ao de
um banco montado só pela V1 (colunas, tipos, restrições e índices), com os dados
preservados. Reexecutar o script não altera nada.

Em 11/11/2026, o procedimento completo (script + passo 4) foi repetido numa
cópia descartável do `frotamais`, com a aplicação em perfil `dev`: baseline em
V1, V2 a V10 aplicadas, aplicação no ar, e as 17 empresas, 17 usuários e 8
caminhões preservados. Os caminhões antigos ficam com `marca` e `eixos` nulos
até serem editados.

**Procedimento (exige autorização do responsável, pois remove uma restrição e
uma coluna vazia):**

1. Pare a API. Confirme que nada está conectado ao banco:
   ```bash
   psql -h localhost -U postgres -Atc "select count(*) from pg_stat_activity where datname='frotamais'"
   ```
2. Faça o backup como uma cópia completa no próprio servidor. O `pg_dump` do
   Codespaces é da versão 15 e recusa o servidor 16.
   ```bash
   psql -h localhost -U postgres -c "CREATE DATABASE frotamais_backup_pre_flyway TEMPLATE frotamais"
   ```
3. Adeque o schema:
   ```bash
   psql -h localhost -U postgres -v ON_ERROR_STOP=1 -d frotamais -f scripts/db/adequar-banco-legado-v1.sql
   ```
4. Suba a API **uma vez** marcando a V1 como baseline. O Flyway registra a
   versão 1 sem executá-la e aplica as seguintes:
   ```bash
   SPRING_FLYWAY_BASELINE_ON_MIGRATE=true SPRING_FLYWAY_BASELINE_VERSION=1 ./run-dev-frotamais.sh
   ```
   Nas próximas vezes, suba normalmente, sem essas variáveis.
5. Confira o histórico:
   ```bash
   psql -h localhost -U postgres -d frotamais -c "select version, description, success from flyway_schema_history order by installed_rank"
   ```

**Recuperação**, se algo der errado: pare a API e troque os bancos.

```bash
psql -h localhost -U postgres -c "ALTER DATABASE frotamais RENAME TO frotamais_falhou"
psql -h localhost -U postgres -c "ALTER DATABASE frotamais_backup_pre_flyway RENAME TO frotamais"
```

### 4. Banco com dados e schema divergente desconhecido

Não force o baseline. Compare o schema com o de um banco novo montado pelas
migrations (colunas, `pg_constraint` e `pg_indexes`). Depois adapte o script de
adequação para o caso e teste antes numa cópia (`CREATE DATABASE ... TEMPLATE`).
Dados em colunas que sairiam do modelo exigem decisão humana antes.
