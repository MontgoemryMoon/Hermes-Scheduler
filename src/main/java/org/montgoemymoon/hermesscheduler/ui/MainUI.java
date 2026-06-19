package org.montgoemymoon.hermesscheduler.ui;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.montgoemymoon.hermesscheduler.raft.RaftNode;
import org.montgoemymoon.hermesscheduler.task.Task;
import org.montgoemymoon.hermesscheduler.task.TaskDAO;
import org.montgoemymoon.hermesscheduler.task.TaskStatus;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class MainUI extends Application {

    private Stage stage;
    private RaftNode raftNode;
    private TableView<Task> taskTable;
    private Label roleLabel;
    private Label termLabel;
    private ListView<String> clusterView;
    private TaskDAO taskDAO = new TaskDAO();

    private ObservableList<Task> cachedTaskList = FXCollections.observableArrayList();

    private HBox titleBar;
    private StackPane rootPane;

    private double dragOffsetX;
    private double dragOffsetY;

    // ----- 标题栏滑动（全屏）AnimationTimer -----
    private AnimationTimer titleBarAnimTimer;
    private long titleBarAnimStartTime;
    private double titleBarStartTranslateY;
    private double titleBarTargetTranslateY;
    private boolean titleBarAnimRunning = false;

    // ----- 窗口最大化/还原 AnimationTimer -----
    private AnimationTimer maximizeAnimTimer;
    private long maximizeAnimStartTime;
    private double startX, startY, startWidth, startHeight;
    private double targetX, targetY, targetWidth, targetHeight;
    private boolean maximizeAnimRunning = false;
    private Runnable maximizeOnFinish;

    // ----- 定时刷新 UI -----
    private javafx.animation.Timeline refreshTimeline;

    // ----- 状态 -----
    private boolean isTitleBarVisible = false;
    private double titleBarHeight = 50;

    private boolean isCustomMaximized = false;
    private double normalX, normalY, normalWidth, normalHeight;

    // 六次缓出
    private static double easeOutPower6(double t) {
        return 1 - Math.pow(1 - t, 6);
    }

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        primaryStage.initStyle(StageStyle.TRANSPARENT);

        String nodeId = "node1";
        int listenPort = 8888;
        List<String> peers = List.of("node2:8888", "node3:8888");
        raftNode = new RaftNode(nodeId, peers, listenPort);

        titleBar = createTitleBar(primaryStage, nodeId);
        BorderPane mainContent = buildMainContent();

        VBox mainLayout = new VBox(titleBar, mainContent);
        mainLayout.setStyle("-fx-background-color: #f5f7fa; -fx-background-radius: 10 10 10 10;");
        VBox.setVgrow(mainContent, Priority.ALWAYS);

        rootPane = new StackPane(mainLayout);
        rootPane.setPadding(new Insets(10));
        rootPane.setStyle(
                "-fx-background-color: transparent; " +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 20, 0, 0, 0);"
        );

        Scene scene = new Scene(rootPane, 1280, 720);
        scene.setFill(null);
        try {
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        } catch (Exception ignored) {}

        // ESC 退出全屏
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE && primaryStage.isFullScreen()) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "是否退出全屏模式？", ButtonType.YES, ButtonType.NO);
                Optional<ButtonType> result = alert.showAndWait();
                if (result.isPresent() && result.get() == ButtonType.YES) {
                    primaryStage.setFullScreen(false);
                }
            }
        });

        // 全屏鼠标移到顶部显示标题栏
        scene.setOnMouseMoved(event -> {
            if (primaryStage.isFullScreen()) {
                double y = event.getY();
                if (y < 30) {
                    showTitleBar();
                } else {
                    scheduleHideTitleBar();
                }
            }
        });

        Platform.setImplicitExit(false);
        primaryStage.setOnCloseRequest(event -> {
            event.consume();
            primaryStage.hide();
        });

        // 全屏属性监听
        primaryStage.fullScreenProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                titleBar.setTranslateY(-titleBarHeight);
                isTitleBarVisible = false;
                if (titleBarAnimTimer != null) titleBarAnimTimer.stop();
                rootPane.setPadding(Insets.EMPTY);
                if (isCustomMaximized) {
                    restoreFromMaximized(true);
                }
            } else {
                titleBar.setTranslateY(0);
                isTitleBarVisible = true;
                if (titleBarAnimTimer != null) titleBarAnimTimer.stop();
                rootPane.setPadding(new Insets(10));
            }
        });

        primaryStage.setScene(scene);
        primaryStage.show();

        titleBarHeight = titleBar.getHeight() > 0 ? titleBar.getHeight() : 50;
        titleBar.setTranslateY(0);

        enableResize(primaryStage, rootPane, 600, 400);

        // 定时刷新 UI（保留 Timeline 作为定时器）
        refreshTimeline = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(Duration.seconds(2), e -> {
                    roleLabel.setText("Role: " + raftNode.getRole());
                    termLabel.setText("Term: " + raftNode.getCurrentTerm());
                    refreshClusterView();
                })
        );
        refreshTimeline.setCycleCount(javafx.animation.Timeline.INDEFINITE);
        refreshTimeline.play();

        // 首次加载任务表（异步）
        refreshTaskTable();
    }

    // ---------- 标题栏 ----------
    private HBox createTitleBar(Stage stage, String nodeId) {
        HBox bar = new HBox();
        bar.setId("title-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 15, 10, 15));
        bar.setSpacing(10);

        Label titleLabel = new Label("⚡ 分布式任务调度系统 - " + nodeId);
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");

        roleLabel = new Label("Role: " + raftNode.getRole());
        termLabel = new Label("Term: " + raftNode.getCurrentTerm());
        roleLabel.setStyle("-fx-font-size: 13px;");
        termLabel.setStyle("-fx-font-size: 13px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        ImageView maxIcon = new ImageView(new Image("image/max-button.png"));
        maxIcon.setFitWidth(16);
        maxIcon.setFitHeight(16);
        maxIcon.setPreserveRatio(true);
        Button maxBtn = new Button();
        maxBtn.setGraphic(maxIcon);
        maxBtn.setStyle("-fx-font-size: 18px; -fx-cursor: hand;");
        maxBtn.setTooltip(new Tooltip("最大化/还原窗口"));
        maxBtn.setOnAction(e -> toggleCustomMaximize());

        ImageView minIcon = new ImageView(new Image("image/min-button.png"));
        minIcon.setFitWidth(32);
        minIcon.setFitHeight(32);
        minIcon.setPreserveRatio(true);
        Button minBtn = new Button();
        minBtn.setGraphic(minIcon);
        minBtn.setStyle("-fx-font-size: 18px; -fx-cursor: hand;");
        minBtn.setTooltip(new Tooltip("最小化"));
        minBtn.setOnAction(e -> stage.setIconified(true));

        Button closeBtn = new Button("✕");
        closeBtn.setStyle("-fx-font-size: 18px; -fx-cursor: hand;");
        closeBtn.setTooltip(new Tooltip("关闭窗口（后台继续运行）"));
        closeBtn.setOnAction(e -> stage.hide());

        bar.getChildren().addAll(titleLabel, roleLabel, termLabel, spacer, minBtn, maxBtn, closeBtn);

        bar.setOnMousePressed(event -> {
            if (event.getClickCount() == 2) {
                toggleCustomMaximize();
            } else {
                dragOffsetX = event.getSceneX();
                dragOffsetY = event.getSceneY();
            }
        });
        bar.setOnMouseDragged(event -> {
            if (!stage.isFullScreen() && !isCustomMaximized) {
                stage.setX(event.getScreenX() - dragOffsetX);
                stage.setY(event.getScreenY() - dragOffsetY);
            }
        });

        return bar;
    }

    // ---------- 最大化/还原 ----------
    private void toggleCustomMaximize() {
        if (stage.isFullScreen()) return;
        if (maximizeAnimTimer != null) {
            maximizeAnimTimer.stop();
            maximizeAnimRunning = false;
        }
        if (!isCustomMaximized) {
            normalX = stage.getX();
            normalY = stage.getY();
            normalWidth = stage.getWidth();
            normalHeight = stage.getHeight();

            Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
            targetX = visualBounds.getMinX();
            targetY = visualBounds.getMinY();
            targetWidth = visualBounds.getWidth();
            targetHeight = visualBounds.getHeight();

            startX = normalX;
            startY = normalY;
            startWidth = normalWidth;
            startHeight = normalHeight;

            maximizeOnFinish = () -> {
                isCustomMaximized = true;
                rootPane.setPadding(Insets.EMPTY);
            };
            startMaximizeAnimation();
        } else {
            restoreFromMaximized(false);
        }
    }

    private void restoreFromMaximized(boolean instant) {
        if (!isCustomMaximized) return;
        if (maximizeAnimTimer != null) {
            maximizeAnimTimer.stop();
            maximizeAnimRunning = false;
        }
        if (instant) {
            stage.setX(normalX);
            stage.setY(normalY);
            stage.setWidth(normalWidth);
            stage.setHeight(normalHeight);
            rootPane.setPadding(new Insets(10));
            isCustomMaximized = false;
        } else {
            startX = stage.getX();
            startY = stage.getY();
            startWidth = stage.getWidth();
            startHeight = stage.getHeight();
            targetX = normalX;
            targetY = normalY;
            targetWidth = normalWidth;
            targetHeight = normalHeight;

            maximizeOnFinish = () -> {
                rootPane.setPadding(new Insets(10));
                isCustomMaximized = false;
            };
            startMaximizeAnimation();
        }
    }

    private void startMaximizeAnimation() {
        if (refreshTimeline != null) refreshTimeline.pause();
        maximizeAnimRunning = true;
        maximizeAnimStartTime = System.nanoTime();
        final long duration = 250_000_000L;

        if (maximizeAnimTimer == null) {
            maximizeAnimTimer = new AnimationTimer() {
                @Override
                public void handle(long now) {
                    if (!maximizeAnimRunning) {
                        this.stop();
                        return;
                    }
                    long elapsed = now - maximizeAnimStartTime;
                    double progress = Math.min(1.0, (double) elapsed / duration);
                    double eased = easeOutPower6(progress);

                    stage.setX(startX + (targetX - startX) * eased);
                    stage.setY(startY + (targetY - startY) * eased);
                    stage.setWidth(startWidth + (targetWidth - startWidth) * eased);
                    stage.setHeight(startHeight + (targetHeight - startHeight) * eased);

                    if (progress >= 1.0) {
                        maximizeAnimRunning = false;
                        this.stop();
                        if (refreshTimeline != null) refreshTimeline.play();
                        if (maximizeOnFinish != null) {
                            maximizeOnFinish.run();
                            maximizeOnFinish = null;
                        }
                    }
                }
            };
        }
        maximizeAnimTimer.start();
    }

    // ---------- 标题栏滑动 ----------
    private void startTitleBarAnimation(double from, double to) {
        if (titleBarAnimTimer != null) {
            titleBarAnimTimer.stop();
            titleBarAnimRunning = false;
        }
        titleBarStartTranslateY = from;
        titleBarTargetTranslateY = to;
        titleBarAnimRunning = true;
        titleBarAnimStartTime = System.nanoTime();
        final long duration = 300_000_000L;

        if (titleBarAnimTimer == null) {
            titleBarAnimTimer = new AnimationTimer() {
                @Override
                public void handle(long now) {
                    if (!titleBarAnimRunning) {
                        this.stop();
                        return;
                    }
                    long elapsed = now - titleBarAnimStartTime;
                    double progress = Math.min(1.0, (double) elapsed / duration);
                    double eased = easeOutPower6(progress);

                    double currentY = titleBarStartTranslateY + (titleBarTargetTranslateY - titleBarStartTranslateY) * eased;
                    titleBar.setTranslateY(currentY);

                    if (progress >= 1.0) {
                        titleBarAnimRunning = false;
                        this.stop();
                        if (titleBarTargetTranslateY == 0) {
                            isTitleBarVisible = true;
                        } else {
                            isTitleBarVisible = false;
                        }
                    }
                }
            };
        }
        titleBarAnimTimer.start();
    }

    private void showTitleBar() {
        if (!stage.isFullScreen()) return;
        if (titleBarAnimTimer != null) titleBarAnimTimer.stop();
        if (!isTitleBarVisible) {
            double from = titleBar.getTranslateY();
            double to = 0;
            startTitleBarAnimation(from, to);
        }
    }

    private void scheduleHideTitleBar() {
        if (!stage.isFullScreen()) return;
        if (titleBarAnimTimer != null) titleBarAnimTimer.stop();
        javafx.animation.Timeline delay = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(Duration.seconds(1), e -> {
                    if (isTitleBarVisible && stage.isFullScreen()) {
                        double from = titleBar.getTranslateY();
                        double to = -titleBarHeight;
                        startTitleBarAnimation(from, to);
                    }
                })
        );
        delay.setCycleCount(1);
        delay.play();
    }

    // ---------- 构建主内容 ----------
    private BorderPane buildMainContent() {
        BorderPane content = new BorderPane();
        content.getStyleClass().add("main-content");

        taskTable = new TableView<>();
        taskTable.setItems(cachedTaskList);

        TableColumn<Task, String> nameCol = new TableColumn<>("任务名");
        nameCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getName()));
        TableColumn<Task, String> cronCol = new TableColumn<>("Cron表达式");
        cronCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getCronExpression()));
        TableColumn<Task, String> statusCol = new TableColumn<>("状态");
        statusCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getStatus().name()));
        TableColumn<Task, String> nextCol = new TableColumn<>("下次执行时间");
        nextCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(
                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(cellData.getValue().getNextExecuteTime()))
        ));
        taskTable.getColumns().addAll(nameCol, cronCol, statusCol, nextCol);
        taskTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        clusterView = new ListView<>();
        clusterView.setPrefWidth(200);
        refreshClusterView();

        HBox buttons = new HBox(10);
        buttons.setPadding(new Insets(10));
        buttons.setAlignment(Pos.CENTER);

        Button addBtn = new Button("➕ 添加任务");
        addBtn.setOnAction(e -> showCustomAddTaskDialog());

        Button deleteBtn = new Button("🗑️ 删除任务");
        deleteBtn.setOnAction(e -> deleteSelectedTask());
        deleteBtn.getStyleClass().add("button-danger");

        Button pauseBtn = new Button("⏯️ 暂停/恢复");
        pauseBtn.setOnAction(e -> togglePauseTask());
        pauseBtn.getStyleClass().add("button-success");

        buttons.getChildren().addAll(addBtn, deleteBtn, pauseBtn);

        content.setCenter(taskTable);
        content.setRight(clusterView);
        content.setBottom(buttons);

        return content;
    }

    // ---------- 自定义添加任务窗口 ----------
    private void showCustomAddTaskDialog() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(0)); // 内边距由 CustomDialog 统一控制

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setMinWidth(100);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(col1, col2);

        TextField nameField = new TextField();
        TextField cronField = new TextField();
        TextField delayField = new TextField();
        TextField classField = new TextField();

        grid.add(new Label("任务名:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Cron表达式:"), 0, 1);
        grid.add(cronField, 1, 1);
        grid.add(new Label("延迟秒数(可选):"), 0, 2);
        grid.add(delayField, 1, 2);
        grid.add(new Label("任务类全限定名:"), 0, 3);
        grid.add(classField, 1, 3);

        // 使用 CustomDialog
        CustomDialog dialog = new CustomDialog(
                stage,
                "➕ 添加定时任务",
                grid,
                "添加",
                () -> {
                    // 验证和插入逻辑
                    String name = nameField.getText().trim();
                    if (name.isEmpty()) {
                        showError("任务名不能为空");
                        return;
                    }
                    if (name.length() > 200) {
                        showError("任务名长度不能超过200个字符（当前长度：" + name.length() + "）");
                        return;
                    }
                    String className = classField.getText().trim();
                    if (className.isEmpty()) {
                        showError("任务类全限定名不能为空");
                        return;
                    }
                    String cron = cronField.getText().trim();
                    String delayStr = delayField.getText().trim();
                    if (cron.isEmpty() && delayStr.isEmpty()) {
                        showError("请至少填写Cron表达式或延迟秒数");
                        return;
                    }

                    Task task = new Task();
                    task.setName(name);
                    task.setCronExpression(cron.isEmpty() ? null : cron);
                    task.setTaskClass(className);

                    if (!delayStr.isEmpty()) {
                        try {
                            int delay = Integer.parseInt(delayStr);
                            if (delay < 0) {
                                showError("延迟秒数不能为负数");
                                return;
                            }
                            task.setDelaySeconds(delay);
                        } catch (NumberFormatException ex) {
                            showError("延迟秒数请输入有效的整数");
                            return;
                        }
                    }
                    task.setStatus(TaskStatus.RUNNING);
                    task.setNextExecuteTime(System.currentTimeMillis() + 10000);

                    taskDAO.insertTask(task);
                    refreshTaskTable();
                }
        );
        dialog.show();
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        alert.showAndWait();
    }

    // ---------- 窗口调整大小 ----------
    private void enableResize(Stage stage, StackPane root, double minWidth, double minHeight) {
        final double RESIZE_MARGIN = 10;

        root.setOnMouseMoved(event -> {
            if (stage.isFullScreen() || isCustomMaximized) {
                root.setCursor(Cursor.DEFAULT);
                return;
            }
            double x = event.getX();
            double y = event.getY();
            double width = root.getWidth();
            double height = root.getHeight();

            boolean left = x < RESIZE_MARGIN;
            boolean right = x > width - RESIZE_MARGIN;
            boolean top = y < RESIZE_MARGIN;
            boolean bottom = y > height - RESIZE_MARGIN;

            if (top && left) root.setCursor(Cursor.NW_RESIZE);
            else if (top && right) root.setCursor(Cursor.NE_RESIZE);
            else if (bottom && left) root.setCursor(Cursor.SW_RESIZE);
            else if (bottom && right) root.setCursor(Cursor.SE_RESIZE);
            else if (top) root.setCursor(Cursor.N_RESIZE);
            else if (bottom) root.setCursor(Cursor.S_RESIZE);
            else if (left) root.setCursor(Cursor.W_RESIZE);
            else if (right) root.setCursor(Cursor.E_RESIZE);
            else root.setCursor(Cursor.DEFAULT);
        });

        root.setOnMousePressed(event -> {
            if (stage.isFullScreen() || isCustomMaximized) return;
            double x = event.getX();
            double y = event.getY();
            double width = root.getWidth();
            double height = root.getHeight();

            boolean left = x < RESIZE_MARGIN;
            boolean right = x > width - RESIZE_MARGIN;
            boolean top = y < RESIZE_MARGIN;
            boolean bottom = y > height - RESIZE_MARGIN;

            if (left || right || top || bottom) {
                root.setUserData(new double[]{
                        stage.getX(), stage.getY(),
                        stage.getWidth(), stage.getHeight(),
                        event.getScreenX(), event.getScreenY(),
                        left ? 1 : 0, right ? 1 : 0, top ? 1 : 0, bottom ? 1 : 0
                });
                root.setOnMouseDragged(dragEvent -> {
                    if (stage.isFullScreen() || isCustomMaximized) return;
                    double[] data = (double[]) root.getUserData();
                    double startX = data[0];
                    double startY = data[1];
                    double startWidth = data[2];
                    double startHeight = data[3];
                    double startScreenX = data[4];
                    double startScreenY = data[5];
                    boolean resizeLeft = data[6] == 1;
                    boolean resizeRight = data[7] == 1;
                    boolean resizeTop = data[8] == 1;
                    boolean resizeBottom = data[9] == 1;

                    double deltaX = dragEvent.getScreenX() - startScreenX;
                    double deltaY = dragEvent.getScreenY() - startScreenY;

                    double newX = startX;
                    double newY = startY;
                    double newWidth = startWidth;
                    double newHeight = startHeight;

                    if (resizeLeft) {
                        newX = startX + deltaX;
                        newWidth = startWidth - deltaX;
                    } else if (resizeRight) {
                        newWidth = startWidth + deltaX;
                    }
                    if (resizeTop) {
                        newY = startY + deltaY;
                        newHeight = startHeight - deltaY;
                    } else if (resizeBottom) {
                        newHeight = startHeight + deltaY;
                    }

                    if (newWidth < minWidth) {
                        if (resizeLeft) newX = startX + startWidth - minWidth;
                        newWidth = minWidth;
                    }
                    if (newHeight < minHeight) {
                        if (resizeTop) newY = startY + startHeight - minHeight;
                        newHeight = minHeight;
                    }

                    stage.setX(newX);
                    stage.setY(newY);
                    stage.setWidth(newWidth);
                    stage.setHeight(newHeight);
                });
            }
        });

        root.setOnMouseReleased(event -> {
            root.setOnMouseDragged(null);
            root.setUserData(null);
        });
    }

    // ---------- 异步刷新任务表 ----------
    private void refreshTaskTable() {
        javafx.concurrent.Task<List<Task>> queryTask = new javafx.concurrent.Task<>() {
            @Override
            protected List<Task> call() throws Exception {
                return taskDAO.getAllTasks();
            }
        };
        queryTask.setOnSucceeded(event -> {
            List<Task> tasks = queryTask.getValue();
            Platform.runLater(() -> cachedTaskList.setAll(tasks));
        });
        queryTask.setOnFailed(event -> {
            System.err.println("刷新任务表失败: " + queryTask.getException().getMessage());
        });
        new Thread(queryTask).start();
    }

    // ---------- 集群视图刷新 ----------
    private void refreshClusterView() {
        clusterView.getItems().clear();
        clusterView.getItems().add("本节点: " + raftNode.getRole() + ", Term=" + raftNode.getCurrentTerm());
        for (String peer : raftNode.getPeers()) {
            clusterView.getItems().add("节点: " + peer + " (状态未知)");
        }
    }

    // ---------- 业务操作 ----------
    private void deleteSelectedTask() {
        Task selected = taskTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "请先选择要删除的任务", ButtonType.OK);
            alert.showAndWait();
            return;
        }
        Label msgLabel = new Label("确定要删除任务 \"" + selected.getName() + "\" 吗？");
        msgLabel.setWrapText(true);
        msgLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #1e2a36;");

        CustomDialog dialog = new CustomDialog(
                stage,
                "删除任务",
                msgLabel,
                "删除",
                () -> {
                    taskDAO.deleteTask(selected.getId());
                    refreshTaskTable();
                }
        );
        dialog.show();
    }

    private void togglePauseTask() {
        Task selected = taskTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "请先选择一个任务", ButtonType.OK);
            alert.showAndWait();
            return;
        }
        boolean isRunning = selected.getStatus() == TaskStatus.RUNNING;
        String action = isRunning ? "暂停" : "恢复";
        Label msgLabel = new Label("确定要" + action + "任务 \"" + selected.getName() + "\" 吗？");
        msgLabel.setWrapText(true);
        msgLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #1e2a36;");

        CustomDialog dialog = new CustomDialog(
                stage,
                action + "任务",
                msgLabel,
                action,
                () -> {
                    TaskStatus newStatus = isRunning ? TaskStatus.PAUSED : TaskStatus.RUNNING;
                    taskDAO.updateTaskStatus(selected.getId(), newStatus);
                    refreshTaskTable();
                }
        );
        dialog.show();
    }

    public static void main(String[] args) {
        System.setProperty("prism.order", "d3d,es2,sw");
        System.setProperty("prism.forceGPU", "true");
        launch(args);
    }
}