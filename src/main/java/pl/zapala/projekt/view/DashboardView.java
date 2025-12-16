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
import pl.zapala.projekt.protocol.SatelliteProtocol.*;
import pl.zapala.projekt.service.VotingService;

import java.text.SimpleDateFormat;
import java.util.Date;

@Route("")
@PageTitle("System Głosowania Przybliżonego Czasu")
public class DashboardView extends VerticalLayout {

    private final VotingService votingService;
    private final Grid<SatelliteState> satelliteGrid;

    private final H2 systemTimeValue = new H2("Oczekiwanie...");
    private final H2 deviationValue = new H2("0 ms");
    private final H2 activeCountValue = new H2("0 / 8");

    public DashboardView(VotingService votingService) {
        this.votingService = votingService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);
        setAlignItems(Alignment.CENTER);

        H1 title = new H1("System Głosowania Przybliżonego Czasu");
        title.getStyle()
                .set("color", "var(--lumo-primary-text-color)")
                .set("margin", "1rem 0")
                .set("text-align", "center")
                .set("font-weight", "600");

        add(title);

        HorizontalLayout statsPanel = createStatisticsPanel();
        statsPanel.setMaxWidth("1200px");
        add(statsPanel);

        satelliteGrid = createSatelliteGrid();

        VerticalLayout gridLayout = new VerticalLayout();
        gridLayout.setWidthFull();
        gridLayout.setMaxWidth("1300px");
        gridLayout.setPadding(false);

        H3 gridTitle = new H3("Panel Sterowania");
        gridTitle.getStyle().set("margin-top", "2em");

        gridLayout.add(gridTitle, satelliteGrid);
        add(gridLayout);
    }

    private HorizontalLayout createStatisticsPanel() {
        HorizontalLayout layout = new HorizontalLayout();
        layout.setWidthFull();
        layout.setPadding(false);
        layout.setSpacing(true);

        layout.add(createStatCard("Czas Systemowy", systemTimeValue, VaadinIcon.CLOCK));
        layout.add(createStatCard("Odchylenie Czasu", deviationValue, VaadinIcon.CHART));
        layout.add(createStatCard("Aktywne Satelity", activeCountValue, VaadinIcon.CONNECT));
        return layout;
    }

    private Div createStatCard(String label, Component valueComponent, VaadinIcon icon) {
        Div card = new Div();
        card.getStyle()
                .set("padding", "1.5em")
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("box-shadow", "0 4px 12px rgba(0,0,0,0.05)")
                .set("flex", "1")
                .set("text-align", "center");

        Icon iconComponent = icon.create();
        iconComponent.setSize("2.5em");
        iconComponent.setColor("var(--lumo-primary-color)");
        iconComponent.getStyle().set("margin-bottom", "0.5em");

        H4 labelH4 = new H4(label);
        labelH4.getStyle()
                .set("margin", "0.5rem 0 0 0")
                .set("font-size", "0.9em")
                .set("text-transform", "uppercase")
                .set("letter-spacing", "0.05em")
                .set("color", "var(--lumo-secondary-text-color)");

        valueComponent.getStyle()
                .set("margin", "0.5em 0 0 0")
                .set("font-size", "1.5em")
                .set("font-weight", "bold")
                .set("color", "var(--lumo-header-text-color)");

        card.add(iconComponent, labelH4, valueComponent);
        return card;
    }

    private Grid<SatelliteState> createSatelliteGrid() {
        Grid<SatelliteState> grid = new Grid<>();
        grid.setHeight("500px");
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_NO_BORDER);

        grid.addColumn(SatelliteState::getId)
                .setHeader("ID Satelity")
                .setFlexGrow(1)
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.CENTER);

        grid.addComponentColumn(state -> {
                    Span badge = new Span(state.isConnected() ? "Połączony" : "Rozłączony");
                    badge.getElement().getThemeList().add(
                            state.isConnected() ? "badge success" : "badge error"
                    );
                    return badge;
                }).setHeader("Status")
                .setFlexGrow(1)
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.CENTER);

        grid.addColumn(state -> {
                    if (state.getReportedTime() == 0) return "-";
                    return new SimpleDateFormat("HH:mm:ss.SSS").format(new Date(state.getReportedTime()));
                }).setHeader("Zgłoszony Czas")
                .setFlexGrow(1)
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.CENTER);

        grid.addColumn(state -> String.format("%.2f", state.getWeight()))
                .setHeader("Waga")
                .setFlexGrow(1)
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.CENTER);

        grid.addComponentColumn(this::createActionButtons)
                .setHeader("Akcje")
                .setFlexGrow(1)
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.CENTER);

        return grid;
    }

    private HorizontalLayout createActionButtons(SatelliteState state) {
        HorizontalLayout buttons = new HorizontalLayout();
        buttons.setJustifyContentMode(JustifyContentMode.CENTER);
        buttons.setWidthFull();
        buttons.setSpacing(true);

        Button weightBtn = new Button(VaadinIcon.SCALE.create());
        weightBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);
        weightBtn.setTooltipText("Zmień wagę");
        weightBtn.addClickListener(e -> showWeightDialog(state.getId()));

        Button offsetBtn = new Button(VaadinIcon.TIME_BACKWARD.create());
        offsetBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
        offsetBtn.setTooltipText("Przesunięcie czasu");
        offsetBtn.addClickListener(e -> showErrorInjectionDialog(state.getId(), RequestType.INJECT_TIME_OFFSET));

        Button crashBtn = new Button(VaadinIcon.BAN.create());
        crashBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
        crashBtn.setTooltipText("Awaria");
        crashBtn.addClickListener(e -> injectCrash(state.getId()));

        Button resetBtn = new Button(VaadinIcon.REFRESH.create());
        resetBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_SUCCESS);
        resetBtn.setTooltipText("Resetuj");
        resetBtn.addClickListener(e -> resetSatellite(state.getId()));

        buttons.add(weightBtn, offsetBtn, crashBtn, resetBtn);
        return buttons;
    }

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

    private void showErrorInjectionDialog(int satelliteId, RequestType errorType) {
        Dialog dialog = new Dialog();
        String title = errorType == RequestType.INJECT_TIME_OFFSET ? "Przesunięcie czasu" : "Opóźnienie sieci";
        dialog.setHeaderTitle(title + " (ID: " + satelliteId + ")");

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

    private void injectCrash(int satelliteId) {
        votingService.injectError(satelliteId, RequestType.INJECT_CRASH, null)
                .thenAccept(response -> getUI().ifPresent(ui -> ui.access(() -> {
                    showNotification("Awaria satelity " + satelliteId + "!", NotificationVariant.LUMO_ERROR);
                })));
    }

    private void resetSatellite(int satelliteId) {
        votingService.resetSatelliteErrors(satelliteId)
                .thenAccept(response -> getUI().ifPresent(ui -> ui.access(() -> {
                    showNotification("Satelita " + satelliteId + " zresetowany", NotificationVariant.LUMO_SUCCESS);
                })));
    }

    private void showNotification(String message, NotificationVariant variant) {
        Notification notification = Notification.show(message, 3000, Notification.Position.TOP_CENTER);
        notification.addThemeVariants(variant);
    }

    private void updateUI() {
        long systemTime = votingService.getCalculatedSystemTime();
        long deviation = votingService.getDeviation();
        int activeCount = votingService.getActiveResponseCount();

        if (systemTime > 0) {
            systemTimeValue.setText(new SimpleDateFormat("HH:mm:ss.SSS").format(new Date(systemTime)));
        }
        deviationValue.setText(deviation + " ms");
        activeCountValue.setText(activeCount + " / 8");

        satelliteGrid.setItems(votingService.getAllSatelliteStates());
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        UI ui = attachEvent.getUI();

        ui.getPage().addStyleSheet("https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600&subset=latin,latin-ext&display=swap");
        getStyle().set("font-family", "'Inter', sans-serif");

        ui.setPollInterval(1000);
        ui.addPollListener(e -> updateUI());
    }
}