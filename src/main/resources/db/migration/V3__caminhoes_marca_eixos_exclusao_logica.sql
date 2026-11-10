-- V3: cadastro completo do caminhão e exclusão lógica.
--
-- marca e eixos: obrigatórios para cadastros novos e alterações (validados na
-- API), mas nulos nos caminhões já existentes até o dono completá-los.
--
-- Situações: ATIVO, INATIVO e EXCLUIDO (lógico). inativado_em guarda o momento
-- da inativação (base da regra de viagem histórica em caminhão inativo) e
-- excluido_em o da exclusão. Nada é apagado: viagens, lançamentos e
-- resultados continuam ligados ao caminhão.
--
-- Placa: única por empresa só entre caminhões não excluídos. Um caminhão
-- vendido (excluído) mantém a placa no histórico sem impedir que a empresa
-- cadastre de novo um veículo com ela.

ALTER TABLE caminhoes ADD COLUMN marca VARCHAR(60);
ALTER TABLE caminhoes ADD COLUMN eixos INTEGER;
ALTER TABLE caminhoes ADD COLUMN inativado_em TIMESTAMP WITH TIME ZONE;
ALTER TABLE caminhoes ADD COLUMN excluido_em TIMESTAMP WITH TIME ZONE;

ALTER TABLE caminhoes ADD CONSTRAINT ck_caminhoes_eixos CHECK (eixos IS NULL OR eixos BETWEEN 1 AND 20);

ALTER TABLE caminhoes DROP CONSTRAINT ck_caminhoes_status;
ALTER TABLE caminhoes ADD CONSTRAINT ck_caminhoes_status CHECK (status IN ('ATIVO', 'INATIVO', 'EXCLUIDO'));
ALTER TABLE caminhoes ADD CONSTRAINT ck_caminhoes_excluido_em
    CHECK ((status = 'EXCLUIDO') = (excluido_em IS NOT NULL));

ALTER TABLE caminhoes DROP CONSTRAINT uk_caminhoes_empresa_placa;
CREATE UNIQUE INDEX uk_caminhoes_empresa_placa_operacional ON caminhoes (empresa_id, placa)
    WHERE status <> 'EXCLUIDO';
