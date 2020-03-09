package ua.dudka.connector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.naming.ServiceUnavailableException;
import java.util.Collections;
import java.util.Objects;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static ua.dudka.connector.MockThroughputSensitiveServiceClient.MAX_THROUGHPUT;

class MockThroughputSensitiveServiceClientTest {

    private ExternalServiceClient client;

    @BeforeEach
    void setUp() {
        client = new MockThroughputSensitiveServiceClient();
    }

    @Test
    void returnsResponseWithinMaxThroughput() {
        executeTimes(MAX_THROUGHPUT);
    }

    private void executeTimes(int count) {
        IntStream.rangeClosed(1, count).parallel()
                .mapToObj(Objects::toString)
                .map(Collections::singletonList)
                .forEach(client::getByIds);
    }

    @Test
    void throwsServiceUnavailableIfExceedsMaxThroughput() {
        assertThatThrownBy(() -> executeTimes(MAX_THROUGHPUT * 2))
                .isInstanceOf(ServiceUnavailableException.class);
    }
}