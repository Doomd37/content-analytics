package com.contentanalytics.config;

import com.contentanalytics.interceptor.WebSocketInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;


@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketInterceptor webSocketInterceptor;

    /**
     * Register STOMP endpoints
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/documents")
                .setAllowedOrigins("http://localhost:3000", "http://localhost:3001", "http://127.0.0.1:3000")
                .withSockJS();

        registry.addEndpoint("/ws/notifications")
                .setAllowedOrigins("http://localhost:3000", "http://localhost:3001", "http://127.0.0.1:3000")
                .withSockJS();

        registry.addEndpoint("/ws/analytics")
                .setAllowedOrigins("http://localhost:3000", "http://localhost:3001", "http://127.0.0.1:3000")
                .withSockJS();
    }

    /**
     * Configure message broker
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    /**
     * Register WebSocket interceptor for authentication
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketInterceptor);
     }
    }