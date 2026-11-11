-- V11: auditoria mais completa.
-- - perfil (DONO ou MOTORISTA) de quem fez a alteração, no momento em que a
--   fez. O perfil do usuário pode mudar depois; o registro guarda o de então.
-- - resultado_novo: a fotografia financeira que substituiu a anterior, gravada
--   na mesma linha. Assim a linha mostra o antes e o depois da correção.
--
-- Registros antigos: até a V10, só o DONO podia corrigir viagens encerradas
-- (a aplicação recusava o MOTORISTA com 403), então perfil = 'DONO' é o valor
-- verdadeiro deles. resultado_novo fica nulo neles: não foi guardado na época
-- e não é reconstruído. Nenhum dado é apagado.

ALTER TABLE viagem_alteracoes ADD COLUMN perfil VARCHAR(20);
UPDATE viagem_alteracoes SET perfil = 'DONO' WHERE perfil IS NULL;
ALTER TABLE viagem_alteracoes ALTER COLUMN perfil SET NOT NULL;
ALTER TABLE viagem_alteracoes ADD CONSTRAINT ck_viagem_alteracoes_perfil
    CHECK (perfil IN ('DONO', 'MOTORISTA'));

ALTER TABLE viagem_fotografias_anteriores ADD COLUMN perfil VARCHAR(20);
UPDATE viagem_fotografias_anteriores SET perfil = 'DONO' WHERE perfil IS NULL;
ALTER TABLE viagem_fotografias_anteriores ALTER COLUMN perfil SET NOT NULL;
ALTER TABLE viagem_fotografias_anteriores ADD CONSTRAINT ck_viagem_fotografias_perfil
    CHECK (perfil IN ('DONO', 'MOTORISTA'));

ALTER TABLE viagem_fotografias_anteriores ADD COLUMN resultado_novo TEXT;
