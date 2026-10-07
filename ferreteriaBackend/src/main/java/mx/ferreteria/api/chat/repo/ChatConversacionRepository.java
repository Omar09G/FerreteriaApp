package mx.ferreteria.api.chat.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import mx.ferreteria.api.chat.entity.ChatConversacion;

public interface ChatConversacionRepository extends JpaRepository<ChatConversacion, Long> {

    Optional<ChatConversacion> findByConversacionIdAndTipo(Long conversacionId, String tipo);

    List<ChatConversacion> findByConversacionIdIn(List<Long> ids);
}
