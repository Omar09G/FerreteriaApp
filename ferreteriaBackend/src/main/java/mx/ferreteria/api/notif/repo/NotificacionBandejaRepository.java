package mx.ferreteria.api.notif.repo;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import mx.ferreteria.api.notif.entity.NotificacionBandeja;

public interface NotificacionBandejaRepository extends JpaRepository<NotificacionBandeja, Long> {

    Page<NotificacionBandeja> findByUsuarioIdOrderByCreadaEnDesc(Integer usuarioId, Pageable pageable);

    long countByUsuarioIdAndLeidaEnIsNull(Integer usuarioId);

    Optional<NotificacionBandeja> findByBandejaIdAndUsuarioId(Long bandejaId, Integer usuarioId);

    @Modifying
    @Query("""
            update NotificacionBandeja b set b.leidaEn = :ahora
            where b.usuarioId = :usuarioId and b.leidaEn is null
            """)
    int marcarTodasLeidas(@Param("usuarioId") Integer usuarioId, @Param("ahora") Instant ahora);

    @Modifying
    @Query("delete from NotificacionBandeja b where b.creadaEn < :corte")
    int purgarAnterioresA(@Param("corte") Instant corte);

    @Modifying
    @Query("""
            delete from NotificacionBandeja b
            where b.usuarioId = :usuarioId and b.leidaEn is not null
            """)
    int eliminarLeidas(@Param("usuarioId") Integer usuarioId);
}
