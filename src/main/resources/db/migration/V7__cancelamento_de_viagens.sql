-- V7: cancelamento de viagens (status CANCELADA), feito só pelo DONO, com
-- motivo obrigatório. Nada é apagado: a viagem, os lançamentos e o resultado
-- continuam no banco.

ALTER TABLE viagens DROP CONSTRAINT ck_viagens_status;
ALTER TABLE viagens ADD CONSTRAINT ck_viagens_status
    CHECK (status IN ('EM_ANDAMENTO', 'FINALIZADA', 'CANCELADA'));

ALTER TABLE viagens ADD COLUMN cancelada_em TIMESTAMP WITH TIME ZONE;
ALTER TABLE viagens ADD COLUMN cancelada_por_id BIGINT;
ALTER TABLE viagens ADD COLUMN motivo_cancelamento VARCHAR(1000);

ALTER TABLE viagens ADD CONSTRAINT fk_viagens_cancelada_por
    FOREIGN KEY (cancelada_por_id) REFERENCES usuarios (id);
CREATE INDEX idx_viagens_cancelada_por ON viagens (cancelada_por_id);

-- Cancelada <=> tem data final, momento, autor e motivo do cancelamento.
ALTER TABLE viagens ADD CONSTRAINT ck_viagens_cancelamento CHECK (
    (status = 'CANCELADA'
        AND data_fim IS NOT NULL AND cancelada_em IS NOT NULL
        AND cancelada_por_id IS NOT NULL AND motivo_cancelamento IS NOT NULL
        AND length(btrim(motivo_cancelamento)) > 0)
    OR (status <> 'CANCELADA'
        AND cancelada_em IS NULL AND cancelada_por_id IS NULL AND motivo_cancelamento IS NULL));
