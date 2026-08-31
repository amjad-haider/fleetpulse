package com.fleetpulse.dashboard.views;

import com.fleetpulse.dashboard.client.AlertDto;
import com.fleetpulse.dashboard.client.AlertServiceClient;
import com.fleetpulse.dashboard.client.FleetServiceClient;
import com.fleetpulse.dashboard.client.HealthEngineClient;
import com.fleetpulse.dashboard.client.HealthScoreDto;
import com.fleetpulse.dashboard.client.VehicleDto;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;

@Route("")
@PageTitle("FleetPulse - Ops Dashboard")
public class DashboardView extends VerticalLayout implements BeforeEnterObserver {

    private final FleetServiceClient fleetServiceClient;
    private final AlertServiceClient alertServiceClient;
    private final HealthEngineClient healthEngineClient;

    private final Grid<VehicleDto> vehicleGrid = new Grid<>();
    private final Grid<HealthScoreDto> scoreGrid = new Grid<>();
    private final Grid<AlertDto> alertGrid = new Grid<>();

    public DashboardView(FleetServiceClient fleetServiceClient, AlertServiceClient alertServiceClient, HealthEngineClient healthEngineClient) {
        this.fleetServiceClient = fleetServiceClient;
        this.alertServiceClient = alertServiceClient;
        this.healthEngineClient = healthEngineClient;

        setSizeFull();
        buildUi();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (VaadinSession.getCurrent().getAttribute("token") == null) {
            event.forwardTo(LoginView.class);
            return;
        }
        refreshData();
    }

    private void buildUi() {
        Button refreshButton = new Button("Refresh", event -> refreshData());
        Button logoutButton = new Button("Sign out", event -> signOut());

        HorizontalLayout header = new HorizontalLayout(new H1("FleetPulse Ops"), refreshButton, logoutButton);
        header.setAlignItems(Alignment.CENTER);
        header.setWidthFull();
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);

        vehicleGrid.addColumn(VehicleDto::vin).setHeader("VIN").setAutoWidth(true);
        vehicleGrid.addColumn(VehicleDto::make).setHeader("Make").setAutoWidth(true);
        vehicleGrid.addColumn(VehicleDto::model).setHeader("Model").setAutoWidth(true);
        vehicleGrid.addColumn(VehicleDto::year).setHeader("Year").setAutoWidth(true);
        vehicleGrid.addColumn(VehicleDto::licensePlate).setHeader("Plate").setAutoWidth(true);
        vehicleGrid.addColumn(VehicleDto::status).setHeader("Status").setAutoWidth(true);
        vehicleGrid.setAllRowsVisible(true);

        scoreGrid.addColumn(HealthScoreDto::vehicleId).setHeader("Vehicle").setAutoWidth(true);
        scoreGrid.addColumn(HealthScoreDto::riskScore).setHeader("Risk score").setAutoWidth(true);
        scoreGrid.addComponentColumn(this::decisionBadge).setHeader("Decision").setAutoWidth(true);
        scoreGrid.addColumn(HealthScoreDto::scoredAt).setHeader("Scored at").setAutoWidth(true);
        scoreGrid.setAllRowsVisible(true);

        alertGrid.addColumn(AlertDto::vehicleId).setHeader("Vehicle").setAutoWidth(true);
        alertGrid.addColumn(AlertDto::riskScore).setHeader("Risk score").setAutoWidth(true);
        alertGrid.addComponentColumn(this::severityBadge).setHeader("Severity").setAutoWidth(true);
        alertGrid.addColumn(AlertDto::raisedAt).setHeader("Raised at").setAutoWidth(true);
        alertGrid.addColumn(alert -> alert.notificationSent() ? "yes" : "suppressed (cooldown)").setHeader("Notified").setAutoWidth(true);
        alertGrid.setAllRowsVisible(true);

        add(header, new H2("Vehicles"), vehicleGrid, new H2("Recent health scores"), scoreGrid, new H2("Recent alerts"), alertGrid);
    }

    private Span decisionBadge(HealthScoreDto score) {
        return badge(score.decision());
    }

    private Span severityBadge(AlertDto alert) {
        return badge(alert.severity());
    }

    private Span badge(String value) {
        Span badge = new Span(value);
        String color = switch (value) {
            case "SERVICE_NOW" -> "var(--lumo-error-color)";
            case "MONITOR" -> "var(--lumo-warning-color)";
            default -> "var(--lumo-success-color)";
        };
        badge.getStyle()
                .set("color", "white")
                .set("background-color", color)
                .set("padding", "2px 8px")
                .set("border-radius", "8px")
                .set("font-size", "var(--lumo-font-size-s)");
        return badge;
    }

    private void refreshData() {
        String token = (String) VaadinSession.getCurrent().getAttribute("token");
        try {
            vehicleGrid.setItems(fleetServiceClient.listVehicles(token));
        } catch (Exception ex) {
            notifyFailure("vehicles", ex);
        }
        try {
            scoreGrid.setItems(healthEngineClient.recentScores(token));
        } catch (Exception ex) {
            notifyFailure("health scores", ex);
        }
        try {
            alertGrid.setItems(alertServiceClient.recentAlerts(token));
        } catch (Exception ex) {
            notifyFailure("alerts", ex);
        }
    }

    private void notifyFailure(String what, Exception ex) {
        Notification notification = Notification.show("Couldn't load " + what + ": " + ex.getMessage(), 5000, Notification.Position.BOTTOM_END);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    private void signOut() {
        VaadinSession.getCurrent().setAttribute("token", null);
        VaadinSession.getCurrent().setAttribute("role", null);
        UI.getCurrent().navigate(LoginView.class);
    }
}
