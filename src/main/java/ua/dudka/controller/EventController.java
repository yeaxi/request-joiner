package ua.dudka.controller;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.dudka.model.Event;
import ua.dudka.service.EventService;

import java.util.Optional;

@Slf4j
@Controller("/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @Get("/{id}")
    public Optional<Event> getEventById(@PathVariable String id) {
        log.debug("Get event by id: {}", id);
        return eventService.getEventById(id);
    }
}