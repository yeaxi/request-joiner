package ua.dudka.model;

import com.fasterxml.jackson.databind.JsonNode;
import io.micronaut.core.annotation.Introspected;
import lombok.Value;

@Value
@Introspected
public class Event {

    private String id;

    private JsonNode data;

}