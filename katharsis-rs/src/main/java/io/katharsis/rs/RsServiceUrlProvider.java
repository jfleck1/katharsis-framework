package io.katharsis.rs;

import io.katharsis.resource.registry.ServiceUrlProvider;

import javax.annotation.Resource;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.Provider;

@Provider
public class RsServiceUrlProvider implements ServiceUrlProvider {

    @Resource
    private UriInfo request;

    private String pathPrefix;



    public RsServiceUrlProvider(String pathPrefix) {
        this.pathPrefix = pathPrefix;
    }

    @Override
    public String getUrl() {
        String scheme = request.getPath();
        String host = request.getBaseUri().toString();
        return scheme + "://" + host + pathPrefix;
    }
}