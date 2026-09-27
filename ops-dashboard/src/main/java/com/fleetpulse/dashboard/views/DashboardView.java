package com.fleetpulse.dashboard.views;

import com.fleetpulse.dashboard.client.AlertDto;
import com.fleetpulse.dashboard.client.AlertServiceClient;
import com.fleetpulse.dashboard.client.FleetServiceClient;
import com.fleetpulse.dashboard.client.HealthEngineClient;
import com.fleetpulse.dashboard.client.HealthScoreDto;
import com.fleetpulse.dashboard.client.MaintenanceServiceClient;
import com.fleetpulse.dashboard.client.ReconciliationEntryDto;
import com.fleetpulse.dashboard.client.VehicleDto;
import com.fleetpulse.dashboard.client.WorkOrderDto;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;

import java.util.List;

@Route("")
@PageTitle("FleetPulse - Ops Dashboard")
public class DashboardView extends VerticalLayout implements BeforeEnterObserver {

    private final FleetServiceClient fleetServiceClient;
    private final AlertServiceClient alertServiceClient;
    private final HealthEngineClient healthEngineClient;
    private final MaintenanceServiceClient maintenanceServiceClient;

    private final Grid<VehicleDto> vehicleGrid = new Grid<>();
    private final Grid<HealthScoreDto> scoreGrid = new Grid<>();
    private final Grid<AlertDto> alertGrid = new Grid<>();
    private final Grid<WorkOrderDto> workOrderGrid = new Grid<>();
    private final Grid<ReconciliationEntryDto> reconciliationGrid = new Grid<>();

    private final Span vehicleCount = new Span("0");
    private final Span serviceNowCount = new Span("0");
    private final Span alertCount = new Span("0");
    private final Span overdueCount = new Span("0");

    public DashboardView(FleetServiceClient fleetServiceClient, AlertServiceClient alertServiceClient,
                          HealthEngineClient healthEngineClient, MaintenanceServiceClient maintenanceServiceClient) {
        this.fleetServiceClient = fleetServiceClient;
        this.alertServiceClient = alertServiceClient;
        this.healthEngineClient = healthEngineClient;
        this.maintenanceServiceClient = maintenanceServiceClient;

        setSizeFull();
        setPadding(false);
        setSpacing(false);
        getStyle().set("background", "var(--fp-page-bg)");
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
        configureGrids();

        Div content = new Div(buildStatsRow(),
                section("Vehicles", VaadinIcon.CAR, vehicleGrid),
                section("Recent health scores", VaadinIcon.CLIPBOARD_PULSE, scoreGrid),
                section("Recent alerts", VaadinIcon.BELL, alertGrid),
                workOrderSection(),
                section("Maintenance reconciliation", VaadinIcon.CHECK_SQUARE, reconciliationGrid));
        content.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "var(--lumo-space-l)")
                .set("max-width", "1200px")
                .set("width", "100%")
                .set("margin", "0 auto")
                .set("box-sizing", "border-box")
                .set("padding", "var(--lumo-space-l)");

        add(buildAppBar(), content);
    }

    private Component buildAppBar() {
        Icon logo = new Icon(VaadinIcon.TRUCK);
        logo.setColor("white");
        Span appName = new Span("FleetPulse");
        appName.getStyle().set("font-weight", "600").set("font-size", "var(--lumo-font-size-l)").set("color", "white");
        HorizontalLayout brand = new HorizontalLayout(logo, appName);
        brand.setAlignItems(Alignment.CENTER);

        Button refreshButton = new Button("Refresh", new Icon(VaadinIcon.REFRESH), event -> refreshData());
        refreshButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        refreshButton.getStyle().set("color", "white");
        Button logoutButton = new Button("Sign out", new Icon(VaadinIcon.SIGN_OUT), event -> signOut());
        logoutButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        logoutButton.getStyle().set("color", "white");
        HorizontalLayout actions = new HorizontalLayout(refreshButton, logoutButton);

        HorizontalLayout appBar = new HorizontalLayout(brand, actions);
        appBar.setWidthFull();
        appBar.setAlignItems(Alignment.CENTER);
        appBar.setJustifyContentMode(JustifyContentMode.BETWEEN);
        appBar.getStyle()
                .set("background", "var(--fp-appbar-bg)")
                .set("padding", "var(--lumo-space-m) var(--lumo-space-l)")
                .set("box-sizing", "border-box");
        return appBar;
    }

    private Component buildStatsRow() {
        Div row = new Div(
                statTile("Vehicles", vehicleCount, VaadinIcon.CAR, "lumo-primary-color"),
                statTile("Need service", serviceNowCount, VaadinIcon.WRENCH, "lumo-error-color"),
                statTile("Active alerts", alertCount, VaadinIcon.BELL, "lumo-warning-color"),
                statTile("Overdue maintenance", overdueCount, VaadinIcon.CLOCK, "lumo-error-color"));
        row.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "repeat(auto-fit, minmax(200px, 1fr))")
                .set("gap", "var(--lumo-space-m)");
        return row;
    }

    private Component statTile(String label, Span valueSpan, VaadinIcon vaadinIcon, String colorToken) {
        valueSpan.getStyle().set("font-size", "var(--lumo-font-size-xxl)").set("font-weight", "700");
        Span labelSpan = new Span(label);
        labelSpan.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "var(--lumo-font-size-s)");
        VerticalLayout text = new VerticalLayout(valueSpan, labelSpan);
        text.setPadding(false);
        text.setSpacing(false);

        Icon icon = new Icon(vaadinIcon);
        icon.getStyle().set("color", "var(--" + colorToken + ")");
        Div iconBadge = new Div(icon);
        iconBadge.getStyle()
                .set("background", "var(--" + colorToken + "-10pct)")
                .set("border-radius", "50%")
                .set("width", "48px")
                .set("height", "48px")
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("flex", "none");

        HorizontalLayout tile = new HorizontalLayout(iconBadge, text);
        tile.setAlignItems(Alignment.CENTER);
        tile.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("box-shadow", "var(--fp-card-shadow)")
                .set("padding", "var(--lumo-space-m)");
        return tile;
    }

    private Component section(String title, VaadinIcon vaadinIcon, Grid<?> grid) {
        Icon icon = new Icon(vaadinIcon);
        icon.getStyle().set("color", "var(--lumo-primary-text-color)");
        H2 heading = new H2(title);
        heading.getStyle().set("margin", "0").set("font-size", "var(--lumo-font-size-l)");
        HorizontalLayout headerRow = new HorizontalLayout(icon, heading);
        headerRow.setAlignItems(Alignment.CENTER);

        VerticalLayout card = new VerticalLayout(headerRow, grid);
        card.setPadding(true);
        card.setSpacing(true);
        card.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("box-shadow", "var(--fp-card-shadow)");
        return card;
    }

    private Component workOrderSection() {
        Icon icon = new Icon(VaadinIcon.WRENCH);
        icon.getStyle().set("color", "var(--lumo-primary-text-color)");
        H2 heading = new H2("Work orders");
        heading.getStyle().set("margin", "0").set("font-size", "var(--lumo-font-size-l)");
        HorizontalLayout titleGroup = new HorizontalLayout(icon, heading);
        titleGroup.setAlignItems(Alignment.CENTER);

        Button newWorkOrderButton = new Button("New work order", new Icon(VaadinIcon.PLUS), event -> openCreateWorkOrderDialog());
        newWorkOrderButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);

        HorizontalLayout headerRow = new HorizontalLayout(titleGroup, newWorkOrderButton);
        headerRow.setWidthFull();
        headerRow.setAlignItems(Alignment.CENTER);
        headerRow.setJustifyContentMode(JustifyContentMode.BETWEEN);

        VerticalLayout card = new VerticalLayout(headerRow, workOrderGrid);
        card.setPadding(true);
        card.setSpacing(true);
        card.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("box-shadow", "var(--fp-card-shadow)");
        return card;
    }

    private void openCreateWorkOrderDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("New work order");

        TextField vehicleIdField = new TextField("Vehicle ID");
        vehicleIdField.setWidthFull();
        TextField descriptionField = new TextField("Description");
        descriptionField.setWidthFull();
        FormLayout form = new FormLayout(vehicleIdField, descriptionField);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        dialog.add(form);

        Button cancelButton = new Button("Cancel", event -> dialog.close());
        Button createButton = new Button("Create", event -> {
            try {
                maintenanceServiceClient.createWorkOrder(currentToken(), vehicleIdField.getValue(), descriptionField.getValue());
                dialog.close();
                refreshData();
            } catch (Exception ex) {
                notifyFailure("work order", ex);
            }
        });
        createButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(cancelButton, createButton);

        dialog.open();
    }

    private void configureGrids() {
        vehicleGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_NO_BORDER);
        vehicleGrid.addColumn(VehicleDto::vin).setHeader("VIN").setAutoWidth(true);
        vehicleGrid.addColumn(VehicleDto::make).setHeader("Make").setAutoWidth(true);
        vehicleGrid.addColumn(VehicleDto::model).setHeader("Model").setAutoWidth(true);
        vehicleGrid.addColumn(VehicleDto::year).setHeader("Year").setAutoWidth(true);
        vehicleGrid.addColumn(VehicleDto::licensePlate).setHeader("Plate").setAutoWidth(true);
        vehicleGrid.addColumn(VehicleDto::status).setHeader("Status").setAutoWidth(true);
        vehicleGrid.setHeight("320px");

        scoreGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_NO_BORDER);
        scoreGrid.addColumn(HealthScoreDto::vehicleId).setHeader("Vehicle").setAutoWidth(true);
        scoreGrid.addColumn(HealthScoreDto::riskScore).setHeader("Risk score").setAutoWidth(true);
        scoreGrid.addComponentColumn(this::decisionBadge).setHeader("Decision").setAutoWidth(true);
        scoreGrid.addColumn(HealthScoreDto::scoredAt).setHeader("Scored at").setAutoWidth(true);
        scoreGrid.setHeight("320px");

        alertGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_NO_BORDER);
        alertGrid.addColumn(AlertDto::vehicleId).setHeader("Vehicle").setAutoWidth(true);
        alertGrid.addColumn(AlertDto::riskScore).setHeader("Risk score").setAutoWidth(true);
        alertGrid.addComponentColumn(this::severityBadge).setHeader("Severity").setAutoWidth(true);
        alertGrid.addColumn(AlertDto::raisedAt).setHeader("Raised at").setAutoWidth(true);
        alertGrid.addColumn(alert -> alert.notificationSent() ? "yes" : "suppressed (cooldown)").setHeader("Notified").setAutoWidth(true);
        alertGrid.setHeight("320px");

        workOrderGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_NO_BORDER);
        workOrderGrid.addColumn(WorkOrderDto::vehicleId).setHeader("Vehicle").setAutoWidth(true);
        workOrderGrid.addColumn(WorkOrderDto::description).setHeader("Description").setAutoWidth(true);
        workOrderGrid.addComponentColumn(order -> badge(order.status())).setHeader("Status").setAutoWidth(true);
        workOrderGrid.addColumn(WorkOrderDto::createdAt).setHeader("Created at").setAutoWidth(true);
        workOrderGrid.addColumn(order -> order.completedAt() == null ? "-" : order.completedAt()).setHeader("Completed at").setAutoWidth(true);
        workOrderGrid.setHeight("320px");

        reconciliationGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_NO_BORDER);
        reconciliationGrid.addColumn(ReconciliationEntryDto::vehicleId).setHeader("Vehicle").setAutoWidth(true);
        reconciliationGrid.addColumn(ReconciliationEntryDto::riskScore).setHeader("Risk score").setAutoWidth(true);
        reconciliationGrid.addColumn(ReconciliationEntryDto::alertRaisedAt).setHeader("Alert raised at").setAutoWidth(true);
        reconciliationGrid.addComponentColumn(entry -> badge(entry.status())).setHeader("Status").setAutoWidth(true);
        reconciliationGrid.setHeight("320px");
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
            case "SERVICE_NOW", "OVERDUE", "CANCELLED" -> "var(--lumo-error-color)";
            case "MONITOR", "PENDING", "OPEN", "IN_PROGRESS" -> "var(--lumo-warning-color)";
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
            List<VehicleDto> vehicles = fleetServiceClient.listVehicles(token);
            vehicleGrid.setItems(vehicles);
            vehicleCount.setText(String.valueOf(vehicles.size()));
        } catch (Exception ex) {
            notifyFailure("vehicles", ex);
        }
        try {
            List<HealthScoreDto> scores = healthEngineClient.recentScores(token);
            scoreGrid.setItems(scores);
            long needsService = scores.stream().filter(score -> "SERVICE_NOW".equals(score.decision())).count();
            serviceNowCount.setText(String.valueOf(needsService));
        } catch (Exception ex) {
            notifyFailure("health scores", ex);
        }
        try {
            List<AlertDto> alerts = alertServiceClient.recentAlerts(token);
            alertGrid.setItems(alerts);
            alertCount.setText(String.valueOf(alerts.size()));
        } catch (Exception ex) {
            notifyFailure("alerts", ex);
        }
        try {
            List<WorkOrderDto> workOrders = maintenanceServiceClient.recentWorkOrders(token);
            workOrderGrid.setItems(workOrders);
        } catch (Exception ex) {
            notifyFailure("work orders", ex);
        }
        try {
            List<ReconciliationEntryDto> reconciliation = maintenanceServiceClient.reconciliation(token);
            reconciliationGrid.setItems(reconciliation);
            long overdue = reconciliation.stream().filter(entry -> "OVERDUE".equals(entry.status())).count();
            overdueCount.setText(String.valueOf(overdue));
        } catch (Exception ex) {
            notifyFailure("maintenance reconciliation", ex);
        }
    }

    private String currentToken() {
        return (String) VaadinSession.getCurrent().getAttribute("token");
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
