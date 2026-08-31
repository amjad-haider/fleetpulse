package com.fleetpulse.dashboard;

import com.vaadin.flow.component.page.AppShellConfigurator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OpsDashboardApplication implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(OpsDashboardApplication.class, args);
    }
}
