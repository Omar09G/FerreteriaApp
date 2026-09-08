package mx.ferreteria.api.cfg.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import mx.ferreteria.api.cfg.entity.TicketConfig;

public interface TicketConfigRepository extends JpaRepository<TicketConfig, Integer> {

    Optional<TicketConfig> findByAlmacenId(Integer almacenId);

    Optional<TicketConfig> findByAlmacenIdIsNull();
}
