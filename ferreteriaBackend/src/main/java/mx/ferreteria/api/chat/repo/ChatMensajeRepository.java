package mx.ferreteria.api.chat.repo;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import mx.ferreteria.api.chat.entity.ChatMensaje;

public interface ChatMensajeRepository extends JpaRepository<ChatMensaje, Long> {

    Page<ChatMensaje> findByConversacionIdAndEliminadaEnIsNullOrderByCreadaEnDesc(
            Long conversacionId, Pageable pageable);

    /** Último mensaje visible de la conversación (para la lista). */
    ChatMensaje findFirstByConversacionIdAndEliminadaEnIsNullOrderByCreadaEnDesc(
            Long conversacionId);

    /**
     * No leídos para un usuario desde su marca de lectura. Dos variantes
     * porque PostgreSQL no infiere el tipo de un parámetro temporal nulo
     * en `(:leido is null or ...)` (42P18): sin marca se cuenta todo.
     */
    @Query("""
            select count(m) from ChatMensaje m
            where m.conversacionId = :conv and m.eliminadaEn is null
              and m.autorId <> :usuario and m.creadaEn > :leido
            """)
    long contarNoLeidosDesde(@Param("conv") Long conv, @Param("usuario") Integer usuario,
            @Param("leido") Instant leido);

    @Query("""
            select count(m) from ChatMensaje m
            where m.conversacionId = :conv and m.eliminadaEn is null
              and m.autorId <> :usuario
            """)
    long contarNoLeidosTodos(@Param("conv") Long conv, @Param("usuario") Integer usuario);
}
