-- V9: horário real do abastecimento (opcional). Registros antigos ficam sem
-- horário: nada é preenchido artificialmente. Hora local do fuso do produto
-- (America/Sao_Paulo), junto com a data já existente.
ALTER TABLE abastecimentos ADD COLUMN hora TIME;
