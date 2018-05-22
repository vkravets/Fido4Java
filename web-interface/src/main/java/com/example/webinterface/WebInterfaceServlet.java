package com.example.webinterface;

import com.vaadin.flow.server.VaadinServlet;
import com.vaadin.flow.server.VaadinServletConfiguration;

import javax.servlet.annotation.WebServlet;


@WebServlet(urlPatterns = "/*", name = "WebInterfaceServlet", asyncSupported = true)
@VaadinServletConfiguration(ui = WebInterfaceUI.class, productionMode = false)
public class WebInterfaceServlet extends VaadinServlet { }
