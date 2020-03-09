package ua.dudka.service;

import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import ua.dudka.config.properties.RequestJoinerProperties;
import ua.dudka.connector.ExternalServiceClient;
import ua.dudka.model.Event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestJoinerEventServiceTest {

    private static final String NONEXISTENT_EVENT_ID = "nonexistent event id";

    private EventService eventService;

    private RequestJoinerProperties properties;

    private ExternalServiceClient client;

    @BeforeEach
    void setUp() {
        client = mock(ExternalServiceClient.class);

        when(client.getByIds(anyList())).thenAnswer(this::getMockAnswer);
        when(client.getByIds(eq(Collections.singletonList(NONEXISTENT_EVENT_ID)))).thenReturn(Collections.emptyList());

        properties = RequestJoinerProperties.builder()
                .eventsBatchSize(20)
                .requestTimeout(150)
                .build();

        eventService = new RequestJoinerEventService(client, properties);
    }

    private List<Event> getMockAnswer(InvocationOnMock invocation) {
        return ((List<String>) invocation.getArgument(0)).stream()
                .map(e -> new Event(e, null))
                .collect(Collectors.toList());
    }

    @Test
    void returnsEmptyOptionalIfEventWasNotReturnedWithinTimeout() {
        Optional<Event> event = eventService.getEventById(NONEXISTENT_EVENT_ID);

        assertThat(event).isNotPresent();
    }

    @Test
    void reducesRpsToExternalSystemByBatch() {
        int batchSize = properties.getEventsBatchSize();
        int requestGroupCount = 10;

        int eventsCount = batchSize * requestGroupCount + 1; // + one event to init batch retrieving process

        List<Future<Optional<Event>>> events = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(eventsCount);

        for (int i = 1; i <= eventsCount; i++) {
            final String eventId = Integer.toString(i);
            events.add(executor.submit(() -> eventService.getEventById(eventId)));
        }

        long processedEventsCount = events.stream()
                .map(this::safeGet)
                .filter(Optional::isPresent)
                .count();

        assertThat(processedEventsCount).isEqualTo(eventsCount - 1); // one event will be moved to next batch, so it won't be processed
        verify(client, atMost(requestGroupCount)).getByIds(anyList());
    }

    @SneakyThrows
    private Optional<Event> safeGet(Future<Optional<Event>> e) {
        return e.get();
    }
}