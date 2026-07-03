package com.vietlancer.file;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Serve file đã upload dưới đường dẫn public /files/**. */
@Configuration
@RequiredArgsConstructor
public class FileWebConfig implements WebMvcConfigurer {

    private final FileStorageService fileStorageService;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/files/**")
                .addResourceLocations(fileStorageService.dir().toUri().toString())
                .setCachePeriod(3600);
    }
}
