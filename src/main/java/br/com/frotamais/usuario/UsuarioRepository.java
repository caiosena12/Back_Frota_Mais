package br.com.frotamais.usuario;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<Usuario> findByIdAndEmpresaId(Long id, Long empresaId);

    List<Usuario> findAllByEmpresaIdAndPerfilOrderByNomeAscIdAsc(Long empresaId, Perfil perfil);
}
