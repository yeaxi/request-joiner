package ua.dudka.connector;

import ua.dudka.model.Event;

import java.util.List;

public interface ExternalServiceClient {

    List<Event> getByIds(List<String> ids);
}
