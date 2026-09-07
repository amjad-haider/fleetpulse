package com.fleetpulse.dashboard.views;

import com.fleetpulse.dashboard.client.FleetServiceClient;
import com.fleetpulse.dashboard.client.LoginResponse;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.web.client.RestClientException;

@Route("login")
@PageTitle("FleetPulse - Sign in")
public class LoginView extends VerticalLayout {

    public LoginView(FleetServiceClient fleetServiceClient) {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);
        getStyle().set("background", "var(--fp-page-bg)");

        Div badge = new Div(new Icon(VaadinIcon.TRUCK));
        badge.getStyle()
                .set("background", "var(--lumo-primary-color-10pct)")
                .set("color", "var(--lumo-primary-text-color)")
                .set("border-radius", "50%")
                .set("width", "56px")
                .set("height", "56px")
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("margin", "0 auto");

        H1 title = new H1("FleetPulse");
        title.getStyle().set("margin", "var(--lumo-space-m) 0 0 0").set("text-align", "center");

        Span subtitle = new Span("Fleet health monitoring");
        subtitle.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("margin-bottom", "var(--lumo-space-l)");
        Div subtitleWrap = new Div(subtitle);
        subtitleWrap.getStyle().set("text-align", "center").set("margin-bottom", "var(--lumo-space-l)");

        EmailField email = new EmailField("Email");
        email.setWidthFull();
        PasswordField password = new PasswordField("Password");
        password.setWidthFull();

        Button loginButton = new Button("Sign in");
        loginButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        loginButton.setWidthFull();
        loginButton.addClickListener(event -> attemptLogin(fleetServiceClient, email.getValue(), password.getValue()));
        loginButton.setDisableOnClick(true);
        email.addKeyPressListener(Key.ENTER,
                event -> attemptLogin(fleetServiceClient, email.getValue(), password.getValue()));
        password.addKeyPressListener(Key.ENTER,
                event -> attemptLogin(fleetServiceClient, email.getValue(), password.getValue()));

        FormLayout form = new FormLayout(email, password);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        VerticalLayout card = new VerticalLayout(badge, title, subtitleWrap, form, loginButton);
        card.setAlignItems(Alignment.STRETCH);
        card.setPadding(true);
        card.setSpacing(false);
        card.setWidth("360px");
        card.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("box-shadow", "var(--fp-card-shadow)")
                .set("padding", "var(--lumo-space-xl)");

        add(card);
    }

    private void attemptLogin(FleetServiceClient fleetServiceClient, String email, String password) {
        try {
            LoginResponse response = fleetServiceClient.login(email, password);
            VaadinSession.getCurrent().setAttribute("token", response.token());
            VaadinSession.getCurrent().setAttribute("role", response.role());
            UI.getCurrent().navigate(DashboardView.class);
        } catch (RestClientException ex) {
            Notification notification = Notification.show("Invalid email or password", 4000, Notification.Position.MIDDLE);
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
}
