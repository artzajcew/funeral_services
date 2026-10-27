package ru.memory.fx;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignD;
import ru.memory.fx.data.DatabaseConfig;
import ru.memory.fx.data.JdbcOrderGateway;
import ru.memory.fx.data.JdbcOrderGateway.ClientOption;
import ru.memory.fx.data.JdbcOrderGateway.OrderRow;
import ru.memory.fx.data.JdbcOrderGateway.OrderItemInput;
import ru.memory.fx.data.JdbcOrderGateway.OrderItemRow;
import ru.memory.fx.data.JdbcOrderGateway.PeriodStatistics;
import ru.memory.fx.data.JdbcOrderGateway.NewClientInput;
import ru.memory.fx.data.JdbcOrderGateway.ServiceOption;
import ru.memory.fx.service.AnalyticsService;
import ru.memory.fx.service.AnalyticsService.DashboardStatistics;
import ru.memory.fx.service.CsvExportService;
import ru.memory.fx.service.DirectoryService;
import ru.memory.fx.service.OrderFilterService;
import ru.memory.fx.service.OrderService;
import ru.memory.fx.service.OrderService.DataSnapshot;
import ru.memory.model.Models.OrderStatus;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class MainController {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final java.text.NumberFormat MONEY = java.text.NumberFormat.getIntegerInstance(Locale.forLanguageTag("ru-RU"));
    private static final java.text.NumberFormat PRECISE_MONEY = preciseMoneyFormat();
    private static final javafx.animation.Interpolator EASE_OUT = javafx.animation.Interpolator.SPLINE(0.23, 1, 0.32, 1);
    private static final boolean REDUCE_MOTION = Boolean.getBoolean("memory.reduceMotion");

    @FXML private StackPane sceneRoot;
    @FXML private ScrollPane contentScroll;
    @FXML private VBox overviewRoot;
    @FXML private VBox contentRoot;
    @FXML private VBox clientsRoot;
    @FXML private VBox servicesRoot;
    @FXML private VBox statisticsRoot;
    @FXML private VBox ordersWorkspace;
    @FXML private SplitPane ordersSplit;
    @FXML private Button overviewNavButton;
    @FXML private Button ordersNavButton;
    @FXML private Button clientsNavButton;
    @FXML private Button servicesNavButton;
    @FXML private Button analyticsNavButton;
    @FXML private Button refreshButton;
    @FXML private Button statsRefreshButton;
    @FXML private Button exportButton;
    @FXML private Button createButton;
    @FXML private Button editButton;
    @FXML private Button deleteButton;
    @FXML private Label connectionStatusLabel;

    @FXML private Label overviewOrdersLabel;
    @FXML private Label overviewClientsLabel;
    @FXML private Label overviewServicesLabel;
    @FXML private Label overviewActiveLabel;
    @FXML private Label overviewNewLabel;
    @FXML private Label overviewProgressLabel;
    @FXML private Label overviewCompletedLabel;
    @FXML private TableView<OrderRow> overviewOrdersTable;
    @FXML private TableColumn<OrderRow, Number> overviewOrderIdColumn;
    @FXML private TableColumn<OrderRow, String> overviewOrderClientColumn;
    @FXML private TableColumn<OrderRow, OrderStatus> overviewOrderStatusColumn;
    @FXML private TableColumn<OrderRow, Number> overviewOrderAmountColumn;

    @FXML private TextField clientSearchField;
    @FXML private Label clientCountLabel;
    @FXML private TableView<ClientOption> clientsTable;
    @FXML private TableColumn<ClientOption, String> clientNameColumn;
    @FXML private TableColumn<ClientOption, String> clientPhoneColumn;
    @FXML private TableColumn<ClientOption, String> clientEmailColumn;
    @FXML private TableColumn<ClientOption, Number> clientOrdersColumn;
    @FXML private Button clientCreateButton;
    @FXML private Button clientEditButton;
    @FXML private Button clientDeleteButton;

    @FXML private TextField serviceSearchField;
    @FXML private Label serviceCountLabel;
    @FXML private TableView<ServiceOption> servicesTable;
    @FXML private TableColumn<ServiceOption, String> serviceNameColumn;
    @FXML private TableColumn<ServiceOption, Number> servicePriceColumn;
    @FXML private TableColumn<ServiceOption, Number> serviceUsageColumn;
    @FXML private Button serviceCreateButton;
    @FXML private Button serviceEditButton;
    @FXML private Button serviceDeleteButton;

    @FXML private Label resultCountLabel;
    @FXML private Label totalOrdersLabel;
    @FXML private Label activeOrdersLabel;
    @FXML private Label completedOrdersLabel;
    @FXML private Label activeSumLabel;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusFilter;
    @FXML private DatePicker orderFromDate;
    @FXML private DatePicker orderToDate;
    @FXML private Label orderFilterError;
    @FXML private TableView<OrderRow> ordersTable;
    @FXML private TableColumn<OrderRow, Number> idColumn;
    @FXML private TableColumn<OrderRow, String> clientColumn;
    @FXML private TableColumn<OrderRow, LocalDateTime> dateColumn;
    @FXML private TableColumn<OrderRow, OrderStatus> statusColumn;
    @FXML private TableColumn<OrderRow, String> serviceColumn;
    @FXML private TableColumn<OrderRow, Number> amountColumn;
    @FXML private VBox tableSkeleton;
    @FXML private VBox emptyTableState;
    @FXML private Label emptyTableTitle;
    @FXML private Label emptyTableCopy;
    @FXML private Button emptyTableActionButton;

    @FXML private VBox emptyDetail;
    @FXML private VBox orderDetail;
    @FXML private Label detailNumber;
    @FXML private Label detailStatus;
    @FXML private Label detailClient;
    @FXML private Label detailPhone;
    @FXML private Label detailEmail;
    @FXML private Label detailServices;
    @FXML private Label detailDate;
    @FXML private Label detailTotal;
    @FXML private Button advanceStatusButton;

    @FXML private Label statsTotalOrdersLabel;
    @FXML private Label statsActiveSumLabel;
    @FXML private Label statsNewCountLabel;
    @FXML private Label statsProgressCountLabel;
    @FXML private Label statsCompletedCountLabel;
    @FXML private Label statsCancelledCountLabel;
    @FXML private ProgressBar statsNewProgress;
    @FXML private ProgressBar statsProgressProgress;
    @FXML private ProgressBar statsCompletedProgress;
    @FXML private ProgressBar statsCancelledProgress;
    @FXML private Label statsPortfolioSumLabel;
    @FXML private Label statsAverageOrderLabel;
    @FXML private DatePicker statsFromDate;
    @FXML private DatePicker statsToDate;
    @FXML private Button statsApplyPeriodButton;
    @FXML private Label statsPeriodCaption;
    @FXML private Label statsPeriodError;
    @FXML private Label statsPeriodOrderCountLabel;
    @FXML private Label statsPeriodMinLabel;
    @FXML private Label statsPeriodMaxLabel;
    @FXML private Label statsPeriodRevenueLabel;

    @FXML private Label toastLabel;

    private final ObservableList<OrderRow> orders = FXCollections.observableArrayList();
    private final ObservableList<ClientOption> clients = FXCollections.observableArrayList();
    private final ObservableList<ServiceOption> services = FXCollections.observableArrayList();
    private final JdbcOrderGateway gateway = new JdbcOrderGateway(DatabaseConfig.fromEnvironment());
    private final OrderService orderService = new OrderService(gateway);
    private final DirectoryService directoryService = new DirectoryService(gateway);
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "memory-database");
        thread.setDaemon(true);
        return thread;
    });
    private FilteredList<OrderRow> filteredOrders;
    private FilteredList<ClientOption> filteredClients;
    private FilteredList<ServiceOption> filteredServices;
    private PauseTransition toastTimer;
    private DataState dataState = DataState.LOADING;
    private EmptyAction emptyAction = EmptyAction.NONE;
    private boolean busy;
    private boolean periodDatesInitialized;
    private Boolean compactLayout;
    private VBox activePage;

    @FXML
    private void initialize() {
        activePage = overviewRoot;
        configureTable();
        configureOverview();
        configureDirectories();
        configureFilters();
        configurePeriodStatistics();
        configureShortcuts();
        configureResponsiveLayout();
        updateStatistics();
        connectionStatusLabel.setText("Подключение к базе данных");
        connectionStatusLabel.setTooltip(new Tooltip(gateway.config().endpoint()));
        connectionStatusLabel.getStyleClass().setAll("connection-status");
        updateEmptyState();
        Platform.runLater(() -> refreshOrders(-1, null));
    }

    private void configureResponsiveLayout() {
        for (VBox page : List.of(overviewRoot, contentRoot, clientsRoot, servicesRoot, statisticsRoot)) {
            page.minHeightProperty().bind(Bindings.createDoubleBinding(
                    () -> contentScroll.getViewportBounds().getHeight(), contentScroll.viewportBoundsProperty()));
        }
        sceneRoot.widthProperty().addListener((observable, oldValue, value) -> updateResponsiveLayout(value.doubleValue()));
        Platform.runLater(() -> updateResponsiveLayout(sceneRoot.getWidth()));
    }

    private void updateResponsiveLayout(double width) {
        boolean compact = width < 1280;
        if (compactLayout != null && compactLayout == compact) return;
        compactLayout = compact;
        ordersSplit.setOrientation(compact ? Orientation.VERTICAL : Orientation.HORIZONTAL);
        ordersWorkspace.setMinHeight(compact ? 920 : 500);
        ordersSplit.setDividerPositions(compact ? 0.56 : 0.69);
    }

    private void configureTable() {
        ordersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        idColumn.setCellValueFactory(row -> new SimpleIntegerProperty(row.getValue().id()));
        clientColumn.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().clientName()));
        dateColumn.setCellValueFactory(row -> new SimpleObjectProperty<>(row.getValue().createdAt()));
        statusColumn.setCellValueFactory(row -> new SimpleObjectProperty<>(row.getValue().status()));
        serviceColumn.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().services()));
        amountColumn.setCellValueFactory(row -> new SimpleLongProperty(row.getValue().total()));
        amountColumn.getStyleClass().add("amount-column");

        dateColumn.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(LocalDateTime value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : value.format(DATE_TIME));
            }
        });
        amountColumn.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(Number value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : money(value.longValue()));
            }
        });
        statusColumn.setCellFactory(column -> new TableCell<>() {
            private final Label badge = new Label();
            @Override protected void updateItem(OrderStatus value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setGraphic(null);
                    return;
                }
                badge.setText(value.label());
                badge.getStyleClass().setAll("status-badge", statusClass(value));
                setGraphic(badge);
            }
        });

        ordersTable.setRowFactory(table -> {
            TableRow<OrderRow> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty()) showOrder(row.getItem());
            });
            return row;
        });
        ordersTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, value) -> {
            if (value == null) showOnly(emptyDetail); else showOrder(value);
            updateActionAvailability();
        });
    }

    private void configureOverview() {
        overviewOrdersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        overviewOrderIdColumn.setCellValueFactory(row -> new SimpleIntegerProperty(row.getValue().id()));
        overviewOrderClientColumn.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().clientName()));
        overviewOrderStatusColumn.setCellValueFactory(row -> new SimpleObjectProperty<>(row.getValue().status()));
        overviewOrderAmountColumn.setCellValueFactory(row -> new SimpleLongProperty(row.getValue().total()));
        overviewOrderAmountColumn.getStyleClass().add("amount-column");
        overviewOrderAmountColumn.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(Number value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : money(value.longValue()));
            }
        });
        overviewOrderStatusColumn.setCellFactory(column -> statusCell());
        overviewOrdersTable.setOnMouseClicked(event -> {
            OrderRow selected = overviewOrdersTable.getSelectionModel().getSelectedItem();
            if (selected != null && event.getClickCount() == 2) {
                showOrdersPage();
                selectOrder(selected.id());
            }
        });
    }

    private TableCell<OrderRow, OrderStatus> statusCell() {
        return new TableCell<>() {
            private final Label badge = new Label();
            @Override protected void updateItem(OrderStatus value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setGraphic(null);
                    return;
                }
                badge.setText(value.label());
                badge.getStyleClass().setAll("status-badge", statusClass(value));
                setGraphic(badge);
            }
        };
    }

    private void configureDirectories() {
        clientsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        clientNameColumn.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().fullName()));
        clientPhoneColumn.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().phone()));
        clientEmailColumn.setCellValueFactory(row -> new SimpleStringProperty(
                row.getValue().email() == null || row.getValue().email().isBlank() ? "Не указан" : row.getValue().email()));
        clientOrdersColumn.setCellValueFactory(row -> new SimpleIntegerProperty(row.getValue().orderCount()));
        clientOrdersColumn.getStyleClass().add("amount-column");

        filteredClients = new FilteredList<>(clients, value -> true);
        SortedList<ClientOption> sortedClients = new SortedList<>(filteredClients);
        sortedClients.comparatorProperty().bind(clientsTable.comparatorProperty());
        clientsTable.setItems(sortedClients);
        clientNameColumn.setSortType(TableColumn.SortType.ASCENDING);
        clientsTable.getSortOrder().add(clientNameColumn);
        clientSearchField.textProperty().addListener((observable, oldValue, value) -> applyClientFilter());
        clientsTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, value) -> updateActionAvailability());
        clientsTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && clientsTable.getSelectionModel().getSelectedItem() != null) editSelectedClient();
        });

        servicesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        serviceNameColumn.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().name()));
        servicePriceColumn.setCellValueFactory(row -> new SimpleLongProperty(row.getValue().price()));
        serviceUsageColumn.setCellValueFactory(row -> new SimpleIntegerProperty(row.getValue().usageCount()));
        servicePriceColumn.getStyleClass().add("amount-column");
        serviceUsageColumn.getStyleClass().add("amount-column");
        servicePriceColumn.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(Number value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : money(value.longValue()));
            }
        });
        filteredServices = new FilteredList<>(services, value -> true);
        SortedList<ServiceOption> sortedServices = new SortedList<>(filteredServices);
        sortedServices.comparatorProperty().bind(servicesTable.comparatorProperty());
        servicesTable.setItems(sortedServices);
        serviceNameColumn.setSortType(TableColumn.SortType.ASCENDING);
        servicesTable.getSortOrder().add(serviceNameColumn);
        serviceSearchField.textProperty().addListener((observable, oldValue, value) -> applyServiceFilter());
        servicesTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, value) -> updateActionAvailability());
        servicesTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && servicesTable.getSelectionModel().getSelectedItem() != null) editSelectedService();
        });
    }

    private void configureFilters() {
        statusFilter.setItems(FXCollections.observableArrayList(
                "Все статусы", "Новый", "В обработке", "Выполнен", "Отменён"));
        statusFilter.getSelectionModel().selectFirst();
        filteredOrders = new FilteredList<>(orders, value -> true);
        SortedList<OrderRow> sorted = new SortedList<>(filteredOrders);
        sorted.comparatorProperty().bind(ordersTable.comparatorProperty());
        ordersTable.setItems(sorted);
        dateColumn.setSortType(TableColumn.SortType.DESCENDING);
        idColumn.setSortType(TableColumn.SortType.DESCENDING);
        ordersTable.getSortOrder().addAll(dateColumn, idColumn);
        searchField.textProperty().addListener((observable, oldValue, value) -> applyFilters());
        statusFilter.valueProperty().addListener((observable, oldValue, value) -> applyFilters());
        orderFromDate.setConverter(dateConverter());
        orderToDate.setConverter(dateConverter());
        orderFromDate.valueProperty().addListener((observable, oldValue, value) -> applyFilters());
        orderToDate.valueProperty().addListener((observable, oldValue, value) -> applyFilters());
    }

    private void configurePeriodStatistics() {
        statsFromDate.setConverter(dateConverter());
        statsToDate.setConverter(dateConverter());
        LocalDate today = LocalDate.now();
        statsFromDate.setValue(today.withDayOfMonth(1));
        statsToDate.setValue(today);
        statsFromDate.valueProperty().addListener((observable, oldValue, value) -> clearPeriodValidation());
        statsToDate.valueProperty().addListener((observable, oldValue, value) -> clearPeriodValidation());
    }

    private StringConverter<LocalDate> dateConverter() {
        return new StringConverter<>() {
            @Override public String toString(LocalDate value) {
                return value == null ? "" : value.format(DATE);
            }

            @Override public LocalDate fromString(String value) {
                if (value == null || value.isBlank()) return null;
                try {
                    return LocalDate.parse(value.strip(), DATE);
                } catch (DateTimeParseException error) {
                    return null;
                }
            }
        };
    }

    private void initializePeriodDatesFromOrders() {
        if (periodDatesInitialized) return;
        if (orders.isEmpty()) return;
        periodDatesInitialized = true;
        LocalDate firstDate = orders.stream()
                .map(order -> order.createdAt().toLocalDate())
                .min(LocalDate::compareTo)
                .orElse(statsFromDate.getValue());
        LocalDate lastDate = orders.stream()
                .map(order -> order.createdAt().toLocalDate())
                .max(LocalDate::compareTo)
                .orElse(statsToDate.getValue());
        statsFromDate.setValue(firstDate);
        statsToDate.setValue(lastDate);
    }

    @FXML
    private void applyPeriodStatistics() {
        refreshPeriodStatistics(true);
    }

    private void refreshPeriodStatistics(boolean showSuccess) {
        LocalDate from = statsFromDate.getValue();
        LocalDate to = statsToDate.getValue();
        String validation = validatePeriod(from, to);
        if (validation != null) {
            showPeriodValidation(validation);
            return;
        }
        clearPeriodValidation();
        runDatabaseOperation("Расчёт статистики", () -> orderService.loadPeriodStatistics(from, to), statistics -> {
            showPeriodStatistics(from, to, statistics);
            setConnectionReady();
            if (showSuccess) showToast("Статистика за период обновлена", false);
        }, error -> {
            showPeriodValidation("Не удалось рассчитать статистику. Проверьте подключение и повторите попытку.");
            connectionStatusLabel.setText("Не удалось рассчитать статистику");
            connectionStatusLabel.getStyleClass().setAll("connection-status", "connection-error");
        });
    }

    private String validatePeriod(LocalDate from, LocalDate to) {
        if (from == null || to == null) return "Укажите дату начала и дату окончания периода.";
        if (from.isAfter(to)) return "Дата начала должна быть не позже даты окончания.";
        return null;
    }

    private void showPeriodStatistics(LocalDate from, LocalDate to, PeriodStatistics statistics) {
        statsPeriodCaption.setText("С " + from.format(DATE) + " по " + to.format(DATE));
        statsPeriodOrderCountLabel.setText(statistics.orderCount() + " " + ordersWord(statistics.orderCount()));
        statsPeriodMinLabel.setText(money(statistics.minCost()));
        statsPeriodMaxLabel.setText(money(statistics.maxCost()));
        statsPeriodRevenueLabel.setText(money(statistics.revenue()));
    }

    private void showPeriodValidation(String message) {
        statsPeriodError.setText(message);
        statsPeriodError.setVisible(true);
        statsPeriodError.setManaged(true);
        LocalDate from = statsFromDate.getValue();
        LocalDate to = statsToDate.getValue();
        setFieldInvalid(statsFromDate, from == null || to != null && from.isAfter(to));
        setFieldInvalid(statsToDate, to == null || from != null && from.isAfter(to));
    }

    private void clearPeriodValidation() {
        statsPeriodError.setVisible(false);
        statsPeriodError.setManaged(false);
        setFieldInvalid(statsFromDate, false);
        setFieldInvalid(statsToDate, false);
    }

    private void configureShortcuts() {
        sceneRoot.sceneProperty().addListener((observable, oldScene, scene) -> {
            if (scene == null) return;
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN), this::createForActivePage);
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.E, KeyCombination.SHORTCUT_DOWN), this::editForActivePage);
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN), () -> {
                if (activePage == clientsRoot) clientSearchField.requestFocus();
                else if (activePage == servicesRoot) serviceSearchField.requestFocus();
                else {
                    showOrdersPage();
                    searchField.requestFocus();
                }
            });
        });
    }

    private void createForActivePage() {
        if (activePage == clientsRoot) openNewClient();
        else if (activePage == servicesRoot) openNewService();
        else openNewOrder();
    }

    private void editForActivePage() {
        if (activePage == clientsRoot) editSelectedClient();
        else if (activePage == servicesRoot) editSelectedService();
        else if (activePage == contentRoot) editSelectedOrder();
    }

    @FXML
    private void applyFilters() {
        LocalDate from = orderFromDate.getValue();
        LocalDate to = orderToDate.getValue();
        String dateError = OrderFilterService.validatePeriod(from, to);
        orderFilterError.setText(dateError == null ? "" : dateError);
        orderFilterError.setVisible(dateError != null);
        orderFilterError.setManaged(dateError != null);
        setFieldInvalid(orderFromDate, dateError != null);
        setFieldInvalid(orderToDate, dateError != null);
        if (dateError == null) {
            filteredOrders.setPredicate(order -> OrderFilterService.matches(
                    order, searchField.getText(), statusFilter.getValue(), from, to));
        } else {
            filteredOrders.setPredicate(order -> false);
        }
        resultCountLabel.setText("Показано: " + filteredOrders.size());
        if (filteredOrders.isEmpty()) showOnly(emptyDetail);
        if (ordersTable.getSelectionModel().getSelectedItem() == null && !filteredOrders.isEmpty()) {
            ordersTable.getSelectionModel().selectFirst();
        }
        updateEmptyState();
        updateActionAvailability();
    }

    @FXML
    private void clearFilters() {
        searchField.clear();
        statusFilter.getSelectionModel().selectFirst();
        orderFromDate.setValue(null);
        orderToDate.setValue(null);
        searchField.requestFocus();
    }

    private void applyClientFilter() {
        if (filteredClients == null) return;
        String query = normalized(clientSearchField.getText());
        filteredClients.setPredicate(client -> query.isBlank()
                || normalized(client.fullName()).contains(query)
                || normalized(client.phone()).contains(query)
                || normalized(client.email()).contains(query));
        clientCountLabel.setText("Показано: " + filteredClients.size());
        if (clientsTable.getSelectionModel().getSelectedItem() == null && !filteredClients.isEmpty()) {
            clientsTable.getSelectionModel().selectFirst();
        }
        updateActionAvailability();
    }

    @FXML
    private void clearClientSearch() {
        clientSearchField.clear();
        clientSearchField.requestFocus();
    }

    private void applyServiceFilter() {
        if (filteredServices == null) return;
        String query = normalized(serviceSearchField.getText());
        filteredServices.setPredicate(service -> query.isBlank() || normalized(service.name()).contains(query));
        serviceCountLabel.setText("Показано: " + filteredServices.size());
        if (servicesTable.getSelectionModel().getSelectedItem() == null && !filteredServices.isEmpty()) {
            servicesTable.getSelectionModel().selectFirst();
        }
        updateActionAvailability();
    }

    @FXML
    private void clearServiceSearch() {
        serviceSearchField.clear();
        serviceSearchField.requestFocus();
    }

    private static String normalized(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.forLanguageTag("ru-RU"));
    }

    @FXML
    private void refreshOrders() {
        refreshOrders(selectedOrderId(), "Данные обновлены");
    }

    private void refreshOrders(int selectedId, String successMessage) {
        dataState = DataState.LOADING;
        updateEmptyState();
        runDatabaseOperation("Загрузка данных", orderService::loadSnapshot, snapshot -> {
            orders.setAll(snapshot.orders());
            clients.setAll(snapshot.clients());
            services.setAll(snapshot.services());
            dataState = DataState.READY;
            applyFilters();
            applyClientFilter();
            applyServiceFilter();
            updateStatistics();
            selectOrder(selectedId);
            initializePeriodDatesFromOrders();
            setConnectionReady();
            if (successMessage != null) showToast(successMessage, false);
            refreshPeriodStatistics(false);
        }, error -> {
            orders.clear();
            clients.clear();
            services.clear();
            dataState = DataState.ERROR;
            applyFilters();
            applyClientFilter();
            applyServiceFilter();
            updateStatistics();
            connectionStatusLabel.setText("База данных недоступна");
            connectionStatusLabel.getStyleClass().setAll("connection-status", "connection-error");
            connectionStatusLabel.setTooltip(new Tooltip(error instanceof SQLException sql
                    ? databaseErrorMessage(sql)
                    : operationErrorMessage(error)));
            statsPeriodCaption.setText("Период недоступен");
            statsPeriodOrderCountLabel.setText("0 заказов");
            statsPeriodMinLabel.setText("0 ₽");
            statsPeriodMaxLabel.setText("0 ₽");
            statsPeriodRevenueLabel.setText("0 ₽");
        });
    }

    @FXML
    private void openNewOrder() {
        if (busy) return;
        if (!ensureServices()) return;
        showCreateDialog().ifPresent(draft -> runDatabaseOperation("Создание заказа", () -> {
                    List<OrderItemInput> items = draft.items().stream()
                            .map(item -> new OrderItemInput(item.service().id(), item.quantity(), item.service().price()))
                            .toList();
                    if (draft.newClient() != null) {
                        ClientDraft client = draft.newClient();
                        return orderService.createWithClient(new NewClientInput(
                                client.fullName(), client.phone(), client.email(), client.login(), client.password()), items);
                    }
                    return orderService.create(draft.client().id(), items);
                },
                id -> refreshOrders(id, "Заказ № " + id + " создан"),
                error -> {
                    setConnectionReady();
                    showError("Не удалось создать заказ", friendlyDirectoryError(error,
                            "Телефон, email или логин нового клиента уже используются."));
                }));
    }

    @FXML
    private void editSelectedOrder() {
        if (busy) return;
        OrderRow selected = ordersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Заказ не выбран", "Выберите строку в таблице и повторите действие.");
            return;
        }
        if (!ensureClients() || !ensureServices()) return;
        runDatabaseOperation("Загрузка заказа", () -> orderService.loadItems(selected.id()), existingItems ->
                showEditDialog(selected, existingItems).ifPresent(draft ->
                        runDatabaseOperation("Сохранение изменений", () -> {
                            List<OrderItemInput> items = draft.items().stream()
                                    .map(item -> new OrderItemInput(
                                            item.service().id(), item.quantity(), item.service().price()))
                                    .toList();
                            if (draft.newClient() != null) {
                                ClientDraft client = draft.newClient();
                                orderService.updateWithClient(selected.id(), new NewClientInput(
                                        client.fullName(), client.phone(), client.email(), client.login(), client.password()),
                                        draft.status(), items);
                            } else {
                                orderService.update(selected.id(), draft.client().id(), draft.status(), items);
                            }
                            return selected.id();
                        }, id -> refreshOrders(id, "Изменения заказа сохранены"), error -> {
                            setConnectionReady();
                            showError("Не удалось сохранить изменения", friendlyDirectoryError(error,
                                    "Телефон, email или логин нового клиента уже используются."));
                        })), error -> {
            setConnectionReady();
            showError("Не удалось загрузить состав заказа", operationErrorMessage(error));
        });
    }

    @FXML
    private void openNewClient() {
        if (busy) return;
        showClientDialog(null).ifPresent(draft -> runDatabaseOperation("Добавление клиента", () -> directoryService.createClient(
                draft.fullName(), draft.phone(), draft.email(), draft.login(), draft.password()),
                id -> refreshOrders(selectedOrderId(), "Клиент добавлен"), error -> {
                    setConnectionReady();
                    showError("Не удалось добавить клиента", friendlyDirectoryError(error, "Телефон, email или логин уже используются."));
                }));
    }

    @FXML
    private void editSelectedClient() {
        if (busy) return;
        ClientOption selected = clientsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Клиент не выбран", "Выберите клиента в таблице и повторите действие.");
            return;
        }
        showClientDialog(selected).ifPresent(draft -> runDatabaseOperation("Сохранение клиента", () -> {
            directoryService.updateClient(selected.id(), selected.userId(), draft.fullName(), draft.phone(), draft.email());
            return selected.id();
        }, id -> refreshOrders(selectedOrderId(), "Данные клиента сохранены"), error -> {
            setConnectionReady();
            showError("Не удалось сохранить клиента", friendlyDirectoryError(error, "Телефон или email уже используются."));
        }));
    }

    @FXML
    private void deleteSelectedClient() {
        if (busy) return;
        ClientOption selected = clientsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Клиент не выбран", "Выберите клиента в таблице и повторите действие.");
            return;
        }
        if (!confirmDeletion("Удалить клиента?", selected.fullName(),
                selected.orderCount() > 0
                        ? "У клиента есть заказы. База данных не позволит удалить связанную запись."
                        : "Контактные данные и учётная запись клиента будут удалены без возможности восстановления.",
                "Удалить клиента")) return;
        runDatabaseOperation("Удаление клиента", () -> {
            directoryService.deleteClient(selected.id(), selected.userId());
            return selected.id();
        }, ignored -> refreshOrders(selectedOrderId(), "Клиент удалён"), error -> {
            setConnectionReady();
            showError("Не удалось удалить клиента", selected.orderCount() > 0
                    ? "Сначала удалите или переназначьте заказы этого клиента."
                    : operationErrorMessage(error));
        });
    }

    @FXML
    private void openNewService() {
        if (busy) return;
        showServiceDialog(null).ifPresent(draft -> runDatabaseOperation("Добавление услуги", () ->
                directoryService.createCatalogService(draft.name(), draft.price()),
                id -> refreshOrders(selectedOrderId(), "Услуга добавлена"), error -> {
                    setConnectionReady();
                    showError("Не удалось добавить услугу", friendlyDirectoryError(error, "Услуга с таким названием уже существует."));
                }));
    }

    @FXML
    private void editSelectedService() {
        if (busy) return;
        ServiceOption selected = servicesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Услуга не выбрана", "Выберите услугу в таблице и повторите действие.");
            return;
        }
        showServiceDialog(selected).ifPresent(draft -> runDatabaseOperation("Сохранение услуги", () -> {
            directoryService.updateCatalogService(selected.id(), draft.name(), draft.price());
            return selected.id();
        }, id -> refreshOrders(selectedOrderId(), "Услуга обновлена"), error -> {
            setConnectionReady();
            showError("Не удалось сохранить услугу", friendlyDirectoryError(error, "Услуга с таким названием уже существует."));
        }));
    }

    @FXML
    private void deleteSelectedService() {
        if (busy) return;
        ServiceOption selected = servicesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Услуга не выбрана", "Выберите услугу в таблице и повторите действие.");
            return;
        }
        if (!confirmDeletion("Удалить услугу?", selected.name(),
                selected.usageCount() > 0
                        ? "Услуга используется в заказах. База данных не позволит удалить связанную запись."
                        : "Услуга исчезнет из каталога без возможности восстановления.",
                "Удалить услугу")) return;
        runDatabaseOperation("Удаление услуги", () -> {
            directoryService.deleteCatalogService(selected.id());
            return selected.id();
        }, ignored -> refreshOrders(selectedOrderId(), "Услуга удалена"), error -> {
            setConnectionReady();
            showError("Не удалось удалить услугу", selected.usageCount() > 0
                    ? "Эта услуга уже используется в заказах и должна остаться в каталоге."
                    : operationErrorMessage(error));
        });
    }

    @FXML
    private void handleEmptyAction() {
        switch (emptyAction) {
            case CREATE -> openNewOrder();
            case RESET -> clearFilters();
            case RETRY -> refreshOrders();
            case NONE -> { }
        }
    }

    private void updateEmptyState() {
        if (tableSkeleton == null || emptyTableState == null || filteredOrders == null) return;
        boolean loadingWithoutRows = dataState == DataState.LOADING && orders.isEmpty();
        tableSkeleton.setVisible(loadingWithoutRows);
        tableSkeleton.setManaged(loadingWithoutRows);
        emptyTableState.setVisible(!loadingWithoutRows);
        emptyTableState.setManaged(!loadingWithoutRows);
        if (loadingWithoutRows) {
            emptyAction = EmptyAction.NONE;
            return;
        }
        if (dataState == DataState.ERROR) {
            configureEmptyState("Не удалось загрузить заказы",
                    "Проверьте подключение к базе данных и повторите попытку.", "Повторить", EmptyAction.RETRY);
        } else if (!orders.isEmpty() && filteredOrders.isEmpty()) {
            configureEmptyState("Ничего не найдено",
                    "Измените поисковый запрос или сбросьте выбранные фильтры.", "Сбросить фильтры", EmptyAction.RESET);
        } else {
            configureEmptyState("Заказов пока нет",
                    "Создайте первый заказ, чтобы начать работу.", "Создать заказ", EmptyAction.CREATE);
        }
    }

    private void configureEmptyState(String title, String copy, String actionText, EmptyAction action) {
        emptyTableTitle.setText(title);
        emptyTableCopy.setText(copy);
        emptyTableActionButton.setText(actionText);
        emptyTableActionButton.setVisible(action != EmptyAction.NONE);
        emptyTableActionButton.setManaged(action != EmptyAction.NONE);
        emptyAction = action;
    }

    private void setConnectionReady() {
        connectionStatusLabel.setText("База данных подключена");
        connectionStatusLabel.getStyleClass().setAll("connection-status", "connection-ok");
        connectionStatusLabel.setTooltip(new Tooltip(gateway.config().endpoint()));
    }

    private <T> void runDatabaseOperation(
            String progressText,
            Callable<T> operation,
            Consumer<T> onSuccess,
            Consumer<Exception> onFailure) {
        if (busy) return;
        setBusy(true, progressText);
        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception {
                return operation.call();
            }
        };
        task.setOnSucceeded(event -> {
            setBusy(false, null);
            onSuccess.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            setBusy(false, null);
            Throwable error = task.getException();
            onFailure.accept(error instanceof Exception exception
                    ? exception
                    : new RuntimeException(error));
        });
        databaseExecutor.execute(task);
    }

    void shutdown() {
        databaseExecutor.shutdownNow();
    }

    private Optional<CreateDraft> showCreateDialog() {
        Dialog<CreateDraft> dialog = baseDialog("Новый заказ");
        ButtonType saveType = new ButtonType("Создать заказ", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, cancelType);

        ComboBox<ClientOption> client = new ComboBox<>(clients);
        client.setMaxWidth(Double.MAX_VALUE);
        configureClientAutocomplete(client);
        CheckBox createClient = new CheckBox("Создать нового клиента");
        createClient.getStyleClass().add("dialog-toggle");

        TextField clientName = new TextField();
        clientName.setPromptText("Иванов Иван Иванович");
        TextField clientPhone = new TextField();
        clientPhone.setPromptText("+7 999 123-45-67");
        TextField clientEmail = new TextField();
        clientEmail.setPromptText("client@example.ru");
        TextField clientLogin = new TextField();
        clientLogin.setPromptText("ivanov");
        PasswordField clientPassword = new PasswordField();
        clientPassword.setPromptText("Не менее 6 символов");
        VBox newClientForm = dialogForm();
        newClientForm.getStyleClass().add("inline-client-form");
        addDialogField(newClientForm, "ФИО", clientName);
        addDialogField(newClientForm, "Телефон", clientPhone);
        addDialogField(newClientForm, "Email (необязательно)", clientEmail);
        addDialogField(newClientForm, "Логин", clientLogin);
        addDialogField(newClientForm, "Временный пароль", clientPassword);

        Runnable updateClientMode = () -> {
            boolean creating = createClient.isSelected();
            client.setVisible(!creating);
            client.setManaged(!creating);
            newClientForm.setVisible(creating);
            newClientForm.setManaged(creating);
        };
        createClient.selectedProperty().addListener((observable, oldValue, value) -> updateClientMode.run());
        if (clients.isEmpty()) {
            createClient.setSelected(true);
            createClient.setDisable(true);
        }
        updateClientMode.run();

        VBox serviceLines = new VBox(8);
        serviceLines.getStyleClass().add("service-lines");
        Label serviceHeaderName = new Label("Услуга");
        Label serviceHeaderQuantity = new Label("Количество");
        Label serviceHeaderAmount = new Label("Сумма");
        serviceHeaderName.setMaxWidth(Double.MAX_VALUE);
        serviceHeaderQuantity.setPrefWidth(104);
        serviceHeaderAmount.setPrefWidth(112);
        HBox.setHgrow(serviceHeaderName, Priority.ALWAYS);
        Region serviceHeaderAction = new Region();
        serviceHeaderAction.setPrefWidth(44);
        HBox serviceColumns = new HBox(8, serviceHeaderName, serviceHeaderQuantity, serviceHeaderAmount, serviceHeaderAction);
        serviceColumns.getStyleClass().add("service-columns");
        List<ServiceLineEditor> lineEditors = new java.util.ArrayList<>();
        Label total = new Label("0 ₽");
        total.getStyleClass().add("dialog-total");
        Runnable[] updateTotal = new Runnable[1];
        updateTotal[0] = () -> {
            long sum = 0;
            for (ServiceLineEditor editor : lineEditors) {
                if (editor.service() != null) {
                    sum = Math.addExact(sum, Math.multiplyExact(editor.service().price(), editor.quantity()));
                }
            }
            total.setText(money(sum));
        };
        Runnable addServiceLine = () -> {
            ServiceLineEditor editor = new ServiceLineEditor(services, updateTotal[0]);
            editor.removeButton().setOnAction(event -> {
                if (lineEditors.size() == 1) {
                    editor.clear();
                    return;
                }
                lineEditors.remove(editor);
                serviceLines.getChildren().remove(editor.node());
                updateTotal[0].run();
            });
            lineEditors.add(editor);
            serviceLines.getChildren().add(editor.node());
            updateTotal[0].run();
        };
        addServiceLine.run();

        Button addServiceButton = new Button("Добавить услугу");
        addServiceButton.getStyleClass().add("button-secondary");
        addServiceButton.setOnAction(event -> addServiceLine.run());
        Label totalCaption = new Label("Итого");
        totalCaption.getStyleClass().add("dialog-total-caption");
        HBox totalRow = new HBox(12, totalCaption, total);
        totalRow.getStyleClass().add("order-total-row");
        HBox.setHgrow(total, Priority.ALWAYS);

        VBox clientSection = dialogSection("Клиент",
                "Выберите клиента из базы или заполните данные нового.", client, createClient, newClientForm);
        VBox servicesSection = dialogSection("Состав заказа",
                "Добавьте одну или несколько разных услуг.", serviceColumns, serviceLines, addServiceButton, totalRow);
        VBox form = new VBox(18, clientSection, servicesSection);
        Label validation = dialogValidationLabel();
        VBox content = dialogContent("Создание заказа",
                "Новый заказ будет сохранён со статусом «Новый».", form, validation);
        ScrollPane formScroll = new ScrollPane(content);
        formScroll.setFitToWidth(true);
        formScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        formScroll.setPrefViewportHeight(650);
        formScroll.getStyleClass().add("dialog-scroll");
        dialog.getDialogPane().setContent(formScroll);
        dialog.getDialogPane().setPrefWidth(640);

        dialog.getDialogPane().lookupButton(saveType).addEventFilter(ActionEvent.ACTION, event -> {
            String message = null;
            if (createClient.isSelected()) {
                message = validateClient(clientName.getText(), clientPhone.getText(), clientEmail.getText(),
                        clientLogin.getText(), clientPassword.getText());
            } else if (resolveClient(client) == null) {
                message = "Выберите клиента или создайте нового.";
            }
            if (message == null) message = validateOrderLines(lineEditors);
            if (message != null) {
                event.consume();
                showDialogValidation(validation, message);
                setFieldInvalid(client, !createClient.isSelected() && resolveClient(client) == null);
                if (createClient.isSelected()) {
                    setFieldInvalid(clientName, clientName.getText().strip().length() < 3);
                    setFieldInvalid(clientPhone, clientPhone.getText().replaceAll("\\D", "").length() < 10);
                    setFieldInvalid(clientEmail, !validEmail(clientEmail.getText()));
                    setFieldInvalid(clientLogin, clientLogin.getText().strip().length() < 3);
                    setFieldInvalid(clientPassword, clientPassword.getText().length() < 6);
                }
                markInvalidOrderLines(lineEditors);
            }
        });
        dialog.setResultConverter(button -> {
            if (button != saveType) return null;
            ClientDraft newClient = createClient.isSelected()
                    ? new ClientDraft(clientName.getText().strip(), clientPhone.getText().strip(), clientEmail.getText().strip(),
                            clientLogin.getText().strip(), clientPassword.getText())
                    : null;
            List<OrderLineDraft> items = lineEditors.stream()
                    .map(editor -> new OrderLineDraft(editor.service(), editor.quantity()))
                    .toList();
            return new CreateDraft(createClient.isSelected() ? null : resolveClient(client), newClient, items);
        });
        return dialog.showAndWait();
    }

    private void configureClientAutocomplete(ComboBox<ClientOption> comboBox) {
        FilteredList<ClientOption> suggestions = new FilteredList<>(clients, value -> true);
        boolean[] selectingSuggestion = {false};
        comboBox.setItems(suggestions);
        comboBox.setEditable(true);
        comboBox.setVisibleRowCount(7);
        comboBox.getEditor().setPromptText("Начните вводить ФИО, телефон или email");
        comboBox.setConverter(new StringConverter<>() {
            @Override public String toString(ClientOption client) {
                return client == null ? "" : client.fullName();
            }

            @Override public ClientOption fromString(String value) {
                String query = normalized(value);
                List<ClientOption> exactMatches = clients.stream()
                        .filter(client -> normalized(client.fullName()).equals(query)
                                || normalized(client.phone()).equals(query)
                                || normalized(client.email()).equals(query))
                        .toList();
                return exactMatches.size() == 1 ? exactMatches.getFirst() : null;
            }
        });
        comboBox.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(ClientOption client, boolean empty) {
                super.updateItem(client, empty);
                if (empty || client == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Label name = new Label(client.fullName());
                name.getStyleClass().add("client-suggestion-name");
                String email = client.email() == null || client.email().isBlank() ? "Email не указан" : client.email();
                Label contacts = new Label(client.phone() + "  |  " + email);
                contacts.getStyleClass().add("client-suggestion-meta");
                VBox content = new VBox(2, name, contacts);
                content.getStyleClass().add("client-suggestion");
                setText(null);
                setGraphic(content);
            }
        });
        comboBox.valueProperty().addListener((observable, oldValue, value) -> {
            if (value == null) return;
            selectingSuggestion[0] = true;
            Platform.runLater(() -> {
                comboBox.hide();
                selectingSuggestion[0] = false;
            });
        });
        comboBox.getEditor().textProperty().addListener((observable, oldValue, value) -> {
            if (selectingSuggestion[0]) return;
            String query = normalized(value);
            suggestions.setPredicate(client -> query.isBlank()
                    || normalized(client.fullName()).contains(query)
                    || normalized(client.phone()).contains(query)
                    || normalized(client.email()).contains(query));
            Platform.runLater(() -> {
                if (comboBox.getEditor().isFocused() && !suggestions.isEmpty()
                        && comboBox.getScene() != null && comboBox.getScene().getWindow().isShowing()) {
                    comboBox.show();
                } else if (suggestions.isEmpty()) {
                    comboBox.hide();
                }
            });
        });
        comboBox.getEditor().focusedProperty().addListener((observable, oldValue, focused) -> {
            if (focused && !suggestions.isEmpty()) Platform.runLater(comboBox::show);
        });
    }

    private ClientOption resolveClient(ComboBox<ClientOption> comboBox) {
        String query = normalized(comboBox.getEditor().getText());
        ClientOption selected = comboBox.getValue();
        if (selected != null && normalized(selected.fullName()).equals(query)) return selected;
        if (query.isBlank()) return null;
        List<ClientOption> matches = clients.stream()
                .filter(client -> normalized(client.fullName()).contains(query)
                        || normalized(client.phone()).contains(query)
                        || normalized(client.email()).contains(query))
                .toList();
        if (matches.size() == 1) {
            comboBox.setValue(matches.getFirst());
            return matches.getFirst();
        }
        return null;
    }

    private Optional<EditDraft> showEditDialog(OrderRow order, List<OrderItemRow> existingItems) {
        Dialog<EditDraft> dialog = baseDialog("Редактирование заказа");
        ButtonType saveType = new ButtonType("Сохранить изменения", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, cancelType);

        ComboBox<ClientOption> client = new ComboBox<>(clients);
        client.setMaxWidth(Double.MAX_VALUE);
        configureClientAutocomplete(client);
        clients.stream().filter(value -> value.id() == order.clientId()).findFirst().ifPresent(client::setValue);
        CheckBox createClient = new CheckBox("Создать нового клиента");
        createClient.getStyleClass().add("dialog-toggle");

        TextField clientName = new TextField();
        clientName.setPromptText("Иванов Иван Иванович");
        TextField clientPhone = new TextField();
        clientPhone.setPromptText("+7 999 123-45-67");
        TextField clientEmail = new TextField();
        clientEmail.setPromptText("client@example.ru");
        TextField clientLogin = new TextField();
        clientLogin.setPromptText("ivanov");
        PasswordField clientPassword = new PasswordField();
        clientPassword.setPromptText("Не менее 6 символов");
        VBox newClientForm = dialogForm();
        newClientForm.getStyleClass().add("inline-client-form");
        addDialogField(newClientForm, "ФИО", clientName);
        addDialogField(newClientForm, "Телефон", clientPhone);
        addDialogField(newClientForm, "Email (необязательно)", clientEmail);
        addDialogField(newClientForm, "Логин", clientLogin);
        addDialogField(newClientForm, "Временный пароль", clientPassword);
        Runnable updateClientMode = () -> {
            boolean creating = createClient.isSelected();
            client.setVisible(!creating);
            client.setManaged(!creating);
            newClientForm.setVisible(creating);
            newClientForm.setManaged(creating);
        };
        createClient.selectedProperty().addListener((observable, oldValue, value) -> updateClientMode.run());
        updateClientMode.run();

        ComboBox<OrderStatus> status = new ComboBox<>(FXCollections.observableArrayList(OrderStatus.values()));
        status.setConverter(statusConverter());
        status.setValue(order.status());
        status.setMaxWidth(Double.MAX_VALUE);

        VBox serviceLines = new VBox(8);
        serviceLines.getStyleClass().add("service-lines");
        HBox serviceColumns = serviceColumns();
        List<ServiceLineEditor> lineEditors = new java.util.ArrayList<>();
        Label total = new Label("0 ₽");
        total.getStyleClass().add("dialog-total");
        Runnable updateTotal = () -> total.setText(money(lineEditors.stream()
                .filter(editor -> editor.service() != null)
                .mapToLong(editor -> Math.multiplyExact(editor.service().price(), editor.quantity()))
                .sum()));
        for (OrderItemRow item : existingItems) {
            services.stream()
                    .filter(service -> service.id() == item.serviceId())
                    .findFirst()
                    .ifPresent(service -> appendServiceLine(
                            serviceLines, lineEditors, updateTotal, service, item.quantity()));
        }
        if (lineEditors.isEmpty()) appendServiceLine(serviceLines, lineEditors, updateTotal, null, 1);

        Button addServiceButton = new Button("Добавить услугу");
        addServiceButton.getStyleClass().add("button-secondary");
        addServiceButton.setOnAction(event -> appendServiceLine(
                serviceLines, lineEditors, updateTotal, null, 1));
        Label totalCaption = new Label("Итого");
        totalCaption.getStyleClass().add("dialog-total-caption");
        HBox totalRow = new HBox(12, totalCaption, total);
        totalRow.getStyleClass().add("order-total-row");
        HBox.setHgrow(total, Priority.ALWAYS);

        VBox clientSection = dialogSection("Клиент", null, client, createClient, newClientForm);
        VBox statusSection = dialogSection("Статус", null, status);
        VBox servicesSection = dialogSection("Состав заказа", null,
                serviceColumns, serviceLines, addServiceButton, totalRow);
        VBox form = new VBox(18, clientSection, statusSection, servicesSection);
        Label validationMessage = dialogValidationLabel();
        VBox content = dialogContent("Заказ № " + order.id(), null, form, validationMessage);
        ScrollPane formScroll = new ScrollPane(content);
        formScroll.setFitToWidth(true);
        formScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        formScroll.setPrefViewportHeight(650);
        formScroll.getStyleClass().add("dialog-scroll");
        dialog.getDialogPane().setContent(formScroll);
        dialog.getDialogPane().setPrefWidth(640);

        dialog.getDialogPane().lookupButton(saveType).addEventFilter(ActionEvent.ACTION, event -> {
            String message = null;
            if (createClient.isSelected()) {
                message = validateClient(clientName.getText(), clientPhone.getText(), clientEmail.getText(),
                        clientLogin.getText(), clientPassword.getText());
            } else if (resolveClient(client) == null) {
                message = "Выберите клиента или создайте нового.";
            }
            if (message == null && status.getValue() == null) message = "Выберите статус заказа.";
            if (message == null) message = validateOrderLines(lineEditors);
            if (message != null) {
                event.consume();
                showDialogValidation(validationMessage, message);
                setFieldInvalid(client, !createClient.isSelected() && resolveClient(client) == null);
                setFieldInvalid(status, status.getValue() == null);
                if (createClient.isSelected()) {
                    setFieldInvalid(clientName, clientName.getText().strip().length() < 3);
                    setFieldInvalid(clientPhone, clientPhone.getText().replaceAll("\\D", "").length() < 10);
                    setFieldInvalid(clientEmail, !validEmail(clientEmail.getText()));
                    setFieldInvalid(clientLogin, clientLogin.getText().strip().length() < 3);
                    setFieldInvalid(clientPassword, clientPassword.getText().length() < 6);
                }
                markInvalidOrderLines(lineEditors);
            }
        });
        dialog.setResultConverter(button -> {
            if (button != saveType) return null;
            ClientDraft newClient = createClient.isSelected()
                    ? new ClientDraft(clientName.getText().strip(), clientPhone.getText().strip(), clientEmail.getText().strip(),
                            clientLogin.getText().strip(), clientPassword.getText())
                    : null;
            List<OrderLineDraft> items = lineEditors.stream()
                    .map(editor -> new OrderLineDraft(editor.service(), editor.quantity()))
                    .toList();
            return new EditDraft(createClient.isSelected() ? null : resolveClient(client), newClient,
                    status.getValue(), items);
        });
        return dialog.showAndWait();
    }

    private Optional<ClientDraft> showClientDialog(ClientOption existing) {
        boolean creating = existing == null;
        Dialog<ClientDraft> dialog = baseDialog(creating ? "Новый клиент" : "Редактирование клиента");
        ButtonType saveType = new ButtonType(creating ? "Добавить клиента" : "Сохранить изменения", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, cancelType);

        TextField name = new TextField(creating ? "" : existing.fullName());
        name.setPromptText("Иванов Иван Иванович");
        TextField phone = new TextField(creating ? "" : existing.phone());
        phone.setPromptText("+7 999 123-45-67");
        TextField email = new TextField(creating || existing.email() == null ? "" : existing.email());
        email.setPromptText("client@example.ru");
        TextField login = new TextField();
        login.setPromptText("ivanov");
        PasswordField password = new PasswordField();
        password.setPromptText("Не менее 6 символов");

        VBox form = dialogForm();
        addDialogField(form, "ФИО", name);
        addDialogField(form, "Телефон", phone);
        addDialogField(form, "Email (необязательно)", email);
        if (creating) {
            addDialogField(form, "Логин", login);
            addDialogField(form, "Временный пароль", password);
        }
        Label validation = dialogValidationLabel();
        String hint = creating
                ? "Контактные данные используются в заказах. Логин и пароль создают связанную учётную запись."
                : "Изменения имени синхронизируются со связанной учётной записью.";
        dialog.getDialogPane().setContent(dialogContent(creating ? "Добавление клиента" : existing.fullName(), hint, form, validation));

        dialog.getDialogPane().lookupButton(saveType).addEventFilter(ActionEvent.ACTION, event -> {
            String message = validateClient(name.getText(), phone.getText(), email.getText(),
                    creating ? login.getText() : "existing", creating ? password.getText() : "existing");
            if (message != null) {
                event.consume();
                showDialogValidation(validation, message);
                setFieldInvalid(name, name.getText().strip().length() < 3);
                setFieldInvalid(phone, phone.getText().replaceAll("\\D", "").length() < 10);
                setFieldInvalid(email, !validEmail(email.getText()));
                if (creating) {
                    setFieldInvalid(login, login.getText().strip().length() < 3);
                    setFieldInvalid(password, password.getText().length() < 6);
                }
            }
        });
        dialog.setResultConverter(button -> button == saveType
                ? new ClientDraft(name.getText().strip(), phone.getText().strip(), email.getText().strip(),
                        creating ? login.getText().strip() : "", creating ? password.getText() : "")
                : null);
        Platform.runLater(name::requestFocus);
        return dialog.showAndWait();
    }

    private Optional<ServiceDraft> showServiceDialog(ServiceOption existing) {
        boolean creating = existing == null;
        Dialog<ServiceDraft> dialog = baseDialog(creating ? "Новая услуга" : "Редактирование услуги");
        ButtonType saveType = new ButtonType(creating ? "Добавить услугу" : "Сохранить изменения", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, cancelType);

        TextField name = new TextField(creating ? "" : existing.name());
        name.setPromptText("Название услуги");
        TextField price = new TextField(creating ? "" : String.valueOf(existing.price()));
        price.setPromptText("Стоимость в рублях");
        VBox form = dialogForm();
        addDialogField(form, "Название", name);
        addDialogField(form, "Цена", price);
        Label validation = dialogValidationLabel();
        dialog.getDialogPane().setContent(dialogContent(creating ? "Добавление услуги" : existing.name(),
                "Цена применяется к новым заказам. Уже созданные заказы сохраняют прежнюю стоимость.", form, validation));

        dialog.getDialogPane().lookupButton(saveType).addEventFilter(ActionEvent.ACTION, event -> {
            String message = validateService(name.getText(), price.getText());
            if (message != null) {
                event.consume();
                showDialogValidation(validation, message);
                setFieldInvalid(name, name.getText().strip().length() < 3);
                setFieldInvalid(price, !validPositiveAmount(price.getText()));
            }
        });
        dialog.setResultConverter(button -> button == saveType
                ? new ServiceDraft(name.getText().strip(), parseAmount(price.getText()))
                : null);
        Platform.runLater(name::requestFocus);
        return dialog.showAndWait();
    }

    private <T> Dialog<T> baseDialog(String title) {
        Dialog<T> dialog = new Dialog<>();
        dialog.initOwner(sceneRoot.getScene().getWindow());
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.setResizable(true);
        dialog.getDialogPane().setMinWidth(560);
        dialog.getDialogPane().setPrefWidth(560);
        styleDialog(dialog);
        dialog.getDialogPane().getStyleClass().add("order-dialog");
        return dialog;
    }

    private VBox dialogForm() {
        VBox form = new VBox(12);
        form.getStyleClass().add("dialog-form-grid");
        return form;
    }

    private VBox dialogSection(String heading, String copy, Node... children) {
        Label title = new Label(heading);
        title.getStyleClass().add("dialog-section-title");
        VBox section = new VBox(10);
        section.getStyleClass().add("dialog-section");
        section.getChildren().add(title);
        if (copy != null && !copy.isBlank()) {
            Label description = new Label(copy);
            description.setWrapText(true);
            description.getStyleClass().add("dialog-section-copy");
            section.getChildren().add(description);
        }
        section.getChildren().addAll(children);
        return section;
    }

    private VBox dialogContent(String heading, String hint, VBox form, Label validation) {
        Label title = new Label(heading);
        title.setWrapText(true);
        title.getStyleClass().add("dialog-title");
        VBox intro = new VBox(6, title);
        if (hint != null && !hint.isBlank()) {
            Label copy = new Label(hint);
            copy.setWrapText(true);
            copy.getStyleClass().add("dialog-copy");
            intro.getChildren().add(copy);
        }
        intro.getStyleClass().add("dialog-intro");
        VBox content = new VBox(20, intro, form, validation);
        content.setPadding(new Insets(10, 8, 6, 8));
        return content;
    }

    private void addDialogField(VBox form, String title, Node control) {
        Label label = new Label(title);
        label.getStyleClass().add("field-label");
        VBox field = new VBox(6, label, control);
        field.getStyleClass().add("dialog-field");
        form.getChildren().add(field);
    }

    private Label dialogValidationLabel() {
        Label validation = new Label();
        validation.setWrapText(true);
        validation.setVisible(false);
        validation.setManaged(false);
        validation.getStyleClass().add("dialog-validation");
        return validation;
    }

    private void showDialogValidation(Label validation, String message) {
        validation.setText(message);
        validation.setVisible(true);
        validation.setManaged(true);
    }

    private void setFieldInvalid(Node control, boolean invalid) {
        control.getStyleClass().remove("field-invalid");
        if (invalid) control.getStyleClass().add("field-invalid");
    }

    private HBox serviceColumns() {
        Label serviceHeaderName = new Label("Услуга");
        Label serviceHeaderQuantity = new Label("Количество");
        Label serviceHeaderAmount = new Label("Сумма");
        serviceHeaderName.setMaxWidth(Double.MAX_VALUE);
        serviceHeaderQuantity.setPrefWidth(104);
        serviceHeaderAmount.setPrefWidth(112);
        HBox.setHgrow(serviceHeaderName, Priority.ALWAYS);
        Region serviceHeaderAction = new Region();
        serviceHeaderAction.setPrefWidth(44);
        HBox columns = new HBox(8,
                serviceHeaderName, serviceHeaderQuantity, serviceHeaderAmount, serviceHeaderAction);
        columns.getStyleClass().add("service-columns");
        return columns;
    }

    private ServiceLineEditor appendServiceLine(
            VBox serviceLines,
            List<ServiceLineEditor> editors,
            Runnable updateTotal,
            ServiceOption initialService,
            int initialQuantity) {
        ServiceLineEditor editor = new ServiceLineEditor(services, updateTotal);
        editor.removeButton().setOnAction(event -> {
            if (editors.size() == 1) {
                editor.clear();
                return;
            }
            editors.remove(editor);
            serviceLines.getChildren().remove(editor.node());
            updateTotal.run();
        });
        editors.add(editor);
        serviceLines.getChildren().add(editor.node());
        editor.setValue(initialService, initialQuantity);
        updateTotal.run();
        return editor;
    }

    private String validateOrderLines(List<ServiceLineEditor> editors) {
        if (editors.isEmpty()) return "Добавьте хотя бы одну услугу.";
        Set<Integer> serviceIds = new HashSet<>();
        for (ServiceLineEditor editor : editors) {
            if (editor.service() == null) return "Выберите услугу в каждой строке состава заказа.";
            if (editor.quantity() < 1) return "Количество каждой услуги должно быть больше нуля.";
            if (!serviceIds.add(editor.service().id())) {
                return "Одна услуга добавлена несколько раз. Измените строку или увеличьте количество.";
            }
        }
        return null;
    }

    private void markInvalidOrderLines(List<ServiceLineEditor> editors) {
        Set<Integer> serviceIds = new HashSet<>();
        for (ServiceLineEditor editor : editors) {
            ServiceOption service = editor.service();
            boolean duplicate = service != null && !serviceIds.add(service.id());
            setFieldInvalid(editor.serviceControl(), service == null || duplicate);
            setFieldInvalid(editor.quantityControl(), editor.quantity() < 1);
        }
    }

    private String validateClient(String name, String phone, String email, String login, String password) {
        if (name == null || name.strip().length() < 3) return "Укажите полное имя клиента.";
        if (phone == null || phone.replaceAll("\\D", "").length() < 10) return "Укажите телефон минимум из 10 цифр.";
        if (!validEmail(email)) return "Проверьте формат email или оставьте поле пустым.";
        if (login == null || login.strip().length() < 3) return "Логин должен содержать не менее 3 символов.";
        if (password == null || password.length() < 6) return "Временный пароль должен содержать не менее 6 символов.";
        return null;
    }

    private boolean validEmail(String email) {
        if (email == null || email.isBlank()) return true;
        String value = email.strip();
        int at = value.indexOf('@');
        return at > 0 && at == value.lastIndexOf('@') && value.indexOf('.', at) > at + 1 && !value.endsWith(".");
    }

    private String validateService(String name, String price) {
        if (name == null || name.strip().length() < 3) return "Название должно содержать не менее 3 символов.";
        if (!validPositiveAmount(price)) return "Цена должна быть целым числом больше нуля.";
        return null;
    }

    private boolean validPositiveAmount(String amount) {
        try {
            return parseAmount(amount) > 0;
        } catch (NumberFormatException error) {
            return false;
        }
    }

    private long parseAmount(String value) {
        return Long.parseLong(value == null ? "" : value.replace(" ", "").replace("\u00A0", "").strip());
    }

    private void showOrder(OrderRow order) {
        showOnly(orderDetail);
        detailNumber.setText("Заказ № " + order.id());
        detailStatus.setText(order.status().label());
        detailStatus.getStyleClass().setAll("status-badge", "detail-status", statusClass(order.status()));
        detailClient.setText(order.clientName());
        detailPhone.setText(order.phone());
        detailEmail.setText(order.email() == null || order.email().isBlank() ? "Email не указан" : order.email());
        detailServices.setText(order.services());
        detailDate.setText(order.createdAt().format(DATE_TIME));
        detailTotal.setText(money(order.total()));
        updateAdvanceAction(order);
    }

    private void updateAdvanceAction(OrderRow order) {
        switch (order.status()) {
            case NEW -> advanceStatusButton.setText("Принять в работу");
            case IN_PROGRESS -> advanceStatusButton.setText("Завершить заказ");
            case COMPLETED -> advanceStatusButton.setText("Заказ завершён");
            case CANCELLED -> advanceStatusButton.setText("Заказ отменён");
        }
        advanceStatusButton.setDisable(busy
                || order.status() == OrderStatus.COMPLETED
                || order.status() == OrderStatus.CANCELLED);
    }

    @FXML
    private void advanceStatus() {
        if (busy) return;
        OrderRow order = ordersTable.getSelectionModel().getSelectedItem();
        if (order == null) {
            showError("Заказ не выбран", "Выберите строку в таблице и повторите действие.");
            return;
        }
        OrderStatus next = order.status() == OrderStatus.NEW ? OrderStatus.IN_PROGRESS : OrderStatus.COMPLETED;
        runDatabaseOperation("Обновление статуса", () -> {
            orderService.updateStatus(order.id(), next);
            return order.id();
        }, id -> refreshOrders(id, "Статус заказа обновлён"), error -> {
            setConnectionReady();
            showError("Не удалось изменить статус", operationErrorMessage(error));
        });
    }

    @FXML
    private void deleteOrder() {
        if (busy) return;
        OrderRow order = ordersTable.getSelectionModel().getSelectedItem();
        if (order == null) {
            showError("Заказ не выбран", "Выберите строку в таблице и повторите действие.");
            return;
        }
        if (confirmOrderDeletion(order)) {
            runDatabaseOperation("Удаление заказа", () -> {
                orderService.delete(order.id());
                return order.id();
            }, ignored -> refreshOrders(-1, "Заказ удалён"), error -> {
                setConnectionReady();
                showError("Не удалось удалить заказ", operationErrorMessage(error));
            });
        }
    }

    private boolean confirmOrderDeletion(OrderRow order) {
        Dialog<ButtonType> dialog = baseDialog("Удаление заказа");
        DialogPane pane = dialog.getDialogPane();
        pane.getStyleClass().add("destructive-dialog");
        pane.setMinWidth(520);
        pane.setPrefWidth(520);
        dialog.setResizable(false);

        ButtonType deleteType = new ButtonType("Удалить заказ", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().setAll(deleteType, cancelType);

        Button deleteButton = (Button) pane.lookupButton(deleteType);
        FontIcon buttonIcon = new FontIcon(MaterialDesignD.DELETE_OUTLINE);
        buttonIcon.setIconSize(17);
        deleteButton.setGraphic(buttonIcon);
        deleteButton.getStyleClass().add("destructive-primary");
        deleteButton.setDefaultButton(false);

        Button cancelButton = (Button) pane.lookupButton(cancelType);
        cancelButton.getStyleClass().add("destructive-cancel");
        cancelButton.setDefaultButton(true);

        FontIcon warningIcon = new FontIcon(MaterialDesignD.DELETE_ALERT_OUTLINE);
        warningIcon.setIconSize(23);
        StackPane iconBox = new StackPane(warningIcon);
        iconBox.getStyleClass().add("destructive-icon-box");

        Label title = new Label("Удалить заказ № " + order.id() + "?");
        title.setWrapText(true);
        title.getStyleClass().add("destructive-title");
        Label explanation = new Label("Проверьте данные перед удалением. Восстановить заказ после этого не получится.");
        explanation.setWrapText(true);
        explanation.getStyleClass().add("destructive-copy");
        VBox heading = new VBox(6, title, explanation);
        HBox.setHgrow(heading, Priority.ALWAYS);
        HBox header = new HBox(14, iconBox, heading);
        header.setAlignment(Pos.TOP_LEFT);
        header.getStyleClass().add("destructive-header");

        Label clientCaption = new Label("Клиент");
        clientCaption.getStyleClass().add("delete-summary-label");
        Label client = new Label(order.clientName());
        client.setWrapText(true);
        client.getStyleClass().add("delete-summary-client");

        Label servicesCaption = new Label("Состав заказа");
        servicesCaption.getStyleClass().add("delete-summary-label");
        Label servicesValue = new Label(order.services());
        servicesValue.setWrapText(true);
        servicesValue.getStyleClass().add("delete-summary-value");

        Label totalCaption = new Label("Сумма");
        totalCaption.getStyleClass().add("delete-summary-label");
        Label total = new Label(money(order.total()));
        total.getStyleClass().add("delete-summary-total");
        Region totalSpacer = new Region();
        HBox.setHgrow(totalSpacer, Priority.ALWAYS);
        HBox totalRow = new HBox(12, totalCaption, totalSpacer, total);
        totalRow.setAlignment(Pos.CENTER_LEFT);
        totalRow.getStyleClass().add("delete-summary-total-row");

        VBox summary = new VBox(7, clientCaption, client, servicesCaption, servicesValue, totalRow);
        summary.getStyleClass().add("delete-summary");

        Label consequence = new Label("Вместе с заказом будут удалены все его позиции.");
        consequence.setWrapText(true);
        consequence.getStyleClass().add("destructive-consequence");

        VBox content = new VBox(18, header, summary, consequence);
        content.getStyleClass().add("destructive-content");
        pane.setContent(content);
        Platform.runLater(cancelButton::requestFocus);
        return dialog.showAndWait().filter(deleteType::equals).isPresent();
    }

    private boolean confirmDeletion(String heading, String subject, String copy, String actionText) {
        Dialog<ButtonType> dialog = baseDialog("Подтверждение удаления");
        dialog.getDialogPane().getStyleClass().add("destructive-dialog");
        ButtonType deleteType = new ButtonType(actionText, ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().setAll(deleteType, cancelType);
        Button deleteButton = (Button) dialog.getDialogPane().lookupButton(deleteType);
        FontIcon deleteIcon = new FontIcon(MaterialDesignD.DELETE_OUTLINE);
        deleteIcon.setIconSize(17);
        deleteButton.setGraphic(deleteIcon);
        deleteButton.getStyleClass().add("destructive-primary");
        deleteButton.setDefaultButton(false);
        Button cancelButton = (Button) dialog.getDialogPane().lookupButton(cancelType);
        cancelButton.getStyleClass().add("destructive-cancel");
        cancelButton.setDefaultButton(true);
        Label subjectLabel = new Label(subject);
        subjectLabel.setWrapText(true);
        subjectLabel.getStyleClass().add("dialog-subject");
        VBox body = new VBox(12, subjectLabel);
        body.getStyleClass().add("delete-summary");
        dialog.getDialogPane().setContent(dialogContent(heading, copy, body, dialogValidationLabel()));
        Platform.runLater(cancelButton::requestFocus);
        return dialog.showAndWait().filter(value -> value == deleteType).isPresent();
    }

    @FXML
    private void exportOrders() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Экспорт списка заказов");
        chooser.setInitialFileName("orders.csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File file = chooser.showSaveDialog(sceneRoot.getScene().getWindow());
        if (file == null) return;
        try {
            CsvExportService.writeOrders(file.toPath(), List.copyOf(ordersTable.getItems()), DATE_TIME);
            showToast("CSV сохранён", false);
        } catch (IOException error) {
            showError("Не удалось сохранить файл", "Проверьте доступ к выбранной папке и повторите экспорт.");
        }
    }

    @FXML
    private void showOrdersPage() {
        showPage(contentRoot, ordersNavButton);
    }

    @FXML
    private void showOverview() {
        updateStatistics();
        showPage(overviewRoot, overviewNavButton);
    }

    @FXML
    private void showClients() {
        applyClientFilter();
        showPage(clientsRoot, clientsNavButton);
    }

    @FXML
    private void showServices() {
        applyServiceFilter();
        showPage(servicesRoot, servicesNavButton);
    }

    @FXML
    private void showStatistics() {
        updateStatistics();
        showPage(statisticsRoot, analyticsNavButton);
    }

    private void showPage(VBox pageToShow, Button activeButton) {
        activePage = pageToShow;
        for (VBox page : List.of(overviewRoot, contentRoot, clientsRoot, servicesRoot, statisticsRoot)) {
            boolean visible = page == pageToShow;
            page.setVisible(visible);
            page.setManaged(visible);
        }
        for (Button button : List.of(overviewNavButton, ordersNavButton, clientsNavButton, servicesNavButton, analyticsNavButton)) {
            button.getStyleClass().setAll(button == activeButton ? "nav-button-active" : "nav-button");
        }
        contentScroll.setVvalue(0);
    }

    private void updateStatistics() {
        DashboardStatistics statistics = AnalyticsService.calculate(orders);

        overviewOrdersLabel.setText(String.valueOf(statistics.total()));
        overviewClientsLabel.setText(String.valueOf(clients.size()));
        overviewServicesLabel.setText(String.valueOf(services.size()));
        overviewActiveLabel.setText(String.valueOf(statistics.activeCount()));
        overviewNewLabel.setText(String.valueOf(statistics.newCount()));
        overviewProgressLabel.setText(String.valueOf(statistics.progressCount()));
        overviewCompletedLabel.setText(String.valueOf(statistics.completedCount()));
        overviewOrdersTable.setItems(FXCollections.observableArrayList(orders.stream().limit(5).toList()));

        totalOrdersLabel.setText(String.valueOf(statistics.total()));
        activeOrdersLabel.setText(String.valueOf(statistics.activeCount()));
        completedOrdersLabel.setText(String.valueOf(statistics.completedCount()));
        activeSumLabel.setText(money(statistics.activeSum()));
        statsTotalOrdersLabel.setText(statistics.total() + " " + ordersWord(statistics.total()));
        statsActiveSumLabel.setText(money(statistics.activeSum()));
        statsNewCountLabel.setText(String.valueOf(statistics.newCount()));
        statsProgressCountLabel.setText(String.valueOf(statistics.progressCount()));
        statsCompletedCountLabel.setText(String.valueOf(statistics.completedCount()));
        statsCancelledCountLabel.setText(String.valueOf(statistics.cancelledCount()));
        statsNewProgress.setProgress(statistics.share(statistics.newCount()));
        statsProgressProgress.setProgress(statistics.share(statistics.progressCount()));
        statsCompletedProgress.setProgress(statistics.share(statistics.completedCount()));
        statsCancelledProgress.setProgress(statistics.share(statistics.cancelledCount()));
        statsPortfolioSumLabel.setText(money(statistics.portfolioSum()));
        statsAverageOrderLabel.setText(money(statistics.average()));
        if (filteredOrders != null) resultCountLabel.setText("Показано: " + filteredOrders.size());
    }

    private boolean ensureClients() {
        if (!clients.isEmpty()) return true;
        showError("Нет клиентов", "Добавьте клиента перед редактированием заказа.");
        return false;
    }

    private boolean ensureServices() {
        if (!services.isEmpty()) return true;
        showError("Нет услуг", "Добавьте хотя бы одну услугу в каталог перед созданием заказа.");
        return false;
    }

    private int selectedOrderId() {
        OrderRow selected = ordersTable.getSelectionModel().getSelectedItem();
        return selected == null ? -1 : selected.id();
    }

    private void selectOrder(int id) {
        Optional<OrderRow> match = ordersTable.getItems().stream().filter(order -> order.id() == id).findFirst();
        if (match.isEmpty() && !ordersTable.getItems().isEmpty()) match = Optional.of(ordersTable.getItems().getFirst());
        match.ifPresent(order -> {
            ordersTable.getSelectionModel().select(order);
            ordersTable.scrollTo(order);
        });
    }

    private void setBusy(boolean busy, String progressText) {
        this.busy = busy;
        if (busy) {
            connectionStatusLabel.setText(progressText == null ? "Выполняется операция" : progressText);
            connectionStatusLabel.getStyleClass().setAll("connection-status");
        }
        updateActionAvailability();
    }

    private void updateActionAvailability() {
        OrderRow selected = ordersTable.getSelectionModel().getSelectedItem();
        ClientOption selectedClient = clientsTable.getSelectionModel().getSelectedItem();
        ServiceOption selectedService = servicesTable.getSelectionModel().getSelectedItem();
        refreshButton.setDisable(busy);
        statsRefreshButton.setDisable(busy);
        statsApplyPeriodButton.setDisable(busy);
        statsFromDate.setDisable(busy);
        statsToDate.setDisable(busy);
        searchField.setDisable(busy);
        statusFilter.setDisable(busy);
        ordersTable.setDisable(busy && !orders.isEmpty());
        createButton.setDisable(busy || dataState == DataState.ERROR);
        exportButton.setDisable(busy || filteredOrders == null || filteredOrders.isEmpty());
        editButton.setDisable(busy || selected == null);
        deleteButton.setDisable(busy || selected == null);
        clientSearchField.setDisable(busy);
        serviceSearchField.setDisable(busy);
        clientsTable.setDisable(busy && !clients.isEmpty());
        servicesTable.setDisable(busy && !services.isEmpty());
        clientCreateButton.setDisable(busy || dataState == DataState.ERROR);
        clientEditButton.setDisable(busy || selectedClient == null);
        clientDeleteButton.setDisable(busy || selectedClient == null);
        serviceCreateButton.setDisable(busy || dataState == DataState.ERROR);
        serviceEditButton.setDisable(busy || selectedService == null);
        serviceDeleteButton.setDisable(busy || selectedService == null);
        if (selected == null) {
            advanceStatusButton.setDisable(true);
        } else {
            updateAdvanceAction(selected);
        }
    }

    private void showOnly(Node visiblePane) {
        List.of(emptyDetail, orderDetail).forEach(pane -> {
            boolean visible = pane == visiblePane;
            pane.setVisible(visible);
            pane.setManaged(visible);
        });
    }

    private void showError(String header, String message) {
        Dialog<ButtonType> dialog = baseDialog("Ошибка");
        dialog.getDialogPane().getStyleClass().add("error-dialog");
        ButtonType closeType = new ButtonType("Понятно", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(closeType);
        VBox body = new VBox();
        dialog.getDialogPane().setContent(dialogContent(header, message, body, dialogValidationLabel()));
        dialog.showAndWait();
    }

    private String databaseErrorMessage(SQLException error) {
        String state = error.getSQLState();
        if ("08001".equals(state) && error.getMessage() != null
                && error.getMessage().contains("Настройки базы данных не найдены")) {
            return "Создайте database.properties по образцу database.properties.example или задайте переменные MEMORY_DB_URL, MEMORY_DB_USER и MEMORY_DB_PASSWORD.";
        }
        if ("3D000".equals(state)) {
            return "База funeral_services не создана. Выполните backend/src/db/schema.sql, затем backend/src/db/data.sql и нажмите «Обновить».";
        }
        if ("42P01".equals(state)) {
            return "Подключение установлено, но схема неполная. Выполните backend/src/db/schema.sql и backend/src/db/data.sql.";
        }
        if (state != null && state.startsWith("28")) {
            return "PostgreSQL отклонил логин или пароль. Проверьте MEMORY_DB_USER и MEMORY_DB_PASSWORD.";
        }
        return "Проверьте, что PostgreSQL запущен, а параметры подключения указаны верно.";
    }

    private String operationErrorMessage(Exception error) {
        if (error instanceof SQLException sql && sql.getSQLState() != null && sql.getSQLState().startsWith("08")) {
            return "Соединение с базой данных потеряно. Проверьте PostgreSQL и повторите действие.";
        }
        return "Операция не выполнена. Обновите данные и повторите попытку.";
    }

    private String friendlyDirectoryError(Exception error, String duplicateMessage) {
        if (error instanceof SQLException sql) {
            if ("23505".equals(sql.getSQLState())) return duplicateMessage;
            if ("23503".equals(sql.getSQLState())) return "Запись используется в заказах и не может быть удалена.";
            if (sql.getSQLState() != null && sql.getSQLState().startsWith("08")) {
                return "Соединение с базой данных потеряно. Проверьте PostgreSQL и повторите действие.";
            }
        }
        return operationErrorMessage(error);
    }

    private void showToast(String message, boolean destructive) {
        if (toastTimer != null) toastTimer.stop();
        toastLabel.setText(message);
        toastLabel.getStyleClass().setAll("toast", destructive ? "toast-danger" : "toast-info");
        toastLabel.setVisible(true);
        toastLabel.setManaged(true);
        toastLabel.setOpacity(1);
        toastLabel.setTranslateY(0);
        if (!REDUCE_MOTION) {
            toastLabel.setOpacity(0);
            toastLabel.setTranslateY(8);
            playEnter(toastLabel);
        }
        toastTimer = new PauseTransition(Duration.seconds(2.4));
        toastTimer.setOnFinished(event -> {
            FadeTransition fade = new FadeTransition(Duration.millis(160), toastLabel);
            fade.setToValue(0);
            fade.setOnFinished(done -> {
                toastLabel.setVisible(false);
                toastLabel.setManaged(false);
            });
            if (REDUCE_MOTION) fade.setDuration(Duration.millis(1));
            fade.play();
        });
        toastTimer.play();
    }

    private void playEnter(Node node) {
        if (REDUCE_MOTION) {
            node.setOpacity(1);
            node.setTranslateY(0);
            return;
        }
        node.setOpacity(0);
        node.setTranslateY(6);
        FadeTransition fade = new FadeTransition(Duration.millis(180), node);
        fade.setToValue(1);
        fade.setInterpolator(EASE_OUT);
        TranslateTransition move = new TranslateTransition(Duration.millis(180), node);
        move.setToY(0);
        move.setInterpolator(EASE_OUT);
        new ParallelTransition(fade, move).play();
    }

    private void styleDialog(Dialog<?> dialog) {
        DialogPane pane = dialog.getDialogPane();
        pane.getStylesheets().add(MemoryFxApplication.class.getResource("/ru/memory/fx/theme.css").toExternalForm());
        pane.getStyleClass().add("memory-dialog");
    }

    private StringConverter<OrderStatus> statusConverter() {
        return new StringConverter<>() {
            @Override public String toString(OrderStatus value) { return value == null ? "" : value.label(); }
            @Override public OrderStatus fromString(String value) { return null; }
        };
    }

    private static String statusClass(OrderStatus status) {
        return switch (status) {
            case NEW -> "status-new";
            case IN_PROGRESS -> "status-progress";
            case COMPLETED -> "status-completed";
            case CANCELLED -> "status-cancelled";
        };
    }

    private static String money(long value) {
        return MONEY.format(value) + " ₽";
    }

    private static String money(BigDecimal value) {
        return PRECISE_MONEY.format(value == null ? BigDecimal.ZERO : value) + " ₽";
    }

    private static java.text.NumberFormat preciseMoneyFormat() {
        java.text.NumberFormat format = java.text.NumberFormat.getNumberInstance(Locale.forLanguageTag("ru-RU"));
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(2);
        return format;
    }

    private static String ordersWord(long value) {
        long lastTwo = value % 100;
        if (lastTwo >= 11 && lastTwo <= 14) return "заказов";
        return switch ((int) (value % 10)) {
            case 1 -> "заказ";
            case 2, 3, 4 -> "заказа";
            default -> "заказов";
        };
    }

    private final class ServiceLineEditor {
        private final HBox node = new HBox(8);
        private final ComboBox<ServiceOption> service = new ComboBox<>();
        private final Spinner<Integer> quantity = new Spinner<>(1, 99, 1);
        private final Label amount = new Label("0 ₽");
        private final Button remove = new Button();
        private final Runnable onChanged;

        private ServiceLineEditor(ObservableList<ServiceOption> availableServices, Runnable onChanged) {
            this.onChanged = onChanged;
            service.setItems(availableServices);
            service.setPromptText("Выберите услугу");
            service.setMaxWidth(Double.MAX_VALUE);
            quantity.setEditable(true);
            quantity.setPrefWidth(104);
            amount.setPrefWidth(112);
            amount.setMinWidth(112);
            amount.getStyleClass().add("service-line-amount");
            FontIcon trashIcon = new FontIcon(MaterialDesignD.DELETE_OUTLINE);
            trashIcon.setIconSize(18);
            remove.setGraphic(trashIcon);
            remove.setTooltip(new Tooltip("Убрать услугу"));
            remove.setAccessibleText("Убрать услугу из заказа");
            remove.setPrefWidth(44);
            remove.setMinWidth(44);
            remove.getStyleClass().addAll("button-quiet", "service-line-remove");
            node.setAlignment(Pos.CENTER_LEFT);
            node.getStyleClass().add("service-line");
            HBox.setHgrow(service, Priority.ALWAYS);
            node.getChildren().addAll(service, quantity, amount, remove);
            service.valueProperty().addListener((observable, oldValue, value) -> refresh());
            quantity.valueProperty().addListener((observable, oldValue, value) -> refresh());
        }

        private void refresh() {
            amount.setText(service() == null ? "0 ₽" : money(Math.multiplyExact(service().price(), quantity())));
            onChanged.run();
        }

        private void clear() {
            service.getSelectionModel().clearSelection();
            quantity.getValueFactory().setValue(1);
            refresh();
        }

        private void setValue(ServiceOption initialService, int initialQuantity) {
            service.setValue(initialService);
            quantity.getValueFactory().setValue(Math.max(1, Math.min(99, initialQuantity)));
            refresh();
        }

        private HBox node() { return node; }
        private ComboBox<ServiceOption> serviceControl() { return service; }
        private Spinner<Integer> quantityControl() { return quantity; }
        private Button removeButton() { return remove; }
        private ServiceOption service() { return service.getValue(); }
        private int quantity() { return quantity.getValue() == null ? 0 : quantity.getValue(); }
    }

    private enum DataState { LOADING, READY, ERROR }

    private enum EmptyAction { NONE, CREATE, RESET, RETRY }

    private record CreateDraft(ClientOption client, ClientDraft newClient, List<OrderLineDraft> items) {}
    private record OrderLineDraft(ServiceOption service, int quantity) {}
    private record EditDraft(
            ClientOption client,
            ClientDraft newClient,
            OrderStatus status,
            List<OrderLineDraft> items) {}
    private record ClientDraft(String fullName, String phone, String email, String login, String password) {}
    private record ServiceDraft(String name, long price) {}
}
