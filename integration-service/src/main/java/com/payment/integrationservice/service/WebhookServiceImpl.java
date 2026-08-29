package com.payment.integrationservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.integrationservice.entity.WebhookEvent;
import com.payment.integrationservice.repository.WebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookServiceImpl implements WebhookService{

    private final WebhookEventRepository repo;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${kafka.topic.webhook-events:webhook.events}")
    private String webhookTopic;

    @Transactional
    @Override
    public Map<String, Object> handleStripeWebhook(String payload, String signatureHeader, String endpointSecret) {

        try {
            JsonNode json = objectMapper.readTree(payload);
            String providerEventId = json.path("id").asText();
            String type = json.path("type").asText();
           // Gertting existing transaction
            Optional<WebhookEvent> existing = repo.findByProviderEventId(providerEventId);
            if (existing.isPresent()) {
                return buildEventMap(existing.get());
            }

            WebhookEvent entity = WebhookEvent.builder()
                    .id(null)
                    .provider("stripe")
                    .providerEventId(providerEventId)
                    .eventType(type)
                    .payload(json)
                    .processingStatus("received")
                    .build();
            log.info("Saving record in DB for: {}", entity.getId());
            entity = repo.save(entity);

            // Publish event
            publishEvent(json, providerEventId, type);

            entity.setProcessingStatus("published");
            entity.setProcessedAt(OffsetDateTime.now());
            log.info("Updated processing status for: {}",entity.getId());
            repo.save(entity);

            return buildEventMap(entity);
        }catch (JsonProcessingException e){
            log.error("kafka event serialization failed", e);
            Map<String, Object> resp = new HashMap<>();
            resp.put("status", "Failed");
            resp.put("message", "Failed to emit kafka event");
            return resp;
        }

        catch (Exception e) {
            log.error("Webhook processing failed", e);
            Map<String, Object> resp = new HashMap<>();
            resp.put("status", "Failed");
            resp.put("message", "Failed to process webhook");
            return resp;
        }
    }



    private void publishEvent(JsonNode json, String providerEventId, String type) throws JsonProcessingException {
        var objectNode = json.path("data").path("object");
        Map<String, Object> eventMap = new HashMap<>();
        eventMap.put("provider", "stripe");
        eventMap.put("providerEventId", providerEventId);
        eventMap.put("type", type);
        eventMap.put("object", objectNode);
        kafkaTemplate.send(webhookTopic, providerEventId, objectMapper.writeValueAsString(eventMap));
        log.info("Published kafka event {}",eventMap);
    }

    private Map<String, Object> buildEventMap(WebhookEvent e) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", e.getId());
        data.put("provider", e.getProvider());
        data.put("providerEventId", e.getProviderEventId());
        data.put("eventType", e.getEventType());
        data.put("processingStatus", e.getProcessingStatus());
        data.put("receivedAt", e.getReceivedAt());
        data.put("processedAt", e.getProcessedAt());
        data.put("success", true);
        return data;
    }
}
