package mx.ferreteria.api.chat.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import mx.ferreteria.api.chat.entity.ChatParticipante;

public interface ChatParticipanteRepository extends JpaRepository<ChatParticipante, Long> {

    List<ChatParticipante> findByConversacionId(Long conversacionId);

    List<ChatParticipante> findByUsuarioId(Integer usuarioId);

    Optional<ChatParticipante> findByConversacionIdAndUsuarioId(Long conversacionId, Integer usuarioId);

    /**
     DIRECTA existente entre dos usuarios (exactamente ellos dos).
     */
    @Query(value = """
            SELECT c.conversacion_id FROM notif.chat_conversacion c
            WHERE c.tipo = 'DIRECTA'
              AND EXISTS (SELECT 1 FROM notif.chat_participante p
                          WHERE p.conversacion_id = c.conversacion_id AND p.usuario_id = :a)
              AND EXISTS (SELECT 1 FROM notif.chat_participante p
                          WHERE p.conversacion_id = c.conversacion_id AND p.usuario_id = :b)
              AND (SELECT COUNT(*) FROM notif.chat_participante p
                   WHERE p.conversacion_id = c.conversacion_id) = 2
            """, nativeQuery = true)
    List<Long> directaEntre(@Param("a") Integer a, @Param("b") Integer b);
}
