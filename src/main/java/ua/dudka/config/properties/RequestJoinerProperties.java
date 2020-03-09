package ua.dudka.config.properties;

import io.micronaut.context.annotation.ConfigurationProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Min;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties("request-joiner")
public class RequestJoinerProperties {

    private long requestTimeout;

    @Min(1L)
    private int eventsBatchSize;

    private int batchCallTimeout;

}
