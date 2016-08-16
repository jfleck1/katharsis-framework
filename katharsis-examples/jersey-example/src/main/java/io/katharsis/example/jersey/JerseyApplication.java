package io.katharsis.example.jersey;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.katharsis.example.jersey.domain.repository.ProjectRepository;
import io.katharsis.example.jersey.domain.repository.TaskToProjectRepository;
import io.katharsis.rs.KatharsisProperties;
import io.katharsis.rs.RsServiceUrlProvider;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.process.internal.RequestScoped;
import org.glassfish.jersey.server.ResourceConfig;

import javax.inject.Singleton;
import javax.ws.rs.ApplicationPath;

@ApplicationPath("/")
public class JerseyApplication extends ResourceConfig {

    public static final String APPLICATION_URL = "http://localhost:8080";

    public JerseyApplication() {
        property(KatharsisProperties.RESOURCE_SEARCH_PACKAGE, "io.katharsis.example.jersey.domain");
        register(KatharsisDynamicFeature.class);
        register(RsServiceUrlProvider.class);
        register(new AbstractBinder() {
            @Override
            public void configure() {
                bindFactory(ObjectMapperFactory.class).to(ObjectMapper.class).in(Singleton.class);

                bindService(ProjectRepository.class);
                bindService(TaskToProjectRepository.class);
            }

            private void bindService(Class<?> serviceType) {
                bind(serviceType).to(serviceType).proxy(true).in(RequestScoped.class);
            }
        });

    }
}
