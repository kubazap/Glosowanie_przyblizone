package pl.zapala.projekt.view;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import pl.zapala.projekt.model.VotingHistoryEntry;
import pl.zapala.projekt.protocol.SatelliteProtocol.*;
import pl.zapala.projekt.service.VotingService;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Main dashboard view for the Distributed Time Voting System.
 * Displays real-time system metrics, satellite control panel, topology visualization, and voting history.
 */
@Route("")
@PageTitle("System Głosowania Przybliżonego Czasu")
public class DashboardView extends VerticalLayout {
    private final VotingService votingService;
    private final Grid<SatelliteState> satelliteGrid;
    private final Grid<VotingHistoryEntry> historyGrid;

    private final H2 systemTimeValue = new H2("Oczekiwanie...");
    private final H2 deviationValue = new H2("0 ms");
    private final H2 activeCountValue = new H2("0 / 8");

    private final Div topologyCanvas = new Div();

    public DashboardView(VotingService votingService) {
        this.votingService = votingService;

        setWidthFull();
        setHeight(null);
        setPadding(false);
        setSpacing(false);
        getStyle().set("background", "transparent");

        VerticalLayout wrapper = new VerticalLayout();
        wrapper.setWidthFull();
        wrapper.setPadding(true);
        wrapper.setSpacing(true);
        wrapper.getStyle()
                .set("background", "var(--lumo-contrast-5pct)")
                .set("min-height", "100vh");

        H1 title = new H1("System Głosowania Przybliżonego Czasu");
        title.getStyle()
                .set("color", "var(--lumo-primary-text-color)")
                .set("margin", "0.5rem 0")
                .set("text-align", "center")
                .set("font-size", "1.8rem")
                .set("font-weight", "600");
        wrapper.add(title);

        // TOP SECTION: Topology + Statistics
        HorizontalLayout topSection = new HorizontalLayout();
        topSection.setWidthFull();
        topSection.setHeight("400px");
        topSection.setSpacing(true);

        // Left side: Topology visualization
        VerticalLayout topologyLayout = createSectionContainer("Topologia");
        topologyLayout.setWidth("55%");
        topologyLayout.setHeightFull();

        topologyCanvas.setSizeFull();
        topologyCanvas.getStyle()
                .set("position", "relative")
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("border", "1px solid var(--lumo-contrast-10pct)");

        topologyLayout.add(topologyCanvas);
        topologyLayout.setFlexGrow(1, topologyCanvas);

        // Right side: Statistics in 2x1 layout
        VerticalLayout statsLayout = new VerticalLayout();
        statsLayout.setWidth("45%");
        statsLayout.setHeightFull();
        statsLayout.setSpacing(true);
        statsLayout.setPadding(false);

        // Top row: System Time + Active Satellites (50% height)
        HorizontalLayout statsRow1 = new HorizontalLayout();
        statsRow1.setWidthFull();
        statsRow1.setHeight("50%");
        statsRow1.setSpacing(true);

        Div timeCard = createStatCard("Czas Systemowy", systemTimeValue, VaadinIcon.CLOCK);
        Div activeCard = createStatCard("Aktywne Satelity", activeCountValue, VaadinIcon.CONNECT);

        timeCard.getStyle().set("width", "50%").set("height", "100%");
        activeCard.getStyle().set("width", "50%").set("height", "100%");

        statsRow1.add(timeCard, activeCard);

        // Bottom row: Deviation (50% height, full width)
        Div deviationCard = createStatCard("Odchylenie czasu", deviationValue, VaadinIcon.CHART);
        deviationCard.getStyle().set("width", "100%").set("height", "50%");

        statsLayout.add(statsRow1, deviationCard);

        topSection.add(topologyLayout, statsLayout);
        wrapper.add(topSection);

        // MIDDLE SECTION: Satellite Control Panel
        VerticalLayout satelliteSection = createSectionContainer("Panel Sterowania");
        satelliteGrid = createSatelliteGrid();
        satelliteSection.add(satelliteGrid);
        wrapper.add(satelliteSection);

        // BOTTOM SECTION: Voting History
        VerticalLayout historySection = createSectionContainer("Historia Głosowań");
        historyGrid = createHistoryGrid();
        historySection.add(historyGrid);
        wrapper.add(historySection);

        add(wrapper);
    }

    /**
     * Create section container with white background and shadow
     */
    private VerticalLayout createSectionContainer(String title) {
        VerticalLayout layout = new VerticalLayout();
        layout.setWidthFull();
        layout.setPadding(true);
        layout.setSpacing(true);
        layout.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("box-shadow", "0 2px 8px rgba(0,0,0,0.08)");

        H3 header = new H3(title);
        header.getStyle()
                .set("margin", "0 0 0.5em 0")
                .set("font-size", "1.1em")
                .set("color", "var(--lumo-secondary-text-color)");

        layout.add(header);
        return layout;
    }

    /**
     * Create statistics card with icon, label and value
     */
    private Div createStatCard(String label, Component valueComponent, VaadinIcon icon) {
        Div card = new Div();
        card.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("box-shadow", "0 2px 8px rgba(0,0,0,0.08)")
                .set("padding", "1.5em")
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("box-sizing", "border-box");

        Icon iconComponent = icon.create();
        iconComponent.setSize("2em");
        iconComponent.setColor("var(--lumo-primary-color)");
        iconComponent.getStyle().set("margin-bottom", "0.5em");

        H4 labelHeader = new H4(label);
        labelHeader.getStyle()
                .set("margin", "0")
                .set("font-size", "0.8em")
                .set("text-transform", "uppercase")
                .set("letter-spacing", "0.05em")
                .set("color", "var(--lumo-secondary-text-color)");

        valueComponent.getStyle()
                .set("margin", "0.3em 0 0 0")
                .set("font-size", "1.6em")
                .set("font-weight", "bold")
                .set("color", "var(--lumo-body-text-color)");

        card.add(iconComponent, labelHeader, valueComponent);
        return card;
    }

    /**
     * Update topology visualization with real-time satellite states.
     * Creates an SVG-based circular topology with server at center and satellites on the perimeter.
     */
    private void updateTopologyVisualization(List<SatelliteState> states) {
        topologyCanvas.removeAll();

        int n = states.size();

        double cx = 50.0;
        double cy = 50.0;
        double radius = 35.0;

        StringBuilder svgContent = new StringBuilder();

        svgContent.append("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 100 100' preserveAspectRatio='none' ")
                .append("style='width: 100%; height: 100%; display: block;'>");

        for (int i = 0; i < n; i++) {
            SatelliteState state = states.get(i);

            double angle = 2 * Math.PI * i / n - Math.PI / 2;
            double sx = cx + radius * Math.cos(angle);
            double sy = cy + radius * Math.sin(angle);

            String color = resolveSvgColor(state);

            svgContent.append(String.format(Locale.US,
                    "<line x1='%.2f' y1='%.2f' x2='%.2f' y2='%.2f' stroke='%s' stroke-width='2' vector-effect='non-scaling-stroke' opacity='0.6' />",
                    cx, cy, sx, sy, color
            ));
        }
        svgContent.append("</svg>");

        Div svgContainer = new Div();
        svgContainer.setSizeFull();
        svgContainer.getStyle()
                .set("position", "absolute")
                .set("top", "0")
                .set("left", "0")
                .set("z-index", "0")
                .set("pointer-events", "none");

        svgContainer.getElement().setProperty("innerHTML", svgContent.toString());
        topologyCanvas.add(svgContainer);

        Div server = new Div("SRV");
        server.getStyle()
                .set("position", "absolute")
                .set("left", "50%").set("top", "50%")
                .set("transform", "translate(-50%, -50%)")
                .set("width", "60px").set("height", "60px")
                .set("border-radius", "50%")
                .set("background", "var(--lumo-primary-color)")
                .set("color", "white")
                .set("display", "flex").set("align-items", "center").set("justify-content", "center")
                .set("font-weight", "bold")
                .set("box-shadow", "0 0 15px rgba(0,0,0,0.2)")
                .set("z-index", "2");

        topologyCanvas.add(server);

        for (int i = 0; i < n; i++) {
            SatelliteState state = states.get(i);

            double angle = 2 * Math.PI * i / n - Math.PI / 2;
            double sx = cx + radius * Math.cos(angle);
            double sy = cy + radius * Math.sin(angle);

            Div sat = new Div("S" + state.getId());
            sat.setTitle("Waga: " + state.getWeight());

            sat.getStyle()
                    .set("position", "absolute")
                    .set("left", String.format(Locale.US, "%.2f%%", sx))
                    .set("top", String.format(Locale.US, "%.2f%%", sy))
                    .set("transform", "translate(-50%, -50%)")
                    .set("width", "40px").set("height", "40px")
                    .set("border-radius", "50%")
                    .set("background", resolveSvgColor(state))
                    .set("color", "white")
                    .set("display", "flex").set("align-items", "center").set("justify-content", "center")
                    .set("font-weight", "bold")
                    .set("border", "2px solid #fff")
                    .set("box-shadow", "0 2px 5px rgba(0,0,0,0.2)")
                    .set("cursor", "pointer")
                    .set("z-index", "2");

            topologyCanvas.add(sat);
        }
    }

    /**
     * Resolve color for satellite based on state
     */
    private String resolveSvgColor(SatelliteState state) {
        if (!state.isConnected() || state.getStatus() == ResponseStatus.CRASHED) {
            return "#e03131";
        }
        if (state.getWeight() == 0.0) {
            return "#f59f00";
        }
        return "#2f9e44";
    }

    /**
     * Create satellite control grid
     */
    private Grid<SatelliteState> createSatelliteGrid() {
        Grid<SatelliteState> grid = new Grid<>();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_NO_BORDER);
        grid.setHeight("300px");

        grid.addColumn(SatelliteState::getId)
                .setHeader("ID Satelity")
                .setWidth("150px")
                .setFlexGrow(0)
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.CENTER);

        grid.addComponentColumn(state -> {
                    Span badge = new Span(state.isConnected() ? "Połączony" : "Rozłączony");
                    badge.getElement().getThemeList().add(
                            state.isConnected() ? "badge success" : "badge error"
                    );
                    return badge;
                }).setHeader("Status")
                .setWidth("150px")
                .setFlexGrow(0)
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.CENTER);

        grid.addColumn(state -> {
                    if (state.getReportedTime() == 0) return "-";
                    return new SimpleDateFormat("HH:mm:ss.SSS").format(new Date(state.getReportedTime()));
                }).setHeader("Zgłoszony Czas")
                .setAutoWidth(true)
                .setFlexGrow(1)
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.CENTER);

        grid.addColumn(state -> String.format("%.2f", state.getWeight()))
                .setHeader("Waga")
                .setWidth("150px")
                .setFlexGrow(0)
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.CENTER);

        grid.addComponentColumn(this::createActionButtons)
                .setHeader("Akcje")
                .setAutoWidth(true)
                .setFlexGrow(0)
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.CENTER);

        return grid;
    }

    /**
     * Create action buttons for satellite control
     */
    private HorizontalLayout createActionButtons(SatelliteState state) {
        HorizontalLayout buttons = new HorizontalLayout();
        buttons.setSpacing(true);

        Button weightBtn = new Button(VaadinIcon.SCALE.create());
        weightBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);
        weightBtn.setTooltipText("Zmień wagę");
        weightBtn.addClickListener(e -> showWeightDialog(state.getId()));

        Button offsetBtn = new Button(VaadinIcon.TIME_BACKWARD.create());
        offsetBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
        offsetBtn.setTooltipText("Przesunięcie czasu");
        offsetBtn.addClickListener(e -> showErrorInjectionDialog(state.getId(), RequestType.INJECT_TIME_OFFSET));

        Button delayBtn = new Button(VaadinIcon.TIMER.create());
        delayBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_CONTRAST);
        delayBtn.setTooltipText("Opóźnienie sieci");
        delayBtn.addClickListener(e -> showErrorInjectionDialog(state.getId(), RequestType.INJECT_NETWORK_DELAY));

        Button crashBtn = new Button(VaadinIcon.BAN.create());
        crashBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
        crashBtn.setTooltipText("Awaria");
        crashBtn.addClickListener(e -> injectCrash(state.getId()));

        Button resetBtn = new Button(VaadinIcon.REFRESH.create());
        resetBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_SUCCESS);
        resetBtn.setTooltipText("Resetuj");
        resetBtn.addClickListener(e -> resetSatellite(state.getId()));

        buttons.add(weightBtn, offsetBtn, delayBtn, crashBtn, resetBtn);
        return buttons;
    }

    /**
     * Create voting history grid
     */
    private Grid<VotingHistoryEntry> createHistoryGrid() {
        Grid<VotingHistoryEntry> grid = new Grid<>();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_NO_BORDER);
        grid.setHeight("300px");

        grid.getElement().executeJs(
                "this.style.setProperty('--_lumo-grid-border-width', '0px');" +
                        "const cells = this.shadowRoot.querySelectorAll('td');" +
                        "cells.forEach(cell => { cell.style.padding = '1rem 0.5rem'; });"
        );

        grid.addColumn(VotingHistoryEntry::timeString)
                .setHeader("Czas Systemowy")
                .setAutoWidth(true)
                .setFlexGrow(2)
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.CENTER);

        grid.addColumn(VotingHistoryEntry::activeSatellites)
                .setHeader("Aktywne Satelity")
                .setWidth("250px")
                .setFlexGrow(0)
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.CENTER);

        grid.addColumn(entry -> String.format("%+d ms", entry.deviation()))
                .setHeader("Odchylenie")
                .setWidth("250px")
                .setFlexGrow(0)
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.CENTER);

        return grid;
    }

    /**
     * Show dialog to update satellite weight
     */
    private void showWeightDialog(int satelliteId) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Waga satelity " + satelliteId);

        NumberField weightField = new NumberField("Waga (0-10)");
        weightField.setValue(votingService.getSatelliteWeight(satelliteId));
        weightField.setMin(0);
        weightField.setMax(10);
        weightField.setStep(0.1);
        weightField.setWidthFull();

        Button saveBtn = new Button("Zapisz", e -> {
            votingService.updateSatelliteWeight(satelliteId, weightField.getValue());
            showNotification("Zaktualizowano wagę", NotificationVariant.LUMO_SUCCESS);
            dialog.close();
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveBtn.setWidthFull();

        Button cancelBtn = new Button("Anuluj", e -> dialog.close());
        cancelBtn.setWidthFull();

        VerticalLayout layout = new VerticalLayout(weightField, saveBtn, cancelBtn);
        layout.setPadding(false);
        dialog.add(layout);
        dialog.open();
    }

    /**
     * Show dialog for error injection (time offset or network delay)
     */
    private void showErrorInjectionDialog(int satelliteId, RequestType errorType) {
        Dialog dialog = new Dialog();
        String title = errorType == RequestType.INJECT_TIME_OFFSET ?
                "Przesunięcie czasu" : "Opóźnienie sieci";
        dialog.setHeaderTitle(title + " - Satelita " + satelliteId);

        NumberField valueField = new NumberField("Wartość (ms)");
        valueField.setValue(1000.0);
        valueField.setMin(0);
        valueField.setMax(60000);
        valueField.setStep(100);
        valueField.setWidthFull();

        Button injectBtn = new Button("Wstrzyknij", e -> {
            votingService.injectError(satelliteId, errorType, valueField.getValue().longValue())
                    .thenAccept(response -> getUI().ifPresent(ui -> ui.access(() -> {
                        showNotification(title + " aktywne", NotificationVariant.LUMO_PRIMARY);
                    })));
            dialog.close();
        });
        injectBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        injectBtn.setWidthFull();

        Button cancelBtn = new Button("Anuluj", e -> dialog.close());
        cancelBtn.setWidthFull();

        VerticalLayout layout = new VerticalLayout(valueField, injectBtn, cancelBtn);
        layout.setPadding(false);
        dialog.add(layout);
        dialog.open();
    }

    /**
     * Inject crash error into satellite
     */
    private void injectCrash(int satelliteId) {
        votingService.injectError(satelliteId, RequestType.INJECT_CRASH, null)
                .thenAccept(response -> getUI().ifPresent(ui -> ui.access(() -> {
                    showNotification("Awaria satelity " + satelliteId, NotificationVariant.LUMO_ERROR);
                })));
    }

    /**
     * Reset all errors for a satellite
     */
    private void resetSatellite(int satelliteId) {
        votingService.resetSatelliteErrors(satelliteId)
                .thenAccept(response -> getUI().ifPresent(ui -> ui.access(() -> {
                    showNotification("Satelita " + satelliteId + " zresetowany", NotificationVariant.LUMO_SUCCESS);
                })));
    }

    /**
     * Show notification with variant styling
     */
    private void showNotification(String message, NotificationVariant variant) {
        Notification notification = Notification.show(message, 3000, Notification.Position.TOP_CENTER);
        notification.addThemeVariants(variant);
    }

    /**
     * Update UI with latest data from voting service
     */
    private void updateUI() {
        long systemTime = votingService.getCalculatedSystemTime();
        long deviation = votingService.getDeviation();
        int activeCount = votingService.getActiveResponseCount();

        if (systemTime > 0) {
            systemTimeValue.setText(new SimpleDateFormat("HH:mm:ss.SSS").format(new Date(systemTime)));
        }
        deviationValue.setText(deviation + " ms");
        activeCountValue.setText(activeCount + " / 8");

        List<SatelliteState> states = votingService.getAllSatelliteStates();
        satelliteGrid.setItems(states);

        updateTopologyVisualization(states);

        List<VotingHistoryEntry> history = votingService.getVotingHistory();
        historyGrid.setItems(history);
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        UI ui = attachEvent.getUI();

        ui.getPage().addStyleSheet(
                "https://fonts.googleapis.com/css2?family=Poppins:wght@300;400;500;600&display=swap"
        );

        ui.getPage().executeJs(
                "document.documentElement.style.setProperty('--lumo-font-family', 'Poppins, sans-serif');"
        );

        ui.setPollInterval(1000);
        ui.addPollListener(e -> updateUI());
    }
}