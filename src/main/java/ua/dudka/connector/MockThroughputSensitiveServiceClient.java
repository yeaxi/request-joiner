package ua.dudka.connector;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import lombok.SneakyThrows;
import ua.dudka.model.Event;

import javax.inject.Singleton;
import javax.naming.ServiceUnavailableException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Singleton
public class MockThroughputSensitiveServiceClient implements ExternalServiceClient {

    static final int MAX_THROUGHPUT = 1000;

    private volatile int second = 0;
    private volatile boolean isServiceDown = false;

    private final Lock lock = new ReentrantLock();
    private final AtomicInteger rps = new AtomicInteger(0);

    @Override
    @SneakyThrows
    public List<Event> getByIds(List<String> ids) {
        if (isServiceDown) {
            throw new ServiceUnavailableException("Exceeded max throughput");
        }

        int currentSecond = LocalDateTime.now().getSecond();
        if (this.second == currentSecond) {
            incrementAndCheck();
        } else {
            incrementOrReset(currentSecond);
        }
        return ids.stream()
                .map(id -> new Event(id, JsonNodeFactory.instance.numberNode(1)))
                .collect(Collectors.toList());
    }

    private void incrementOrReset(int currentSecond) throws ServiceUnavailableException {
        lock.lock();
        if (this.second != currentSecond) {
            resetRps(currentSecond);
        }
        lock.unlock();

        incrementAndCheck();
    }

    private void resetRps(int currentSecond) {
        this.second = currentSecond;
        this.rps.set(0);
    }

    private void incrementAndCheck() throws ServiceUnavailableException {
        int current = rps.incrementAndGet();
        if (current > MAX_THROUGHPUT) {
            isServiceDown = true;
            throw new ServiceUnavailableException("Exceeded max throughput");
        }
    }
}
