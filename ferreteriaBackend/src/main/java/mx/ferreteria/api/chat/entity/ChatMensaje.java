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
@Table(name = "chat_mensaje", schema = "notif")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMensaje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long mensajeId;

    @Column(name = "conversacion_id", nullable = false)
    private Long conversacionId;

    @Column(name = "autor_id", nullable = false)
    private Integer autorId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String cuerpo;

    @Column(name = "creada_en", nullable = false)
    @Builder.Default
    private Instant creadaEn = Instant.now();

    @Column(name = "eliminada_en")
    private Instant eliminadaEn;
}
