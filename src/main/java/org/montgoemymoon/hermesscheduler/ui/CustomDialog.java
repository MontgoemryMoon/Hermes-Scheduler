package org.montgoemymoon.hermesscheduler.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * 通用自定义对话框（可嵌入任意内容）
 * 用于：添加任务、确认删除、确认暂停/恢复
 */
public class CustomDialog {

    private final Stage owner;
    private final String title;
    private final Node content;           // 对话框主体内容（可以是 GridPane、Label 等）
    private final String confirmText;
    private final Runnable onConfirm;
    private final boolean showCancel;     // 是否显示取消按钮

    public CustomDialog(Stage owner, String title, Node content,
                        String confirmText, Runnable onConfirm) {
        this(owner, title, content, confirmText, onConfirm, true);
    }

    public CustomDialog(Stage owner, String title, Node content,
                        String confirmText, Runnable onConfirm, boolean showCancel) {
        this.owner = owner;
        this.title = title;
        this.content = content;
        this.confirmText = confirmText;
        this.onConfirm = onConfirm;
        this.showCancel = showCancel;
    }

    public void show() {
        Stage dialogStage = new Stage();
        dialogStage.initStyle(StageStyle.TRANSPARENT);
        dialogStage.initModality(Modality.WINDOW_MODAL);
        dialogStage.initOwner(owner);

        // ---- 标题栏 ----
        HBox titleBar = new HBox();
        titleBar.setId("dialog-title-bar");
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setPadding(new Insets(10, 15, 10, 15));
        titleBar.setSpacing(10);

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeBtn = new Button("✕");
        closeBtn.setStyle("-fx-font-size: 18px; -fx-cursor: hand;");
        closeBtn.setTooltip(new Tooltip("关闭"));
        closeBtn.setOnAction(e -> dialogStage.close());

        titleBar.getChildren().addAll(titleLabel, spacer, closeBtn);

        // ---- 内容区域（灵活嵌入） ----
        // 如果内容有内边距，我们不再额外包裹，直接使用 content 的样式
        // 但为了统一背景，将 content 放入 VBox 并设置对齐
        VBox contentBox = new VBox(content);
        contentBox.setAlignment(Pos.TOP_LEFT);
        contentBox.setPadding(new Insets(20, 30, 15, 30));
        VBox.setVgrow(contentBox, Priority.ALWAYS);

        // ---- 按钮区 ----
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(0, 30, 20, 30));

        Button confirmBtn = new Button(confirmText);
        confirmBtn.getStyleClass().add("button-primary");
        if (showCancel) {
            Button cancelBtn = new Button("取消");
            cancelBtn.setOnAction(e -> dialogStage.close());
            buttonBox.getChildren().add(cancelBtn);
        }
        buttonBox.getChildren().add(confirmBtn);

        // ---- 主布局 ----
        VBox dialogLayout = new VBox(titleBar, contentBox, buttonBox);
        dialogLayout.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 20, 0, 0, 5);");

        // 自适应尺寸：如果 content 有固定大小，窗口会根据内容调整
        StackPane rootDialog = new StackPane(dialogLayout);
        rootDialog.setPadding(new Insets(10));
        rootDialog.setStyle("-fx-background-color: transparent;");

        Scene sceneDialog = new Scene(rootDialog);
        sceneDialog.setFill(null);
        try {
            sceneDialog.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        } catch (Exception ignored) {}

        // ---- 拖拽移动 ----
        final double[] dragOffset = {0, 0};
        titleBar.setOnMousePressed(event -> {
            dragOffset[0] = event.getSceneX();
            dragOffset[1] = event.getSceneY();
        });
        titleBar.setOnMouseDragged(event -> {
            if (!dialogStage.isFullScreen()) {
                dialogStage.setX(event.getScreenX() - dragOffset[0]);
                dialogStage.setY(event.getScreenY() - dragOffset[1]);
            }
        });

        // ---- 确认按钮逻辑 ----
        confirmBtn.setOnAction(e -> {
            if (onConfirm != null) {
                onConfirm.run();
            }
            dialogStage.close();
        });

        dialogStage.setScene(sceneDialog);
        dialogStage.sizeToScene(); // 自动调整大小
        dialogStage.showAndWait();
    }
}