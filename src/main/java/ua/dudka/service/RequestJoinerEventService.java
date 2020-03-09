package ua.dudka.service;

import io.micronaut.scheduling.annotation.Scheduled;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import ua.dudka.config.properties.RequestJoinerProperties;
import ua.dudka.connector.ExternalServiceClient;
import ua.dudka.model.Event;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.Thread.sleep;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

@Slf4j
@Singleton
public class RequestJoinerEventService implements EventService {

    private final ExternalServiceClient client;
    private final RequestJoinerProperties properties;

    private final ArrayBlockingQueue<String> pendingEventIds;
    private final Map<String, Event> availableEvents;

    public RequestJoinerEventService(ExternalServiceClient client, RequestJoinerProperties properties) {
        log.info("Properties: {}", properties);
        this.client = client;
        this.properties = properties;
        this.pendingEventIds = new ArrayBlockingQueue<>(properties.getEventsBatchSize());
        this.availableEvents = new ConcurrentHashMap<>(properties.getEventsBatchSize());
    }

    @Override
    public Optional<Event> getEventById(String id) {
        long startTime = System.nanoTime();

        while (!pendingEventIds.offer(id)) {
            if (!retrieveEventsIfHasTime(id, startTime)) {
                return Optional.empty();
            }
        }

        return getEvent(id, startTime);
    }

    @Scheduled(fixedRate = "${request-joiner.batch-call-timeout}ms")
    void retrieveEventsBySchedule() {
        if (!pendingEventIds.isEmpty()) {
            List<String> ids = getBatchEventIds();
            if (!ids.isEmpty()) {
                client.getByIds(ids).forEach(event -> availableEvents.put(event.getId(), event));
            }
        }
    }

    private boolean retrieveEventsIfHasTime(String id, long startTime) {
        if (requestIsNotTimedOut(startTime)) {
            retrieveEventsInBatch(startTime);
            return true;
        } else {
            log.error("Request timed out for event: {}", id);
            return false;
        }
    }

    private void retrieveEventsInBatch(long startTime) {
        if (pendingEventIds.size() < properties.getEventsBatchSize()) {
            return;
        }

        List<String> batch = getBatchEventIds();
        if (batch.size() == properties.getEventsBatchSize()) {
            client.getByIds(batch).forEach(event -> availableEvents.put(event.getId(), event));
        } else if (!batch.isEmpty()) {
            for (String id : batch) {
                boolean isAdded = false;
                while (!isAdded && requestIsNotTimedOut(startTime)) {
                    isAdded = pendingEventIds.offer(id);
                }
            }
        }
    }

    private List<String> getBatchEventIds() {
        return getBatchEventIds(properties.getEventsBatchSize());
    }

    private List<String> getBatchEventIds(int size) {
        List<String> ids = new ArrayList<>(size);
        pendingEventIds.drainTo(ids, size);
        return ids;
    }

    private Optional<Event> getEvent(String id, long startTime) {
        Optional<Event> event = waitForEvent(id, startTime);
        if (!event.isPresent()) {
            log.error("Event with id: {} was not found", id);
        }

        availableEvents.remove(id);
        return event;
    }

    @SneakyThrows
    private Optional<Event> waitForEvent(String id, long startTime) {
        Event event = null;
        while (event == null && requestIsNotTimedOut(startTime)) {
            event = availableEvents.get(id);
            sleep(1);
        }
        return Optional.ofNullable(event);
    }

    private boolean requestIsNotTimedOut(long requestTime) {
        long processingTime = getRequestProcessingTime(requestTime);

        return processingTime < properties.getRequestTimeout();
    }

    private long getRequestProcessingTime(long requestTime) {
        long now = System.nanoTime();
        return NANOSECONDS.toMillis(now - requestTime);
    }
}