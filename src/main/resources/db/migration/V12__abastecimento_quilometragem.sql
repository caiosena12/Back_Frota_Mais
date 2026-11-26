-- V12: quilometragem (odômetro) do caminhão no momento do abastecimento.
-- Substitui o horário (V9) como informação pedida no cadastro.
--
-- Registros antigos: a quilometragem fica nula. Não é conhecida e não é
-- inventada (nem zero). A aplicação exige o valor em abastecimentos novos; a
-- correção de um abastecimento antigo pode mantê-lo desconhecido.
--
-- A coluna hora (V9) NÃO é removida: guarda o horário dos registros antigos e
-- continua sendo devolvida na consulta. Só deixa de ser recebida no cadastro.

ALTER TABLE abastecimentos ADD COLUMN quilometragem INTEGER;

ALTER TABLE abastecimentos ADD CONSTRAINT ck_abastecimentos_quilometragem
    CHECK (quilometragem IS NULL OR (quilometragem >= 0 AND quilometragem <= 9999999));
