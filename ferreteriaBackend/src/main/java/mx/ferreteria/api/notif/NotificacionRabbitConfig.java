package mx.ferreteria.api.notif;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.notif.config.NotificacionProperties;

/**
 * Wiring RabbitMQ para notificaciones. Solo activo con
 * {@code app.notif.enabled=true} (compose / prod). En local/tests el listener
 * no arranca (spring.rabbitmq.listener.simple.auto-startup=false) y el
 * reconciler reintenta cuando se habilite.
 *
 * <p>El {@link MessageConverter} único lo recogen el RabbitTemplate y el
 * listener container del auto-config de Boot (no se define rabbitTemplate
 * propio: colisionaría con el de Boot, que prohíbe overriding).
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.notif.enabled", havingValue = "true")
public class NotificacionRabbitConfig {

    private final NotificacionProperties props;

    @Bean
    public TopicExchange notificacionExchange() {
        return new TopicExchange(props.rabbit().exchange(), true, false);
    }

    @Bean
    public DirectExchange notificacionDlx() {
        return new DirectExchange(props.rabbit().exchange() + ".dlx", true, false);
    }

    @Bean
    public Queue notificacionQueue() {
        return QueueBuilder.durable(props.rabbit().queue())
                .withArgument("x-dead-letter-exchange", props.rabbit().exchange() + ".dlx")
                .withArgument("x-dead-letter-routing-key", props.rabbit().dlq())
                .build();
    }

    @Bean
    public Queue notificacionDlq() {
        return QueueBuilder.durable(props.rabbit().dlq()).build();
    }

    @Bean
    public Binding notificacionBinding(Queue notificacionQueue, TopicExchange notificacionExchange) {
        return BindingBuilder.bind(notificacionQueue)
                .to(notificacionExchange)
                .with(props.rabbit().routingKey());
    }

    @Bean
    public Binding notificacionDlqBinding(Queue notificacionDlq, DirectExchange notificacionDlx) {
        return BindingBuilder.bind(notificacionDlq)
                .to(notificacionDlx)
                .with(props.rabbit().dlq());
    }

    @Bean
    public MessageConverter notificacionMessageConverter(ObjectMapper om) {
        return new Jackson2JsonMessageConverter(om);
    }
}
