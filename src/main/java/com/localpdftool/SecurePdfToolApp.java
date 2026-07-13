package com.localpdftool;

import javafx.application.Application;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.util.Matrix;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class SecurePdfToolApp extends Application {

    private Stage stage;
    private final TextArea log = new TextArea();

    // Visual editor state
    private File currentPdf;
    private PDDocument previewDocument;
    private PDFRenderer renderer;
    private int currentPageIndex = 0;
    private int renderedPixelWidth = 1;
    private int renderedPixelHeight = 1;
    private static final float RENDER_DPI = 144f;

    private final ImageView imageView = new ImageView();
    private final Pane overlayPane = new Pane();
    private final StackPane pageStack = new StackPane();
    private final Label pageLabel = new Label("No PDF loaded");
    private final TextField textToAdd = new TextField("Approved");
    private final Spinner<Integer> fontSizeSpinner = new Spinner<>(8, 96, 14);
    private final ChoiceBox<String> fontChoice = new ChoiceBox<>();
    private final Slider zoomSlider = new Slider(0.5, 2.0, 1.0);
    private final List<TextMark> textMarks = new ArrayList<>();
    private final List<WhiteoutMark> whiteoutMarks = new ArrayList<>();
    private final List<Object> editHistory = new ArrayList<>();
    private Object selectedMark;
    private EditMode editMode = EditMode.NONE;
    private Button addTextModeBtn;
    private Button whiteoutModeBtn;
    private double whiteoutStartX;
    private double whiteoutStartY;
    private Rectangle whiteoutDraftRect;

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        primaryStage.setTitle("Secure Local PDF Tool - v1.5 Visual Text + Whiteout + Arial/Calibri + Save");

        TabPane tabs = new TabPane();
        tabs.getTabs().add(new Tab("Visual Edit", buildVisualEditor()));
        tabs.getTabs().add(new Tab("Tools", buildUtilityTools()));
        tabs.getTabs().forEach(t -> t.setClosable(false));

        primaryStage.setScene(new Scene(tabs, 1100, 720));
        primaryStage.show();
        append("Ready. Open a PDF in Visual Edit tab. Use Add Text for new text, or Blank/Hide Area to cover existing PDF text with a white rectangle.");
    }

    private BorderPane buildVisualEditor() {
        Button openBtn = new Button("Open PDF");
        Button prevBtn = new Button("< Prev");
        Button nextBtn = new Button("Next >");
        addTextModeBtn = new Button("Add Text");
        whiteoutModeBtn = new Button("Blank/Hide Area");
        Button undoBtn = new Button("Undo Last Edit");
        Button deleteBtn = new Button("Delete Selected");
        Button saveBtn = new Button("Save");
        Button saveAsBtn = new Button("Save As PDF");

        textToAdd.setPrefWidth(220);
        fontSizeSpinner.setEditable(true);
        fontChoice.getItems().addAll("Arial", "Arial Bold", "Calibri", "Calibri Bold", "Helvetica", "Helvetica Bold", "Helvetica Oblique", "Times Roman", "Times Bold", "Courier", "Courier Bold");
        fontChoice.setValue("Arial");
        fontChoice.setPrefWidth(135);
        zoomSlider.setShowTickLabels(true);
        zoomSlider.setShowTickMarks(true);
        zoomSlider.setMajorTickUnit(0.5);
        zoomSlider.setBlockIncrement(0.1);
        zoomSlider.setPrefWidth(160);

        openBtn.setOnAction(e -> openPdfForVisualEditing());
        prevBtn.setOnAction(e -> changePage(-1));
        nextBtn.setOnAction(e -> changePage(1));
        addTextModeBtn.setOnAction(e -> enableAddTextMode());
        whiteoutModeBtn.setOnAction(e -> enableWhiteoutMode());
        undoBtn.setOnAction(e -> undoLastEdit());
        deleteBtn.setOnAction(e -> deleteSelectedText());
        saveBtn.setOnAction(e -> saveVisualEditsToCurrentPdf());
        saveAsBtn.setOnAction(e -> saveVisualEditsAs());
        zoomSlider.valueProperty().addListener((obs, oldVal, newVal) -> applyZoomAndOverlay());

        HBox toolbar = new HBox(8,
                openBtn,
                new Separator(), prevBtn, nextBtn, pageLabel,
                new Separator(), new Label("Text:"), textToAdd,
                new Label("Font:"), fontChoice, new Label("Size:"), fontSizeSpinner, addTextModeBtn, whiteoutModeBtn,
                new Label("Zoom:"), zoomSlider,
                undoBtn, deleteBtn, saveBtn, saveAsBtn
        );
        toolbar.setPadding(new Insets(8));
        toolbar.setAlignment(Pos.CENTER_LEFT);

        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setCursor(Cursor.CROSSHAIR);

        overlayPane.setMouseTransparent(false);
        overlayPane.setPickOnBounds(true);
        overlayPane.setStyle("-fx-background-color: transparent;");
        overlayPane.addEventHandler(MouseEvent.MOUSE_CLICKED, this::addTextAtClickedPosition);
        overlayPane.addEventHandler(MouseEvent.MOUSE_PRESSED, this::startWhiteoutRectangle);
        overlayPane.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::dragWhiteoutRectangle);
        overlayPane.addEventHandler(MouseEvent.MOUSE_RELEASED, this::finishWhiteoutRectangle);
        pageStack.getChildren().addAll(imageView, overlayPane);
        StackPane.setAlignment(imageView, Pos.TOP_LEFT);
        StackPane.setAlignment(overlayPane, Pos.TOP_LEFT);

        ScrollPane scrollPane = new ScrollPane(pageStack);
        scrollPane.setFitToWidth(false);
        scrollPane.setFitToHeight(false);
        scrollPane.setPannable(true);

        Label help = new Label("How to use: Add Text = place new text. Blank/Hide Area = drag a white box over existing PDF text. You can select/move/delete added text and whiteout boxes. Save overwrites the opened PDF; Save As PDF creates a copy.");
        help.setPadding(new Insets(6));

        BorderPane root = new BorderPane();
        root.setTop(new VBox(toolbar, help));
        root.setCenter(scrollPane);
        return root;
    }

    private BorderPane buildUtilityTools() {
        Label title = new Label("Secure Local PDF Utility Tools");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        Label subtitle = new Label("Offline operations: merge, split, extract, rotate, watermark, password protect, PDF info.");

        Button mergeBtn = new Button("Merge PDFs");
        Button splitBtn = new Button("Split PDF into Pages");
        Button extractBtn = new Button("Extract Page Range");
        Button rotateBtn = new Button("Rotate Pages");
        Button stampBtn = new Button("Add Center Watermark");
        Button protectBtn = new Button("Password Protect");
        Button infoBtn = new Button("PDF Info");
        Button clearBtn = new Button("Clear Log");

        List<Button> buttons = List.of(mergeBtn, splitBtn, extractBtn, rotateBtn, stampBtn, protectBtn, infoBtn, clearBtn);
        buttons.forEach(b -> b.setMaxWidth(Double.MAX_VALUE));

        mergeBtn.setOnAction(e -> mergePdfs());
        splitBtn.setOnAction(e -> splitPdf());
        extractBtn.setOnAction(e -> extractRange());
        rotateBtn.setOnAction(e -> rotatePdf());
        stampBtn.setOnAction(e -> addCenterWatermark());
        protectBtn.setOnAction(e -> passwordProtect());
        infoBtn.setOnAction(e -> showPdfInfo());
        clearBtn.setOnAction(e -> log.clear());

        VBox left = new VBox(10, title, subtitle, mergeBtn, splitBtn, extractBtn, rotateBtn, stampBtn, protectBtn, infoBtn, clearBtn);
        left.setPadding(new Insets(10));
        left.setPrefWidth(270);

        log.setEditable(false);
        log.setWrapText(true);

        BorderPane root = new BorderPane();
        root.setLeft(left);
        root.setCenter(log);
        BorderPane.setMargin(log, new Insets(10));
        return root;
    }

    private void openPdfForVisualEditing() {
        try {
            File input = openSinglePdf("Open PDF for visual editing");
            if (input == null) return;
            loadPdfForVisualEditing(input, true);
            append("Opened for visual editing: " + input.getAbsolutePath());
        } catch (Exception ex) {
            error("Open PDF failed", ex);
        }
    }

    private void loadPdfForVisualEditing(File input, boolean clearEdits) throws IOException {
        closePreviewDocument();
        currentPdf = input;
        previewDocument = Loader.loadPDF(input);
        renderer = new PDFRenderer(previewDocument);
        currentPageIndex = Math.max(0, Math.min(currentPageIndex, previewDocument.getNumberOfPages() - 1));
        if (clearEdits) {
            textMarks.clear();
            whiteoutMarks.clear();
            editHistory.clear();
            selectedMark = null;
            editMode = EditMode.NONE;
        }
        refreshModeButtonStyles();
        renderCurrentPage();
    }

    private void changePage(int delta) {
        if (previewDocument == null) return;
        int next = currentPageIndex + delta;
        if (next < 0 || next >= previewDocument.getNumberOfPages()) return;
        currentPageIndex = next;
        try {
            renderCurrentPage();
        } catch (Exception ex) {
            error("Page render failed", ex);
        }
    }

    private void renderCurrentPage() throws IOException {
        if (previewDocument == null) return;
        BufferedImage bufferedImage = renderer.renderImageWithDPI(currentPageIndex, RENDER_DPI, ImageType.RGB);
        renderedPixelWidth = bufferedImage.getWidth();
        renderedPixelHeight = bufferedImage.getHeight();
        Image fxImage = SwingFXUtils.toFXImage(bufferedImage, null);
        imageView.setImage(fxImage);
        pageLabel.setText("Page " + (currentPageIndex + 1) + " / " + previewDocument.getNumberOfPages());
        applyZoomAndOverlay();
    }

    private void applyZoomAndOverlay() {
        double zoom = zoomSlider.getValue();
        if (imageView.getImage() != null) {
            imageView.setFitWidth(renderedPixelWidth * zoom);
            imageView.setFitHeight(renderedPixelHeight * zoom);
            overlayPane.setPrefSize(renderedPixelWidth * zoom, renderedPixelHeight * zoom);
            overlayPane.setMinSize(renderedPixelWidth * zoom, renderedPixelHeight * zoom);
            overlayPane.setMaxSize(renderedPixelWidth * zoom, renderedPixelHeight * zoom);
        }
        redrawOverlays();
    }


    private void enableAddTextMode() {
        if (previewDocument == null || imageView.getImage() == null) {
            alert("Open PDF first", "Open a PDF before adding text.");
            return;
        }
        String text = textToAdd.getText();
        if (text == null || text.isBlank()) {
            alert("Enter text first", "Please type the text you want to add.");
            return;
        }
        editMode = EditMode.ADD_TEXT;
        selectedMark = null;
        refreshModeButtonStyles();
        redrawOverlays();
        append("Add Text mode enabled. Click once on the PDF page to place: \"" + text.trim() + "\"");
    }

    private void enableWhiteoutMode() {
        if (previewDocument == null || imageView.getImage() == null) {
            alert("Open PDF first", "Open a PDF before blanking/hiding an area.");
            return;
        }
        editMode = EditMode.WHITEOUT;
        selectedMark = null;
        refreshModeButtonStyles();
        redrawOverlays();
        append("Blank/Hide Area mode enabled. Drag a rectangle over existing PDF text to cover it with white.");
    }

    private void refreshModeButtonStyles() {
        if (addTextModeBtn != null) {
            if (editMode == EditMode.ADD_TEXT) {
                addTextModeBtn.setText("Click Page to Place");
                addTextModeBtn.setStyle("-fx-background-color: #fff3cd; -fx-border-color: #d39e00; -fx-font-weight: bold;");
            } else {
                addTextModeBtn.setText("Add Text");
                addTextModeBtn.setStyle("");
            }
        }
        if (whiteoutModeBtn != null) {
            if (editMode == EditMode.WHITEOUT) {
                whiteoutModeBtn.setText("Drag White Box");
                whiteoutModeBtn.setStyle("-fx-background-color: #fff3cd; -fx-border-color: #d39e00; -fx-font-weight: bold;");
            } else {
                whiteoutModeBtn.setText("Blank/Hide Area");
                whiteoutModeBtn.setStyle("");
            }
        }
        if (editMode == EditMode.ADD_TEXT || editMode == EditMode.WHITEOUT) {
            imageView.setCursor(Cursor.CROSSHAIR);
            overlayPane.setCursor(Cursor.CROSSHAIR);
        } else {
            imageView.setCursor(Cursor.DEFAULT);
            overlayPane.setCursor(Cursor.DEFAULT);
        }
    }

    private void addTextAtClickedPosition(MouseEvent event) {
        try {
            if (previewDocument == null || imageView.getImage() == null) return;

            // New text is added only after pressing the Add Text button.
            // This prevents accidental duplicate text while selecting/dragging existing labels.
            if (editMode != EditMode.ADD_TEXT) return;
            if (event.getTarget() != overlayPane) return;

            String text = textToAdd.getText();
            if (text == null || text.isBlank()) {
                alert("Enter text first", "Please type the text you want to add.");
                return;
            }

            double zoom = zoomSlider.getValue();
            double clickX = event.getX();
            double clickY = event.getY();
            double imagePixelX = clickX / zoom;
            double imagePixelY = clickY / zoom;

            TextMark mark = createTextMarkFromImagePixel(imagePixelX, imagePixelY, text.trim(), fontSizeSpinner.getValue(), fontChoice.getValue());
            textMarks.add(mark);
            editHistory.add(mark);
            selectedMark = mark;
            editMode = EditMode.NONE;
            refreshModeButtonStyles();
            redrawOverlays();
            append("Added text marker on page " + (currentPageIndex + 1) + ": \"" + text.trim() + "\"");
        } catch (Exception ex) {
            error("Add text failed", ex);
        }
    }

    private TextMark createTextMarkFromImagePixel(double imagePixelX, double imagePixelY, String text, int fontSize, String fontName) {
        PDPage page = previewDocument.getPage(currentPageIndex);
        PDRectangle box = page.getMediaBox();
        float pdfX = (float) (imagePixelX * box.getWidth() / renderedPixelWidth);
        float pdfY = (float) (box.getHeight() - (imagePixelY * box.getHeight() / renderedPixelHeight));
        return new TextMark(currentPageIndex, pdfX, pdfY, text, fontSize, fontName);
    }

    private void moveTextMarkToOverlayPosition(TextMark mark, double overlayX, double overlayY) {
        double zoom = zoomSlider.getValue();
        double imagePixelX = Math.max(0, Math.min(renderedPixelWidth, overlayX / zoom));
        double imagePixelY = Math.max(0, Math.min(renderedPixelHeight, overlayY / zoom));
        PDRectangle box = previewDocument.getPage(currentPageIndex).getMediaBox();
        mark.x = (float) (imagePixelX * box.getWidth() / renderedPixelWidth);
        mark.y = (float) (box.getHeight() - (imagePixelY * box.getHeight() / renderedPixelHeight));
    }

    private void redrawOverlays() {
        overlayPane.getChildren().clear();
        if (previewDocument == null) return;
        double zoom = zoomSlider.getValue();
        PDRectangle box = previewDocument.getPage(currentPageIndex).getMediaBox();

        for (WhiteoutMark mark : whiteoutMarks) {
            if (mark.pageIndex != currentPageIndex) continue;
            double x = mark.x * renderedPixelWidth / box.getWidth() * zoom;
            double y = (box.getHeight() - mark.y - mark.height) * renderedPixelHeight / box.getHeight() * zoom;
            double w = mark.width * renderedPixelWidth / box.getWidth() * zoom;
            double h = mark.height * renderedPixelHeight / box.getHeight() * zoom;

            Rectangle rect = new Rectangle(w, h);
            rect.setFill(Color.WHITE);
            rect.setStroke(mark == selectedMark ? Color.DODGERBLUE : Color.GRAY);
            rect.setStrokeWidth(mark == selectedMark ? 2 : 1);
            rect.setCursor(Cursor.MOVE);
            rect.setLayoutX(x);
            rect.setLayoutY(y);

            DragContext dragContext = new DragContext();
            rect.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
                editMode = EditMode.NONE;
                refreshModeButtonStyles();
                selectedMark = mark;
                dragContext.startSceneX = event.getSceneX();
                dragContext.startSceneY = event.getSceneY();
                dragContext.startLayoutX = rect.getLayoutX();
                dragContext.startLayoutY = rect.getLayoutY();
                dragContext.dragged = false;
                rect.toFront();
                event.consume();
            });
            rect.addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> {
                double deltaX = event.getSceneX() - dragContext.startSceneX;
                double deltaY = event.getSceneY() - dragContext.startSceneY;
                if (Math.abs(deltaX) > 2 || Math.abs(deltaY) > 2) dragContext.dragged = true;
                rect.setLayoutX(clamp(dragContext.startLayoutX + deltaX, 0, Math.max(0, overlayPane.getWidth() - rect.getWidth())));
                rect.setLayoutY(clamp(dragContext.startLayoutY + deltaY, 0, Math.max(0, overlayPane.getHeight() - rect.getHeight())));
                event.consume();
            });
            rect.addEventHandler(MouseEvent.MOUSE_RELEASED, event -> {
                selectedMark = mark;
                moveWhiteoutMarkToOverlayPosition(mark, rect.getLayoutX(), rect.getLayoutY(), rect.getWidth(), rect.getHeight());
                redrawOverlays();
                event.consume();
            });
            rect.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
                selectedMark = mark;
                if (!dragContext.dragged) redrawOverlays();
                event.consume();
            });
            overlayPane.getChildren().add(rect);
        }

        for (TextMark mark : textMarks) {
            if (mark.pageIndex != currentPageIndex) continue;
            double pixelX = mark.x * renderedPixelWidth / box.getWidth();
            double pixelY = (box.getHeight() - mark.y) * renderedPixelHeight / box.getHeight();

            Label label = new Label(mark.text);
            label.setCursor(Cursor.MOVE);
            boolean selected = mark == selectedMark;
            label.setStyle("-fx-background-color: rgba(255,255,180,0.75); "
                    + "-fx-border-color: " + (selected ? "#0078D7" : "#777777") + "; "
                    + "-fx-border-width: " + (selected ? "2" : "1") + "; "
                    + "-fx-padding: 2; -fx-font-family: '" + fontCssName(mark.fontName) + "'; -fx-font-size: " + Math.max(9, mark.fontSize * zoom) + "px;");
            label.setLayoutX(pixelX * zoom);
            label.setLayoutY(pixelY * zoom - (mark.fontSize * zoom));

            DragContext dragContext = new DragContext();

            label.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
                editMode = EditMode.NONE;
                refreshModeButtonStyles();
                selectedMark = mark;
                dragContext.startSceneX = event.getSceneX();
                dragContext.startSceneY = event.getSceneY();
                dragContext.startLayoutX = label.getLayoutX();
                dragContext.startLayoutY = label.getLayoutY();
                dragContext.dragged = false;
                label.toFront();
                label.setStyle("-fx-background-color: rgba(255,255,180,0.75); "
                        + "-fx-border-color: #0078D7; -fx-border-width: 2; "
                        + "-fx-padding: 2; -fx-font-family: '" + fontCssName(mark.fontName) + "'; -fx-font-size: " + Math.max(9, mark.fontSize * zoom) + "px;");
                event.consume();
            });

            label.addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> {
                double deltaX = event.getSceneX() - dragContext.startSceneX;
                double deltaY = event.getSceneY() - dragContext.startSceneY;
                if (Math.abs(deltaX) > 2 || Math.abs(deltaY) > 2) dragContext.dragged = true;
                double newLayoutX = clamp(dragContext.startLayoutX + deltaX, 0, Math.max(0, overlayPane.getWidth() - label.getWidth()));
                double newLayoutY = clamp(dragContext.startLayoutY + deltaY, 0, Math.max(0, overlayPane.getHeight() - label.getHeight()));
                label.setLayoutX(newLayoutX);
                label.setLayoutY(newLayoutY);
                event.consume();
            });

            label.addEventHandler(MouseEvent.MOUSE_RELEASED, event -> {
                selectedMark = mark;
                moveTextMarkToOverlayPosition(mark, label.getLayoutX(), label.getLayoutY() + (mark.fontSize * zoom));
                redrawOverlays();
                event.consume();
            });

            label.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
                selectedMark = mark;
                if (!dragContext.dragged) redrawOverlays();
                event.consume();
            });
            overlayPane.getChildren().add(label);
        }
    }

    private String fontCssName(String name) {
        if (name == null) return "Arial";
        if (name.startsWith("Calibri")) return "Calibri";
        if (name.startsWith("Arial")) return "Arial";
        if (name.startsWith("Times")) return "Times New Roman";
        if (name.startsWith("Courier")) return "Courier New";
        return "Arial";
    }

    private void moveWhiteoutMarkToOverlayPosition(WhiteoutMark mark, double overlayX, double overlayY, double overlayW, double overlayH) {
        double zoom = zoomSlider.getValue();
        double imagePixelX = Math.max(0, Math.min(renderedPixelWidth, overlayX / zoom));
        double imagePixelY = Math.max(0, Math.min(renderedPixelHeight, overlayY / zoom));
        double imagePixelW = Math.max(1, overlayW / zoom);
        double imagePixelH = Math.max(1, overlayH / zoom);
        PDRectangle box = previewDocument.getPage(currentPageIndex).getMediaBox();
        mark.x = (float) (imagePixelX * box.getWidth() / renderedPixelWidth);
        mark.y = (float) (box.getHeight() - ((imagePixelY + imagePixelH) * box.getHeight() / renderedPixelHeight));
        mark.width = (float) (imagePixelW * box.getWidth() / renderedPixelWidth);
        mark.height = (float) (imagePixelH * box.getHeight() / renderedPixelHeight);
    }

    private void startWhiteoutRectangle(MouseEvent event) {
        if (editMode != EditMode.WHITEOUT || event.getTarget() != overlayPane) return;
        whiteoutStartX = event.getX();
        whiteoutStartY = event.getY();
        whiteoutDraftRect = new Rectangle();
        whiteoutDraftRect.setLayoutX(whiteoutStartX);
        whiteoutDraftRect.setLayoutY(whiteoutStartY);
        whiteoutDraftRect.setWidth(1);
        whiteoutDraftRect.setHeight(1);
        whiteoutDraftRect.setFill(Color.rgb(255, 255, 255, 0.75));
        whiteoutDraftRect.setStroke(Color.DODGERBLUE);
        whiteoutDraftRect.setStrokeWidth(2);
        overlayPane.getChildren().add(whiteoutDraftRect);
        event.consume();
    }

    private void dragWhiteoutRectangle(MouseEvent event) {
        if (editMode != EditMode.WHITEOUT || whiteoutDraftRect == null) return;
        double x = Math.min(whiteoutStartX, event.getX());
        double y = Math.min(whiteoutStartY, event.getY());
        double w = Math.abs(event.getX() - whiteoutStartX);
        double h = Math.abs(event.getY() - whiteoutStartY);
        whiteoutDraftRect.setLayoutX(x);
        whiteoutDraftRect.setLayoutY(y);
        whiteoutDraftRect.setWidth(w);
        whiteoutDraftRect.setHeight(h);
        event.consume();
    }

    private void finishWhiteoutRectangle(MouseEvent event) {
        if (editMode != EditMode.WHITEOUT || whiteoutDraftRect == null) return;
        double x = whiteoutDraftRect.getLayoutX();
        double y = whiteoutDraftRect.getLayoutY();
        double w = whiteoutDraftRect.getWidth();
        double h = whiteoutDraftRect.getHeight();
        whiteoutDraftRect = null;
        if (w < 4 || h < 4) {
            redrawOverlays();
            return;
        }
        WhiteoutMark mark = new WhiteoutMark(currentPageIndex, 0, 0, 0, 0);
        moveWhiteoutMarkToOverlayPosition(mark, x, y, w, h);
        whiteoutMarks.add(mark);
        editHistory.add(mark);
        selectedMark = mark;
        editMode = EditMode.NONE;
        refreshModeButtonStyles();
        redrawOverlays();
        append("Added whiteout/blank area on page " + (currentPageIndex + 1));
        event.consume();
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private void undoLastEdit() {
        if (!editHistory.isEmpty()) {
            Object removed = editHistory.remove(editHistory.size() - 1);
            textMarks.remove(removed);
            whiteoutMarks.remove(removed);
            if (selectedMark == removed) selectedMark = null;
            redrawOverlays();
            append("Removed last visual edit.");
        }
    }

    private void deleteSelectedText() {
        if (selectedMark == null) {
            alert("No visual edit selected", "Click an added text box or whiteout box first, then use Delete Selected.");
            return;
        }
        Object removed = selectedMark;
        textMarks.remove(removed);
        whiteoutMarks.remove(removed);
        editHistory.remove(removed);
        selectedMark = null;
        redrawOverlays();
        append("Deleted selected visual edit.");
    }

    private void saveVisualEditsToCurrentPdf() {
        try {
			/*
			 * if (!validateReadyToSave()) return;
			 * 
			 * Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
			 * confirm.setTitle("Overwrite PDF");
			 * confirm.setHeaderText("Save changes into the currently opened PDF?");
			 * confirm.setContentText("This will overwrite:\n" +
			 * currentPdf.getAbsolutePath() +
			 * "\n\nUse Save As PDF if you want to keep the original unchanged.");
			 * ButtonType result = confirm.showAndWait().orElse(ButtonType.CANCEL); if
			 * (result != ButtonType.OK) return;
			 */

            writeVisualEdits(currentPdf, true);
        } catch (Exception ex) {
            error("Save edited PDF failed", ex);
        }
    }

    private void saveVisualEditsAs() {
        try {
            if (!validateReadyToSave()) return;
            File output = saveChooser("edited-with-text.pdf").showSaveDialog(stage);
            if (output == null) return;
            writeVisualEdits(output, false);
        } catch (Exception ex) {
            error("Save As edited PDF failed", ex);
        }
    }

    private boolean validateReadyToSave() {
        if (currentPdf == null) {
            alert("No PDF open", "Open a PDF before saving.");
            return false;
        }
        if (textMarks.isEmpty() && whiteoutMarks.isEmpty()) {
            alert("Nothing to save", "Add at least one text or whiteout/blank area first.");
            return false;
        }
        return true;
    }

    private void writeVisualEdits(File output, boolean overwriteCurrent) throws Exception {
        File actualOutput = output;
        File tempFile = null;

        if (overwriteCurrent) {
            File parent = currentPdf.getParentFile();
            tempFile = new File(parent, currentPdf.getName() + ".tmp-" + System.currentTimeMillis() + ".pdf");
            actualOutput = tempFile;
        }

        writeVisualEditsToFile(actualOutput);

        if (overwriteCurrent) {
            closePreviewDocument();
            Files.move(tempFile.toPath(), currentPdf.toPath(), StandardCopyOption.REPLACE_EXISTING);
            append("Saved changes into existing PDF -> " + currentPdf.getAbsolutePath());
            loadPdfForVisualEditing(currentPdf, true);
        } else {
            append("Saved edited PDF copy -> " + output.getAbsolutePath());
            loadPdfForVisualEditing(output, true);
        }
    }

    private void writeVisualEditsToFile(File output) throws Exception {
        try (PDDocument doc = Loader.loadPDF(currentPdf)) {
            // Whiteout is saved first so it can hide existing PDF content; added text is then written over it.
            for (WhiteoutMark mark : whiteoutMarks) {
                PDPage page = doc.getPage(mark.pageIndex);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    cs.setNonStrokingColor(java.awt.Color.WHITE);
                    cs.addRect(mark.x, mark.y, mark.width, mark.height);
                    cs.fill();
                }
            }
            for (TextMark mark : textMarks) {
                PDPage page = doc.getPage(mark.pageIndex);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    cs.beginText();
                    cs.setFont(toPdfFont(doc, mark.fontName), mark.fontSize);
                    cs.setNonStrokingColor(java.awt.Color.BLACK);
                    cs.newLineAtOffset(mark.x, mark.y);
                    cs.showText(sanitizePdfText(mark.text));
                    cs.endText();
                }
            }
            doc.save(output);
        }
    }

    private PDFont toPdfFont(PDDocument doc, String name) throws IOException {
        File fontFile = findSystemFontFile(name);
        if (fontFile != null && fontFile.exists()) {
            return PDType0Font.load(doc, fontFile);
        }

        Standard14Fonts.FontName fontName;
        if ("Arial Bold".equals(name) || "Helvetica Bold".equals(name)) fontName = Standard14Fonts.FontName.HELVETICA_BOLD;
        else if ("Helvetica Oblique".equals(name)) fontName = Standard14Fonts.FontName.HELVETICA_OBLIQUE;
        else if ("Times Roman".equals(name)) fontName = Standard14Fonts.FontName.TIMES_ROMAN;
        else if ("Times Bold".equals(name)) fontName = Standard14Fonts.FontName.TIMES_BOLD;
        else if ("Courier".equals(name)) fontName = Standard14Fonts.FontName.COURIER;
        else if ("Courier Bold".equals(name)) fontName = Standard14Fonts.FontName.COURIER_BOLD;
        else fontName = Standard14Fonts.FontName.HELVETICA;
        return new PDType1Font(fontName);
    }

    private File findSystemFontFile(String name) {
        String windir = System.getenv("WINDIR");
        String fontsDir = (windir == null || windir.isBlank()) ? "C:\\Windows\\Fonts" : windir + "\\Fonts";
        String fileName = null;

        if ("Arial".equals(name)) fileName = "arial.ttf";
        else if ("Arial Bold".equals(name)) fileName = "arialbd.ttf";
        else if ("Calibri".equals(name)) fileName = "calibri.ttf";
        else if ("Calibri Bold".equals(name)) fileName = "calibrib.ttf";

        if (fileName == null) return null;
        File f = new File(fontsDir, fileName);
        return f.exists() ? f : null;
    }

    private String sanitizePdfText(String input) {
        // Remove control characters; keep normal typed text.
        return input.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "?");
    }

    private void mergePdfs() {
        try {
            FileChooser chooser = pdfChooser("Select PDFs to merge");
            List<File> files = chooser.showOpenMultipleDialog(stage);
            if (files == null || files.isEmpty()) return;

            File output = saveChooser("merged-output.pdf").showSaveDialog(stage);
            if (output == null) return;

            PDFMergerUtility merger = new PDFMergerUtility();
            for (File file : files) merger.addSource(file);
            merger.setDestinationFileName(output.getAbsolutePath());
            merger.mergeDocuments(null);
            append("Merged " + files.size() + " PDFs -> " + output.getAbsolutePath());
        } catch (Exception ex) {
            error("Merge failed", ex);
        }
    }

    private void splitPdf() {
        try {
            File input = openSinglePdf("Select PDF to split");
            if (input == null) return;
            File outputDir = chooseDirectory("Select output folder");
            if (outputDir == null) return;

            try (PDDocument doc = Loader.loadPDF(input)) {
                int pageCount = doc.getNumberOfPages();
                String baseName = removePdfExtension(input.getName());
                for (int i = 0; i < pageCount; i++) {
                    try (PDDocument single = new PDDocument()) {
                        single.importPage(doc.getPage(i));
                        File out = new File(outputDir, baseName + "-page-" + (i + 1) + ".pdf");
                        single.save(out);
                    }
                }
                append("Split " + pageCount + " pages into folder: " + outputDir.getAbsolutePath());
            }
        } catch (Exception ex) {
            error("Split failed", ex);
        }
    }

    private void extractRange() {
        try {
            File input = openSinglePdf("Select PDF");
            if (input == null) return;

            TextInputDialog startDialog = new TextInputDialog("1");
            startDialog.setTitle("Start Page");
            startDialog.setHeaderText("Enter start page number");
            String startStr = startDialog.showAndWait().orElse(null);
            if (startStr == null) return;

            TextInputDialog endDialog = new TextInputDialog("1");
            endDialog.setTitle("End Page");
            endDialog.setHeaderText("Enter end page number");
            String endStr = endDialog.showAndWait().orElse(null);
            if (endStr == null) return;

            int start = Integer.parseInt(startStr.trim());
            int end = Integer.parseInt(endStr.trim());

            File output = saveChooser("extracted-pages.pdf").showSaveDialog(stage);
            if (output == null) return;

            try (PDDocument doc = Loader.loadPDF(input); PDDocument extracted = new PDDocument()) {
                int pageCount = doc.getNumberOfPages();
                if (start < 1 || end > pageCount || start > end) {
                    throw new IllegalArgumentException("Invalid page range. PDF has " + pageCount + " pages.");
                }
                for (int i = start - 1; i <= end - 1; i++) extracted.importPage(doc.getPage(i));
                extracted.save(output);
                append("Extracted pages " + start + "-" + end + " -> " + output.getAbsolutePath());
            }
        } catch (Exception ex) {
            error("Extract failed", ex);
        }
    }

    private void rotatePdf() {
        try {
            File input = openSinglePdf("Select PDF to rotate");
            if (input == null) return;

            ChoiceDialog<Integer> dialog = new ChoiceDialog<>(90, 90, 180, 270);
            dialog.setTitle("Rotate PDF");
            dialog.setHeaderText("Select rotation angle clockwise");
            Integer angle = dialog.showAndWait().orElse(null);
            if (angle == null) return;

            File output = saveChooser("rotated-output.pdf").showSaveDialog(stage);
            if (output == null) return;

            try (PDDocument doc = Loader.loadPDF(input)) {
                for (PDPage page : doc.getPages()) page.setRotation((page.getRotation() + angle) % 360);
                doc.save(output);
                append("Rotated PDF by " + angle + " degrees -> " + output.getAbsolutePath());
            }
        } catch (Exception ex) {
            error("Rotate failed", ex);
        }
    }

    private void addCenterWatermark() {
        try {
            File input = openSinglePdf("Select PDF to watermark");
            if (input == null) return;

            TextInputDialog textDialog = new TextInputDialog("CONFIDENTIAL");
            textDialog.setTitle("Center Watermark");
            textDialog.setHeaderText("Enter watermark text");
            String text = textDialog.showAndWait().orElse(null);
            if (text == null || text.isBlank()) return;

            File output = saveChooser("watermarked-output.pdf").showSaveDialog(stage);
            if (output == null) return;

            try (PDDocument doc = Loader.loadPDF(input)) {
                PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                for (PDPage page : doc.getPages()) {
                    PDRectangle box = page.getMediaBox();
                    float x = box.getWidth() / 2 - 150;
                    float y = box.getHeight() / 2;
                    try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                        cs.beginText();
                        cs.setFont(font, 42);
                        cs.setNonStrokingColor(new java.awt.Color(180, 180, 180));
                        cs.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(35), x, y));
                        cs.showText(sanitizePdfText(text));
                        cs.endText();
                    }
                }
                doc.save(output);
                append("Added center watermark -> " + output.getAbsolutePath());
            }
        } catch (Exception ex) {
            error("Watermark failed", ex);
        }
    }

    private void passwordProtect() {
        try {
            File input = openSinglePdf("Select PDF to protect");
            if (input == null) return;

            TextInputDialog passwordDialog = new TextInputDialog();
            passwordDialog.setTitle("Password Protect");
            passwordDialog.setHeaderText("Enter user password");
            String password = passwordDialog.showAndWait().orElse(null);
            if (password == null || password.isBlank()) return;

            File output = saveChooser("protected-output.pdf").showSaveDialog(stage);
            if (output == null) return;

            try (PDDocument doc = Loader.loadPDF(input)) {
                AccessPermission ap = new AccessPermission();
                StandardProtectionPolicy spp = new StandardProtectionPolicy(password, password, ap);
                spp.setEncryptionKeyLength(256);
                spp.setPermissions(ap);
                doc.protect(spp);
                doc.save(output);
                append("Password protected PDF -> " + output.getAbsolutePath());
            }
        } catch (Exception ex) {
            error("Password protect failed", ex);
        }
    }

    private void showPdfInfo() {
        try {
            File input = openSinglePdf("Select PDF for info");
            if (input == null) return;
            try (PDDocument doc = Loader.loadPDF(input)) {
                append("PDF Info: " + input.getAbsolutePath());
                append("Pages: " + doc.getNumberOfPages());
                append("Encrypted: " + doc.isEncrypted());
                if (doc.getDocumentInformation() != null) {
                    append("Title: " + doc.getDocumentInformation().getTitle());
                    append("Author: " + doc.getDocumentInformation().getAuthor());
                    append("Subject: " + doc.getDocumentInformation().getSubject());
                }
            }
        } catch (Exception ex) {
            error("PDF info failed", ex);
        }
    }

    private FileChooser pdfChooser(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
        return chooser;
    }

    private File openSinglePdf(String title) {
        return pdfChooser(title).showOpenDialog(stage);
    }

    private FileChooser saveChooser(String defaultName) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save PDF");
        chooser.setInitialFileName(defaultName);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
        return chooser;
    }

    private File chooseDirectory(String title) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(title);
        return chooser.showDialog(stage);
    }

    private String removePdfExtension(String name) {
        return name.toLowerCase().endsWith(".pdf") ? name.substring(0, name.length() - 4) : name;
    }

    private void append(String message) {
        log.appendText(message + System.lineSeparator());
    }

    private void error(String title, Exception ex) {
        append(title + ": " + ex.getMessage());
        ex.printStackTrace();
        alert(title, ex.getMessage());
    }

    private void alert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message == null ? "" : message);
        alert.showAndWait();
    }

    private void closePreviewDocument() {
        if (previewDocument != null) {
            try { previewDocument.close(); } catch (IOException ignored) {}
        }
        previewDocument = null;
        renderer = null;
    }

    @Override
    public void stop() {
        closePreviewDocument();
    }

    private static class DragContext {
        double startSceneX;
        double startSceneY;
        double startLayoutX;
        double startLayoutY;
        boolean dragged;
    }

    private enum EditMode {
        NONE, ADD_TEXT, WHITEOUT
    }

    private static class TextMark {
        final int pageIndex;
        float x;
        float y;
        final String text;
        final int fontSize;
        final String fontName;

        TextMark(int pageIndex, float x, float y, String text, int fontSize, String fontName) {
            this.pageIndex = pageIndex;
            this.x = x;
            this.y = y;
            this.text = text;
            this.fontSize = fontSize;
            this.fontName = fontName == null ? "Helvetica" : fontName;
        }
    }

    private static class WhiteoutMark {
        final int pageIndex;
        float x;
        float y;
        float width;
        float height;

        WhiteoutMark(int pageIndex, float x, float y, float width, float height) {
            this.pageIndex = pageIndex;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
