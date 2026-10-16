package com.scalelink.urlservice.application.port.in;

public interface RedirectUrlUseCase {

    record ResolveUrlQuery(
            String shortCode,
            String clientIp,
            String userAgent,
            String referer
    ) {}

    String resolveUrl(ResolveUrlQuery query);
}
