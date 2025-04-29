package com.example.chatservice.kafka;

import com.example.chatservice.dto.ChatMessage;
import com.example.chatservice.entity.ChatMessageEntity;
import com.example.chatservice.repository.ChatMessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import static com.example.chatservice.config.KafkaConfig.CHAT_TOPIC;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatConsumer {

    private final ChatMessageRepository chatMessageRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper(); // ✅ JSON 파싱용

    @KafkaListener(topics = CHAT_TOPIC, groupId = "chat-group")
    public void consume(ConsumerRecord<String, String> record) { // ✅ ConsumerRecord로 변경
        try {
            String message = record.value(); // ✅ Kafka 메시지 value 꺼내기
            ChatMessage chatMessage = objectMapper.readValue(message, ChatMessage.class); // ✅ JSON -> ChatMessage 변환

            // ✅ DB 저장
            chatMessageRepository.save(
                    ChatMessageEntity.builder()
                            .roomId(chatMessage.getRoomId())
                            .sender(chatMessage.getSender())
                            .content(chatMessage.getContent())
                            .timestamp(java.time.LocalDateTime.now().toString())
                            .build()
            );

            log.info("✅ Kafka 수신 및 저장 완료 - roomId: {}, sender: {}, content: {}",
                    chatMessage.getRoomId(), chatMessage.getSender(), chatMessage.getContent());

            // ✅ WebSocket STOMP로 브로드캐스트
            messagingTemplate.convertAndSend(
                    "/topic/chat/room/" + chatMessage.getRoomId(),
                    chatMessage
            );

            log.info("✅ WebSocket STOMP broadcast 완료 - topic: /topic/chat/room/{}", chatMessage.getRoomId());

        } catch (Exception e) {
            log.error("❌ Kafka 메시지 처리 실패", e);
        }
    }
}
