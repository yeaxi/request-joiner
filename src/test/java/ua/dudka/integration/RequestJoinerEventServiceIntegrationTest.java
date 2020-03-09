package ua.dudka.integration;

import io.micronaut.test.annotation.MicronautTest;
import io.micronaut.test.annotation.MockBean;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import ua.dudka.Application;
import ua.dudka.config.properties.RequestJoinerProperties;
import ua.dudka.connector.ExternalServiceClient;
import ua.dudka.connector.MockThroughputSensitiveServiceClient;
import ua.dudka.model.Event;
import ua.dudka.service.EventService;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Slf4j
@MicronautTest(application = Application.class)
class RequestJoinerEventServiceIntegrationTest {

    private static final String EVENT_ID = "Id";
    private static final String NONEXISTENT_EVENT_ID = "nonexistent event id";

    @Inject
    private EventService eventService;

    @Inject
    private RequestJoinerProperties properties;

    @Inject
    private ExternalServiceClient client;


    @Test
    void reducesRpsToExternalSystemByTimeout() {
        int batchSize = properties.getEventsBatchSize() - 1;
        int requestGroupCount = 10;

        List<Future<Optional<Event>>> events = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(batchSize);

        for (int i = 1; i <= requestGroupCount; i++) {
            for (int j = 1; j <= batchSize; j++) {
                final String eventId = i + "" + j;
                events.add(executor.submit(() -> eventService.getEventById(eventId)));
            }
            pause(properties.getBatchCallTimeout());
        }

        long processedEventsCount = events.stream()
                .map(this::safeGet)
                .filter(Optional::isPresent)
                .count();

        assertThat(processedEventsCount).isEqualTo(batchSize * requestGroupCount);
        verify(client, atMost(requestGroupCount)).getByIds(anyList());
    }

    @Test
    void reducesRpsToExternalSystemByBatchOrTimeout() {
        int batchSize = properties.getEventsBatchSize();
        int requestGroupCount = 1000;

        int eventsCount = batchSize * requestGroupCount;

        List<Future<Optional<Event>>> events = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(requestGroupCount);

        for (long i = 1; i <= eventsCount; i++) {
            final String eventId = Long.toString(i);
            events.add(executor.submit(() -> eventService.getEventById(eventId)));
        }

        long processedEventsCount = events.stream()
                .map(this::safeGet)
                .filter(Optional::isPresent)
                .count();

        assertThat(processedEventsCount).isEqualTo(eventsCount);
        verify(client, atMost(requestGroupCount + 5)).getByIds(anyList()); //service doesn't guarantee exact requests reduction
    }

    @SneakyThrows
    private Optional<Event> safeGet(Future<Optional<Event>> e) {
        return e.get();
    }


    @SneakyThrows
    private void pause(long timeout) {
        TimeUnit.MILLISECONDS.sleep(timeout);
    }

    @MockBean(MockThroughputSensitiveServiceClient.class)
    ExternalServiceClient client() {
        ExternalServiceClient client = mock(ExternalServiceClient.class);

        when(client.getByIds(anyList())).thenAnswer(this::getMockAnswer);
        when(client.getByIds(eq(Collections.singletonList(NONEXISTENT_EVENT_ID)))).thenReturn(Collections.emptyList());

        return client;
    }

    private List<Event> getMockAnswer(InvocationOnMock invocation) {
        return ((List<String>) invocation.getArgument(0)).stream()
                .map(e -> new Event(e, null))
                .collect(Collectors.toList());
    }
}