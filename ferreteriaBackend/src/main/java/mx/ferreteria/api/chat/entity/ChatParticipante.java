package mx.ferreteria.api.chat.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "chat_participante", schema = "notif")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatParticipante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long participanteId;

    @Column(name = "conversacion_id", nullable = false)
    private Long conversacionId;

    @Column(name = "usuario_id", nullable = false)
    private Integer usuarioId;

    @Column(name = "ultimo_leido_en")
    private Instant ultimoLeidoEn;

    @Column(name = "creado_en", nullable = false)
    @Builder.Default
    private Instant creadoEn = Instant.now();
}
