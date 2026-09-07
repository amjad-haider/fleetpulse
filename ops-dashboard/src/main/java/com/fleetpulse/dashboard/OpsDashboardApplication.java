package com.fleetpulse.dashboard;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.theme.Theme;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Theme("fleetpulse")
public class OpsDashboardApplication implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(OpsDashboardApplication.class, args);
    }
}
