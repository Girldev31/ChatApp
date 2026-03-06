package org.example.chatapp;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import org.example.chatapp.entity.Message;
import org.example.chatapp.entity.User;
import org.example.chatapp.utils.JPAUtil;

import javax.persistence.EntityManager;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ChatController {

    // ======================= FXML =======================

    @FXML private ListView<String> contactsList;
    @FXML private TextField messageField;
    @FXML private TextField searchField;
    @FXML private VBox messagesBox;
    @FXML private Label usernameLabel;
    @FXML private VBox welcomeBox;
    @FXML private HBox chatHeader;
    @FXML private HBox messageBar;
    @FXML private ScrollPane messagesScroll;
    @FXML private Button logoutButton;

    // ── Profil utilisateur connecté (sidebar) ──
    @FXML private Label  currentUserLabel;
    @FXML private Label  avatarInitiale;
    @FXML private Circle avatarCircle;
    @FXML private Circle myStatusDot;
    @FXML private Circle myStatusDotBadge;
    @FXML private Label  myStatusLabel;

    // ── Header contact sélectionné ──
    @FXML private Label  contactInitiale;
    @FXML private Circle contactStatusDot;
    @FXML private Label  contactStatusLabel;

    // ======================= COULEURS =======================

    private static final Color ONLINE_COLOR  = Color.web("#4ECDC4");
    private static final Color OFFLINE_COLOR = Color.web("#666680");

    // ======================= VARIABLES =======================

    private String username;
    private String selectedContact;
    private List<String> allUsers    = new ArrayList<>();
    private List<String> onlineUsers = new ArrayList<>();

    private Socket socket;
    private BufferedReader bufferedReader;
    private BufferedWriter bufferedWriter;

    // ======================= INITIALISATION =======================

    public void setUsername(String username) {
        this.username = username;

        currentUserLabel.setText(username);
        avatarInitiale.setText(String.valueOf(username.charAt(0)).toUpperCase());
        setMyStatus(true);

        chargerTousLesUtilisateurs();
        connectToServer();
        setupContactClickListener();
        setupSearch();
    }

    // ======================= DECONNEXION PROPRE =======================

    /**
     * ✅ Appelé par HelloController quand on ferme la fenêtre (RG4 + RG10).
     * Ferme le socket → le serveur détecte null → passe OFFLINE en base.
     */
    public void disconnect() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Met à jour visuellement si le thread JavaFX est encore actif
        try {
            Platform.runLater(() -> setMyStatus(false));
        } catch (Exception ignored) {}
    }

    // ======================= CHARGER UTILISATEURS =======================

    private void chargerTousLesUtilisateurs() {
        EntityManager em = JPAUtil.getEntityManagerFactory().createEntityManager();
        try {
            List<User> users = em.createQuery(
                    "SELECT u FROM User u", User.class).getResultList();

            System.out.println("=== TOTAL USERS EN BASE : " + users.size() + " ===");
            users.forEach(u -> System.out.println("  → " + u.getUsername()));

            allUsers.clear();
            for (User u : users) {
                if (!u.getUsername().equals(username)) {
                    allUsers.add(u.getUsername());
                }
            }

            System.out.println("=== CONTACTS A AFFICHER : " + allUsers.size() + " ===");

            // Met à jour la ListView sur le thread JavaFX
            Platform.runLater(() -> contactsList.getItems().setAll(allUsers));

        } finally {
            em.close();
        }
    }

    // ======================= RECHERCHE =======================

    private void setupSearch() {
        if (searchField == null) return;
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            String s = newVal.toLowerCase();
            List<String> filtered = allUsers.stream()
                    .filter(u -> u.toLowerCase().contains(s))
                    .collect(Collectors.toList());
            contactsList.getItems().setAll(filtered);
        });
    }

    // ======================= CONNEXION SERVEUR =======================

    private void connectToServer() {
        try {
            socket = new Socket("localhost", 1234);
            bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            bufferedWriter.write(username);
            bufferedWriter.newLine();
            bufferedWriter.flush();
            startListening();
        } catch (IOException e) {
            Platform.runLater(() -> {
                setMyStatus(false);
                afficherErreurConnexion();
            });
        }
    }

    // ======================= ECOUTE SERVEUR =======================

    private void startListening() {
        Thread t = new Thread(() -> {
            try {
                String msg;
                while ((msg = bufferedReader.readLine()) != null) {
                    final String line = msg;
                    if (line.startsWith("USERLIST|")) {
                        Platform.runLater(() -> updateOnlineUsers(line));
                    } else {
                        String[] parts = line.split("\\|", 2);
                        if (parts.length == 2) {
                            String sender  = parts[0];
                            String content = parts[1];
                            if (sender.equals(selectedContact)) {
                                Platform.runLater(() -> afficherBulle(content, false));
                            }
                        }
                    }
                }
            } catch (IOException e) {
                // Connexion coupée → passe hors ligne visuellement
                Platform.runLater(() -> setMyStatus(false));
            }
        });
        t.setDaemon(true);
        t.start();
    }

    // ======================= UTILISATEURS EN LIGNE =======================

    private void updateOnlineUsers(String msg) {
        String raw = msg.replace("USERLIST|", "");
        onlineUsers = new ArrayList<>(List.of(raw.split(",")));
        contactsList.refresh();
        if (selectedContact != null) {
            updateContactStatusHeader(selectedContact);
        }
    }

    // ======================= STATUT MOI =======================

    private void setMyStatus(boolean online) {
        Color c    = online ? ONLINE_COLOR : OFFLINE_COLOR;
        String txt = online ? "En ligne" : "Déconnecté";
        String bg  = online
                ? "-fx-background-color: rgba(78,205,196,0.2);"
                : "-fx-background-color: rgba(100,100,128,0.2);";

        myStatusDot.setFill(c);
        myStatusDotBadge.setFill(c);
        myStatusLabel.setText(txt);
        myStatusLabel.setStyle("-fx-text-fill: " + toHex(c) + "; -fx-font-size: 10px; -fx-font-weight: bold;");
        myStatusLabel.getParent().setStyle(bg + "-fx-background-radius: 20; -fx-padding: 2 8 2 6;");
    }

    // ======================= STATUT CONTACT =======================

    private void updateContactStatusHeader(String contact) {
        boolean online = onlineUsers.contains(contact);
        Color c = online ? ONLINE_COLOR : OFFLINE_COLOR;
        contactStatusDot.setFill(c);
        contactStatusLabel.setText(online ? "En ligne" : "Hors ligne");
        contactStatusLabel.setStyle("-fx-text-fill: " + toHex(c) + "; -fx-font-size: 11px;");
    }

    // ======================= CELLULES CONTACTS =======================

    private void setupContactClickListener() {

        contactsList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String user, boolean empty) {
                super.updateItem(user, empty);

                if (empty || user == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("-fx-background-color: transparent;");
                    return;
                }

                boolean online = onlineUsers.contains(user);

                // Conteneur principal
                HBox cell = new HBox(10);
                cell.setAlignment(Pos.CENTER_LEFT);
                cell.setPadding(new Insets(8, 10, 8, 8));

                // Avatar (AnchorPane)
                javafx.scene.layout.AnchorPane ap = new javafx.scene.layout.AnchorPane();
                ap.setPrefSize(40, 40);
                ap.setMinSize(40, 40);

                Circle bgCircle = new Circle(18);
                bgCircle.setFill(online ? Color.web("#A8D8F0") : Color.web("#9B8AB0"));
                javafx.scene.layout.AnchorPane.setTopAnchor(bgCircle, 2.0);
                javafx.scene.layout.AnchorPane.setLeftAnchor(bgCircle, 2.0);

                Label init = new Label(String.valueOf(user.charAt(0)).toUpperCase());
                init.setStyle("-fx-text-fill: #3D1A47; -fx-font-size: 13px; -fx-font-weight: bold;");
                javafx.scene.layout.AnchorPane.setTopAnchor(init, 10.0);
                javafx.scene.layout.AnchorPane.setLeftAnchor(init, 10.0);

                Circle dot = new Circle(6);
                dot.setFill(online ? ONLINE_COLOR : OFFLINE_COLOR);
                dot.setStroke(Color.web("#6a3bbf"));
                dot.setStrokeWidth(2);
                javafx.scene.layout.AnchorPane.setBottomAnchor(dot, 0.0);
                javafx.scene.layout.AnchorPane.setRightAnchor(dot, 0.0);

                ap.getChildren().addAll(bgCircle, init, dot);

                // Infos texte
                VBox info = new VBox(2);
                HBox.setHgrow(info, Priority.ALWAYS);

                Label name = new Label(user);
                name.setStyle("-fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold;");

                Label statusTxt = new Label(online ? "En ligne" : "Hors ligne");
                statusTxt.setStyle("-fx-text-fill: " +
                        (online ? "#4ECDC4" : "rgba(255,255,255,0.5)") +
                        "; -fx-font-size: 10px;");

                info.getChildren().addAll(name, statusTxt);
                cell.getChildren().addAll(ap, info);

                setGraphic(cell);
                setText(null);
                setStyle("-fx-background-color: " +
                        (isSelected() ? "rgba(255,255,255,0.15)" : "transparent") +
                        "; -fx-background-radius: 10; -fx-padding: 2 0;");
            }
        });

        // Clic contact → ouvre la conversation
        contactsList.setOnMouseClicked(e -> {
            String clicked = contactsList.getSelectionModel().getSelectedItem();
            if (clicked == null || clicked.equals(selectedContact)) return;

            selectedContact = clicked;
            messagesBox.getChildren().clear();

            usernameLabel.setText(selectedContact);
            contactInitiale.setText(String.valueOf(selectedContact.charAt(0)).toUpperCase());
            updateContactStatusHeader(selectedContact);

            welcomeBox.setVisible(false);
            welcomeBox.setManaged(false);
            chatHeader.setVisible(true);
            chatHeader.setManaged(true);
            messageBar.setVisible(true);
            messageBar.setManaged(true);
            messagesScroll.setVisible(true);
            messagesScroll.setManaged(true);

            chargerHistorique(selectedContact);
        });
    }

    // ======================= HISTORIQUE =======================

    private void chargerHistorique(String contact) {
        EntityManager em = JPAUtil.getEntityManagerFactory().createEntityManager();
        try {
            List<Message> messages = em.createQuery(
                            "SELECT m FROM Message m " +
                                    "WHERE (m.sender.username = :me AND m.receiver.username = :contact) " +
                                    "OR (m.sender.username = :contact AND m.receiver.username = :me) " +
                                    "ORDER BY m.dateEnvoi ASC", Message.class)
                    .setParameter("me", username)
                    .setParameter("contact", contact)
                    .getResultList();

            for (Message m : messages) {
                boolean isMine = m.getSender().getUsername().equals(username);
                afficherBulle(m.getContenu(), isMine);
            }
        } finally {
            em.close();
        }
    }

    // ======================= ENVOI MESSAGE =======================

    @FXML
    private void sendMessage() {
        String content = messageField.getText();
        if (selectedContact == null) {
            afficherAlerte("Sélectionne un contact d'abord !");
            return;
        }
        if (content == null || content.trim().isEmpty()) return;
        try {
            bufferedWriter.write(username + "|" + selectedContact + "|" + content);
            bufferedWriter.newLine();
            bufferedWriter.flush();
            afficherBulle(content, true);
            messageField.clear();
        } catch (IOException e) {
            afficherErreurConnexion();
        }
    }

    // ======================= BULLES =======================

    private void afficherBulle(String content, boolean isMine) {
        HBox wrapper = new HBox();
        wrapper.setPadding(new Insets(4));

        Label bulle = new Label(content);
        bulle.setWrapText(true);
        bulle.setMaxWidth(400);
        bulle.setPadding(new Insets(10, 14, 10, 14));

        if (isMine) {
            bulle.setStyle(
                    "-fx-background-color: #6a3bbf;" +
                            "-fx-text-fill: white;" +
                            "-fx-background-radius: 18 18 4 18;");
            wrapper.setAlignment(Pos.CENTER_RIGHT);
        } else {
            bulle.setStyle(
                    "-fx-background-color: #f0ebff;" +
                            "-fx-text-fill: #1A0A22;" +
                            "-fx-background-radius: 18 18 18 4;");
            wrapper.setAlignment(Pos.CENTER_LEFT);
        }

        wrapper.getChildren().add(bulle);
        messagesBox.getChildren().add(wrapper);
        Platform.runLater(() -> messagesScroll.setVvalue(1.0));
    }

    // ======================= BOUTON DECONNEXION =======================

    @FXML
    private void logout() {
        disconnect(); // ✅ ferme le socket proprement
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/org/example/chatapp/login-view.fxml"));
            Stage stage = (Stage) logoutButton.getScene().getWindow();
            stage.setOnCloseRequest(null); // reset le handler de fermeture
            stage.setScene(new Scene(loader.load()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ======================= UTILS =======================

    private String toHex(Color c) {
        return String.format("#%02X%02X%02X",
                (int)(c.getRed()   * 255),
                (int)(c.getGreen() * 255),
                (int)(c.getBlue()  * 255));
    }

    private void afficherErreurConnexion() {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Connexion perdue");
        a.setContentText("Connexion au serveur perdue. Vous êtes maintenant hors ligne.");
        a.show();
    }

    private void afficherAlerte(String message) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setContentText(message);
        a.show();
    }
}