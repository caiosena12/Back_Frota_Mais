-- V6: motorista responsável pela viagem (quem dirigiu), distinto de
-- criado_por_id (quem registrou). Pode ser um MOTORISTA ou o próprio DONO.
-- Opcional: viagens antigas e históricas podem não ter essa informação.

ALTER TABLE viagens ADD COLUMN motorista_id BIGINT;
ALTER TABLE viagens ADD CONSTRAINT fk_viagens_motorista FOREIGN KEY (motorista_id) REFERENCES usuarios (id);
CREATE INDEX idx_viagens_motorista ON viagens (motorista_id);
