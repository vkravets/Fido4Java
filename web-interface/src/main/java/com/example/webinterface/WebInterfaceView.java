package com.example.webinterface;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.page.BodySize;
import com.vaadin.flow.component.page.Viewport;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.theme.lumo.Lumo;

/**
 * The main view of the application
 */
@Route("")
@Theme(Lumo.class)
@BodySize(height = "100vh", width = "100vw")
@Viewport("width=device-width, minimum-scale=1.0, initial-scale=1.0, user-scalable=yes")
@StyleSheet("frontend://web-interface-theme.css")
public class WebInterfaceView extends VerticalLayout {

    public WebInterfaceView() {
        setClassName("app-view");

        Label hello = new Label("Hello Gradle app!");
        add(hello);

        Button button = new Button("Click me", event -> {
            hello.setText("Clicked!");
            hello.setClassName("clicked");
        });
        add(button);
    }
}
