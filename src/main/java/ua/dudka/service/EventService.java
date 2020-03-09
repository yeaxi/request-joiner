package ua.dudka.service;

import ua.dudka.model.Event;

import java.util.Optional;

public interface EventService {

    Optional<Event> getEventById(String id);
}
