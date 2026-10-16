package com.scalelink.urlservice.application.port.out;

import com.scalelink.urlservice.domain.model.ClickEvent;

public interface ClickEventPublisherPort {

    void publish(ClickEvent event);
}
