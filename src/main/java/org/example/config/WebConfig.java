package org.example.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

/**
 * Makes the JSON converters tolerant of Snowflake's custom {@code application/snowflake} media type,
 * which the driver uses for the both the {@code Accept} and {@code Content-Type} headers. Without this,
 * Spring would reject driver requests with 406/415.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    private static final MediaType SNOWFLAKE = MediaType.parseMediaType("application/snowflake");

    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer.ignoreAcceptHeader(true).defaultContentType(MediaType.APPLICATION_JSON);
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters){
        for(HttpMessageConverter<?> converter : converters){
            if (converter instanceof AbstractHttpMessageConverter<?> jsonConverter
                    && supportsJson(jsonConverter)) {
                List<MediaType> mediaTypes = new ArrayList<>(jsonConverter.getSupportedMediaTypes());
                if (!mediaTypes.contains(SNOWFLAKE)) {
                    mediaTypes.add(SNOWFLAKE);
                    jsonConverter.setSupportedMediaTypes(mediaTypes);
                }
            }
        }
    }

    private static boolean supportsJson(AbstractHttpMessageConverter<?> converter) {
        return converter.getSupportedMediaTypes()
                .stream()
                .anyMatch(mt-> mt.getSubtype().toLowerCase().contains("json"));
    }
}
