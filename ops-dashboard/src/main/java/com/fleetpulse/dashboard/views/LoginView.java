package com.fleetpulse.dashboard.views;

import com.fleetpulse.dashboard.client.FleetServiceClient;
import com.fleetpulse.dashboard.client.LoginResponse;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H1;
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

        EmailField email = new EmailField("Email");
        email.setWidthFull();
        PasswordField password = new PasswordField("Password");
        password.setWidthFull();

        Button loginButton = new Button("Sign in");
        loginButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        loginButton.addClickListener(event -> attemptLogin(fleetServiceClient, email.getValue(), password.getValue()));
        loginButton.setDisableOnClick(true);

        FormLayout form = new FormLayout(email, password, loginButton);
        form.setMaxWidth("360px");
        form.setColspan(loginButton, 2);

        add(new H1("FleetPulse"), form);
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
