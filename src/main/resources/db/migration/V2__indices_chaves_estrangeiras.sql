-- V2: índices nas chaves estrangeiras usadas pelas consultas. O PostgreSQL não
-- cria índice para FK sozinho; sem eles, buscar os lançamentos de uma viagem
-- vira varredura completa conforme o volume cresce.

CREATE INDEX idx_abastecimentos_viagem ON abastecimentos (viagem_id);
CREATE INDEX idx_despesas_viagem_viagem ON despesas_viagem (viagem_id);
CREATE INDEX idx_usuarios_empresa ON usuarios (empresa_id);
-- Sobreposição de períodos e preço de referência: viagens de um caminhão por data.
CREATE INDEX idx_viagens_caminhao_data_inicio ON viagens (caminhao_id, data_inicio);
