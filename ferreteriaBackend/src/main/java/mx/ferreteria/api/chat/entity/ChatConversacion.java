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
@Table(name = "chat_conversacion", schema = "notif")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatConversacion {

    public static final String TIPO_DIRECTA = "DIRECTA";
    public static final String TIPO_GRUPO = "GRUPO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long conversacionId;

    @Column(nullable = false, length = 16)
    private String tipo;

    @Column(length = 120)
    private String titulo;

    @Column(name = "creada_por", nullable = false)
    private Integer creadaPor;

    @Column(name = "creada_en", nullable = false)
    @Builder.Default
    private Instant creadaEn = Instant.now();
}
