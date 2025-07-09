// JavaFX imports for GUI components
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.Group;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Pair;
import java.util.*;

public class Main extends Application {

    // Data structures to store graph components
    private final Map<String, Circle> nodes = new HashMap<>(); // Node visuals
    private final Map<String, List<String>> graph = new HashMap<>(); // Adjacency list
    private final Map<String, double[]> coordinates = new HashMap<>(); // Node positions
    private final Map<String, String> parent = new HashMap<>(); // For BFS path reconstruction
    private final Map<String, Text> labels = new HashMap<>(); // Node labels
    private final List<Line> edges = new ArrayList<>(); // All edges in graph
    private final Set<String> defaultNodes = new HashSet<>(Arrays.asList("A",
            "B", "C", "D", "E", "F")); // Default nodes
    private final List<Line> pathEdges = new ArrayList<>(); // Path highlighted by BFS

    // UI components
    private Group graphGroup; // Container for graph elements
    private ComboBox<String> startSelector, endSelector; // Dropdowns for start/end nodes
    private Label distanceLabel; // Displays shortest path distance

    @Override
    public void start(Stage stage) {
        // Main layout setup
        BorderPane borderPane = new BorderPane();
        graphGroup = new Group();
        borderPane.setCenter(graphGroup);

        // Initialize UI controls
        startSelector = createStyledComboBox();
        endSelector = createStyledComboBox();
        Button runBFS = createStyledButton("Run BFS");
        Button resetBtn = createStyledButton("Reset");

        // Configure dropdown placeholders
        startSelector.setPromptText("Start");
        endSelector.setPromptText("End");

        // Configure distance label
        distanceLabel = new Label();
        distanceLabel.setTextFill(Color.WHITE);
        distanceLabel.setFont(Font.font("Consolas", FontWeight.BOLD, 16));
        distanceLabel.setVisible(false);

        // Initialize default graph
        resetGraphToDefault();

        // BFS button action
        runBFS.setOnAction(e -> {
            resetColors();
            distanceLabel.setVisible(false);
            String start = startSelector.getValue();
            String end = endSelector.getValue();
            if (start != null && end != null) {
                new Thread(() -> bfs(start, end)).start(); // Run BFS in background
            }
        });

        // Reset button action
        resetBtn.setOnAction(e -> {
            resetGraphToDefault();
            distanceLabel.setVisible(false);
        });

        // Top control panel setup
        Label startLabel = new Label("Start Node:");
        startLabel.setTextFill(Color.WHITE);
        Label endLabel = new Label("End Node:");
        endLabel.setTextFill(Color.WHITE);
        HBox topBar = new HBox(10, startLabel, startSelector, endLabel,
                endSelector, runBFS, resetBtn, distanceLabel);
        topBar.setAlignment(Pos.CENTER);
        topBar.setPadding(new Insets(10));
        topBar.setStyle("-fx-background-color: black;");
        borderPane.setTop(topBar);

        // Bottom control buttons
        Button addNodeBtn = createStyledButton("Add Node");
        Button addEdgeBtn = createStyledButton("Add Edge");
        Button removeEdgeBtn = createStyledButton("Remove Edge");
        Button removeNodeBtn = createStyledButton("Remove Node");
        Button renameNodeBtn = createStyledButton("Rename Node");
        HBox bottomBar = new HBox(10, addNodeBtn, addEdgeBtn, removeEdgeBtn,
                removeNodeBtn, renameNodeBtn);
        bottomBar.setAlignment(Pos.CENTER);
        bottomBar.setPadding(new Insets(10));
        bottomBar.setStyle("-fx-background-color: black;");
        borderPane.setBottom(bottomBar);

        // Main window styling
        borderPane.setStyle("-fx-background-color: black;");

        // Add Node button action
        addNodeBtn.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Add Node");
            dialog.setHeaderText("Enter node name:");
            Optional<String> result = dialog.showAndWait();
            result.ifPresent(name -> {
                name = name.trim().toUpperCase();
                if (name.isEmpty() || graph.containsKey(name)) {
                    log("Invalid or duplicate node name.");
                    return;
                }
                // Position new node in circular layout
                int nodeCount = graph.size();
                double centerX = 450;
                double centerY = 300;
                double radius = 200;
                double angle = 2 * Math.PI * nodeCount / 6;
                double x = centerX + radius * Math.cos(angle);
                double y = centerY + radius * Math.sin(angle);

                // Add to data structures
                coordinates.put(name, new double[]{x, y});
                graph.put(name, new ArrayList<>());
                Circle circle = createNeonNode(x, y, name);
                nodes.put(name, circle);
                graphGroup.getChildren().add(circle);
                // Update dropdowns
                startSelector.getItems().add(name);
                endSelector.getItems().add(name);
                log("Node " + name + " added.");
            });
        });

        // Add Edge button action
        addEdgeBtn.setOnAction(e -> {
            Dialog<Pair<String, String>> dialog = new Dialog<>();
            dialog.setTitle("Add Edge");
            dialog.setHeaderText("Enter From and To node names:");

            // Dialog form setup
            Label fromLabel = new Label("From: ");
            Label toLabel = new Label("To: ");
            TextField fromField = new TextField();
            TextField toField = new TextField();
            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new Insets(20, 150, 10, 10));
            grid.add(fromLabel, 0, 0);
            grid.add(fromField, 1, 0);
            grid.add(toLabel, 0, 1);
            grid.add(toField, 1, 1);
            dialog.getDialogPane().setContent(grid);

            // Dialog buttons
            ButtonType okButtonType = new ButtonType("OK",
                    ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(okButtonType,
                    ButtonType.CANCEL);

            // Process result
            dialog.setResultConverter(dialogButton -> {
                if (dialogButton == okButtonType) {
                    return new Pair<>(fromField.getText().trim(),
                            toField.getText().trim());
                }
                return null;
            });

            Optional<Pair<String, String>> result = dialog.showAndWait();
            result.ifPresent(pair -> {
                String from = pair.getKey().toUpperCase();
                String to = pair.getValue().toUpperCase();
                if (!graph.containsKey(from) || !graph.containsKey(to)) {
                    log("Invalid node name(s).");
                    return;
                }
                if (graph.get(from).contains(to)) {
                    log("Edge already exists.");
                    return;
                }
                connect(from, to); // Add to graph
                drawLineSafe(from, to, Color.DARKGRAY); // Visualize
                log("Edge " + from + "-" + to + " added.");
            });
        });

        // Remove Edge button action
        removeEdgeBtn.setOnAction(e -> {
            if (edges.isEmpty()) {
                log("No edges to remove.");
                return;
            }

            Dialog<Pair<String, String>> dialog = new Dialog<>();
            dialog.setTitle("Remove Edge");
            dialog.setHeaderText("Enter From and To node names of edge to remove:");

            // Dialog form setup
            Label fromLabel = new Label("From: ");
            Label toLabel = new Label("To: ");
            TextField fromField = new TextField();
            TextField toField = new TextField();
            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new Insets(20, 150, 10, 10));
            grid.add(fromLabel, 0, 0);
            grid.add(fromField, 1, 0);
            grid.add(toLabel, 0, 1);
            grid.add(toField, 1, 1);
            dialog.getDialogPane().setContent(grid);

            // Dialog buttons
            ButtonType okButtonType = new ButtonType("OK",
                    ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(okButtonType,
                    ButtonType.CANCEL);

            // Process result
            dialog.setResultConverter(dialogButton -> {
                if (dialogButton == okButtonType) {
                    return new Pair<>(fromField.getText().trim(),
                            toField.getText().trim());
                }
                return null;
            });

            Optional<Pair<String, String>> result = dialog.showAndWait();
            result.ifPresent(pair -> {
                String from = pair.getKey().toUpperCase();
                String to = pair.getValue().toUpperCase();

                // Validation
                if (!graph.containsKey(from) || !graph.containsKey(to)) {
                    log("Invalid node name(s).");
                    return;
                }

                if (!graph.get(from).contains(to)) {
                    log("Edge doesn't exist.");
                    return;
                }

                // Remove from graph
                graph.get(from).remove(to);
                graph.get(to).remove(from);

                // Redraw edges
                redrawEdges();
                log("Edge " + from + "-" + to + " removed.");
            });
        });

        // Remove Node button action
        removeNodeBtn.setOnAction(e -> {
            if (nodes.isEmpty()) {
                log("No nodes to remove.");
                return;
            }

            // Node selection dialog
            ChoiceDialog<String> dialog = new ChoiceDialog<>(null, new
                    ArrayList<>(nodes.keySet()));
            dialog.setTitle("Remove Node");
            dialog.setHeaderText("Select the node you want to remove:");
            dialog.setContentText("Node:");

            Optional<String> result = dialog.showAndWait();
            result.ifPresent(nodeName -> {
                // Remove visual elements
                Circle circle = nodes.remove(nodeName);
                if (circle != null) graphGroup.getChildren().remove(circle);
                Text label = labels.remove(nodeName);
                if (label != null) graphGroup.getChildren().remove(label);

                // Remove from data structures
                graph.remove(nodeName);
                coordinates.remove(nodeName);
                startSelector.getItems().remove(nodeName);
                endSelector.getItems().remove(nodeName);

                // Remove from all adjacency lists
                for (List<String> neighbors : graph.values()) {
                    neighbors.remove(nodeName);
                }

                redrawEdges(); // Update visualization
                log("Node " + nodeName + " removed.");
            });
        });

        // Rename Node button action
        renameNodeBtn.setOnAction(e -> {
            if (nodes.isEmpty()) {
                log("No nodes to rename.");
                return;
            }

            // Node selection dialog
            ChoiceDialog<String> choiceDialog = new ChoiceDialog<>(null, new
                    ArrayList<>(nodes.keySet()));
            choiceDialog.setTitle("Rename Node");
            choiceDialog.setHeaderText("Select a node to rename:");
            choiceDialog.setContentText("Node:");

            Optional<String> selectedResult = choiceDialog.showAndWait();
            selectedResult.ifPresent(oldName -> {
                // New name input dialog
                TextInputDialog inputDialog = new TextInputDialog(oldName);
                inputDialog.setTitle("Rename Node");
                inputDialog.setHeaderText("Enter new name for node: " +
                        oldName);
                inputDialog.setContentText("New Name:");

                Optional<String> newNameResult = inputDialog.showAndWait();
                newNameResult.ifPresent(newName -> {
                    newName = newName.trim().toUpperCase();
                    if (newName.isEmpty() || graph.containsKey(newName)) {
                        log("Invalid or duplicate name.");
                        return;
                    }

                    // Update all references to old name
                    graph.put(newName, graph.remove(oldName));
                    coordinates.put(newName, coordinates.remove(oldName));
                    Circle nodeCircle = nodes.remove(oldName);
                    nodes.put(newName, nodeCircle);

                    // Update label
                    Text nodeLabel = labels.remove(oldName);
                    if (nodeLabel != null) {
                        nodeLabel.setText(newName);
                        labels.put(newName, nodeLabel);
                    }

                    // Update all adjacency lists
                    for (Map.Entry<String, List<String>> entry :
                            graph.entrySet()) {
                        List<String> neighbors = entry.getValue();
                        for (int i = 0; i < neighbors.size(); i++) {
                            if (neighbors.get(i).equals(oldName)) {
                                neighbors.set(i, newName);
                            }
                        }
                    }

                    // Update parent reference if needed
                    if (parent.containsKey(oldName)) {
                        parent.put(newName, parent.remove(oldName));
                    }

                    // Update dropdowns
                    startSelector.getItems().remove(oldName);
                    endSelector.getItems().remove(oldName);
                    startSelector.getItems().add(newName);
                    endSelector.getItems().add(newName);

                    redrawEdges(); // Update visualization
                    log("Node renamed from " + oldName + " to " + newName);
                });
            });
        });

        // Create and show main window
        Scene scene = new Scene(borderPane, 900, 600, Color.web("#0d0d0d"));
        stage.setTitle("🌌 BFS Visualizer");
        stage.setScene(scene);
        stage.show();
    }

    // Reset graph to default 6-node circle
    private void resetGraphToDefault() {
        Platform.runLater(() -> {
            // Clear all data
            graphGroup.getChildren().clear();
            graph.clear();
            nodes.clear();
            coordinates.clear();
            labels.clear();
            edges.clear();
            pathEdges.clear();
            parent.clear();
            startSelector.getItems().clear();
            endSelector.getItems().clear();

            // Default node positions in circle
            double centerX = 450;
            double centerY = 300;
            double radius = 200;
            String[] nodeNames = {"A", "B", "C", "D", "E", "F"};

            // Position nodes equally around circle
            for (int i = 0; i < nodeNames.length; i++) {
                double angle = 2 * Math.PI * i / nodeNames.length;
                double x = centerX + radius * Math.cos(angle);
                double y = centerY + radius * Math.sin(angle);
                coordinates.put(nodeNames[i], new double[]{x, y});
                graph.put(nodeNames[i], new ArrayList<>());
            }

            // Connect nodes in circle
            connect("A", "B");
            connect("B", "C");
            connect("C", "D");
            connect("D", "E");
            connect("E", "F");
            connect("F", "A");

            // Draw the graph
            drawGraph();

            // Populate dropdowns
            for (String label : graph.keySet()) {
                startSelector.getItems().add(label);
                endSelector.getItems().add(label);
            }

            log("Graph reset to default circular layout.");
        });
    }

    // Draw all nodes and edges
    private void drawGraph() {
        for (String label : coordinates.keySet()) {
            double[] pos = coordinates.get(label);
            Circle circle = createNeonNode(pos[0], pos[1], label);
            nodes.put(label, circle);
            graphGroup.getChildren().add(circle);
        }
        drawAllEdges(Color.DARKGRAY);
    }

    // Create a visual node with styling
    private Circle createNeonNode(double x, double y, String label) {
        Circle circle = new Circle(x, y, 22);
        circle.setFill(Color.web("#800000")); // Maroon color
        circle.setStroke(Color.web("white"));
        circle.setStrokeWidth(2);
        circle.setEffect(new DropShadow(18, Color.web("white"))); // Glow effect

        // Tooltip shows node name
        Tooltip tip = new Tooltip("Node: " + label);
        Tooltip.install(circle, tip);

        // Make node draggable
        circle.setOnMousePressed(e -> {
            circle.setUserData(new double[]{e.getSceneX(), e.getSceneY(),
                    circle.getCenterX(), circle.getCenterY()});
        });

        circle.setOnMouseDragged(e -> {
            double[] data = (double[]) circle.getUserData();
            double deltaX = e.getSceneX() - data[0];
            double deltaY = e.getSceneY() - data[1];
            double newX = data[2] + deltaX;
            double newY = data[3] + deltaY;
            circle.setCenterX(newX);
            circle.setCenterY(newY);
            coordinates.put(label, new double[]{newX, newY});
            // Update label position
            Text t = labels.get(label);
            if (t != null) {
                t.setX(newX - 5);
                t.setY(newY + 5);
            }
            redrawEdges(); // Update connections
        });

        // Create and position label
        Text text = new Text(x - 5, y + 5, label);
        text.setFill(Color.WHITE);
        text.setFont(Font.font("Consolas", FontWeight.BOLD, 16));
        labels.put(label, text);
        Platform.runLater(() -> graphGroup.getChildren().add(text));

        return circle;
    }

    // Create a styled button
    private Button createStyledButton(String label) {
        Button button = new Button(label);
        button.setStyle("-fx-background-color: maroon; -fx-text-fill: white; -fx-font-weight: bold;");
        return button;
    }

    // Create a styled dropdown
    private ComboBox<String> createStyledComboBox() {
        ComboBox<String> comboBox = new ComboBox<>();
        comboBox.setStyle(
                "-fx-background-color: maroon;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-weight: bold;" +
                        "-fx-prompt-text-fill: white;"
        );

        // Custom cell rendering
        comboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(item);
                setTextFill(Color.WHITE);
                setStyle("-fx-background-color: maroon; -fx-font-weight: bold;");
            }
        });

        comboBox.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    setTextFill(Color.WHITE);
                    setStyle("-fx-background-color: maroon; -fx-font-weight: bold;");
                }
            }
        });

        return comboBox;
    }

    // Connect two nodes (undirected)
    private void connect(String from, String to) {
        if (!graph.get(from).contains(to)) {
            graph.get(from).add(to);
        }
        if (!graph.get(to).contains(from)) {
            graph.get(to).add(from);
        }
    }

    // Draw a line between nodes
    private void drawLineSafe(String from, String to, Color color) {
        double[] fromPos = coordinates.get(from);
        double[] toPos = coordinates.get(to);
        Line line = new Line(fromPos[0], fromPos[1], toPos[0], toPos[1]);
        line.setStroke(color);
        line.setStrokeWidth(2);
        edges.add(line);
        Platform.runLater(() -> graphGroup.getChildren().add(0, line)); //Add behind nodes
    }

    // Draw all edges in graph
    private void drawAllEdges(Color color) {
        for (String from : graph.keySet()) {
            for (String to : graph.get(from)) {
                if (from.compareTo(to) < 0) { // Draw each edge only once
                    drawLineSafe(from, to, color);
                }
            }
        }
    }

    // Redraw all edges (after changes)
    private void redrawEdges() {
        Platform.runLater(() -> {
            graphGroup.getChildren().removeAll(edges);
            graphGroup.getChildren().removeAll(pathEdges);
            edges.clear();
            pathEdges.clear();
            drawAllEdges(Color.WHITE);
        });
    }

    // Breadth-First Search algorithm
    private void bfs(String start, String end) {
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        parent.clear(); // Clear previous path

        // Start from initial node
        queue.add(start);
        visited.add(start);
        highlight(start, Color.GREEN); // Mark start
        log("Starting BFS from: " + start);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            log("Visiting: " + current);

            // Check if reached destination
            if (current.equals(end)) {
                log("Destination " + end + " found.");
                for (String node : visited) {
                    highlight(end, Color.GREEN);
                }

                // Calculate and display distance
                int distance = calculateDistance(start, end);
                Platform.runLater(() -> {
                    distanceLabel.setText("   Shortest Distance: " +
                            distance);
                    distanceLabel.setVisible(true);
                });

                drawPath(start, end); // Visualize path
                log("Shortest distance from " + start + " to " + end + " is "
                        + distance);
                return;
            }

            // Explore neighbors
            for (String neighbor : graph.get(current)) {
                if (!visited.contains(neighbor)) {
                    parent.put(neighbor, current); // Track path
                    visited.add(neighbor);
                    queue.add(neighbor);
                    highlight(neighbor, Color.ORANGE); // Mark visited
                    sleep(500); // Animation delay
                }
            }
        }
        // No path found
        log("Destination " + end + " not reachable.");
        Platform.runLater(() -> {
            distanceLabel.setText("No path exists!");
            distanceLabel.setVisible(true);
        });
    }

    // Calculate path distance
    private int calculateDistance(String start, String end) {
        int distance = 0;
        String current = end;
        // Trace back through parent pointers
        while (!current.equals(start)) {
            distance++;
            current = parent.get(current);
        }
        return distance;
    }

    // Draw the shortest path
    private void drawPath(String start, String end) {
        String current = end;
        while (!current.equals(start)) {
            String prev = parent.get(current);
            double[] fromPos = coordinates.get(prev);
            double[] toPos = coordinates.get(current);
            Line pathLine = new Line(fromPos[0], fromPos[1], toPos[0],
                    toPos[1]);
            pathLine.setStroke(Color.GREEN); // Highlight path
            pathLine.setStrokeWidth(10); // Thicker line
            pathEdges.add(pathLine);

            Platform.runLater(() -> {
                graphGroup.getChildren().add(0, pathLine);
            });

            current = prev;
            sleep(300); // Animation delay
        }
        log("Path drawn in green from " + start + " to " + end);
    }

    // Change node color
    private void highlight(String nodeId, Color color) {
        Circle circle = nodes.get(nodeId);
        if (circle != null) {
            Platform.runLater(() -> {
                circle.setFill(color);
                circle.setStroke(Color.WHITE);
            });
        }
    }

    // Reset all node colors to default
    private void resetColors() {
        Platform.runLater(() -> {
            for (Map.Entry<String, Circle> entry : nodes.entrySet()) {
                entry.getValue().setFill(Color.web("#800000"));
                entry.getValue().setStroke(Color.WHITE);
            }
            graphGroup.getChildren().removeAll(pathEdges);
            pathEdges.clear();
            redrawEdges();
        });
    }

    // Helper for animation delays
    private void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    // Console logging
    private void log(String message) {
        System.out.println("[LOG] " + message);
    }

    public static void main(String[] args) {
        launch(); // Start JavaFX application
    }
}
