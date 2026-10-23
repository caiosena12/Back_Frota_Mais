package br.com.frotamais.caminhao;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CaminhaoRepository extends JpaRepository<Caminhao, Long> {

    boolean existsByPlaca(String placa);
}
