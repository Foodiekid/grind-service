package com.grindandtrain.worker.common.messaging;

import java.util.Map;

/**
 * Body of a Pub/Sub push delivery. {@code data} arrives base64 encoded and is decoded into bytes.
 *
 * @author Dheeraj_Edupuganti
 */
public record PubSubPushRequest(Message message, String subscription) {

    public record Message(byte[] data, String messageId, Map<String, String> attributes) {

        public Message {
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        }
    }
}
