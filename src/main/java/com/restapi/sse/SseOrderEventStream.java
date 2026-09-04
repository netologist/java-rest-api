package com.restapi.sse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Server-Sent Events (SSE) Stream Manager.
 * Dispatches live order lifecycle updates to connected clients using Virtual Threads.
 */
@Service
public class SseOrderEventStream implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SseOrderEventStream.class);

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public SseEmitter createEmitter() {
        // 30 minutes timeout
        SseEmitter emitter = new SseEmitter(1_800_000L);
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            emitter.complete();
        });
        emitter.onError(e -> emitters.remove(emitter));

        // Send initial connection event
        try {
            emitter.send(SseEmitter.event().name("connected").data("SSE Connection Established"));
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        return emitter;
    }

    public void broadcastEvent(String eventName, Object data) {
        if (emitters.isEmpty()) return;

        virtualExecutor.submit(() -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().name(eventName).data(data));
                } catch (Exception e) {
                    emitters.remove(emitter);
                }
            }
        });
    }

    public int activeEmitterCount() {
        return emitters.size();
    }

    @Override
    public void close() {
        virtualExecutor.close();
    }
}
