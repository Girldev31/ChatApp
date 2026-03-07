package org.example.chatapp;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import org.example.chatapp.entity.Message;
import org.example.chatapp.entity.User;
import org.example.chatapp.enumeration.StatusMessage;
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
    @FXML private TextField        messageField;
    @FXML private TextField        searchField;
    @FXML private VBox             messagesBox;
    @FXML private Label            usernameLabel;
    @FXML private VBox             welcomeBox;
    @FXML private HBox             chatHeader;
    @FXML private HBox             messageBar;
    @FXML private ScrollPane       messagesScroll;
    @FXML private Button           logoutButton;

    @FXML private Label  currentUserLabel;
    @FXML private Label  avatarInitiale;
    @FXML private Circle avatarCircle;
    @FXML private Circle myStatusDot;
    @FXML private Circle myStatusDotBadge;
    @FXML private Label  myStatusLabel;

    @FXML private Label  contactInitiale;
    @FXML private Circle contactStatusDot;
    @FXML private Label  contactStatusLabel;

    // ======================= COULEURS =======================

    private static final Color ONLINE_COLOR  = Color.web("#4ECDC4");
    private static final Color OFFLINE_COLOR = Color.web("#888888");

    private static final String BUBBLE_SENT     = "#6a3bbf";
    private static final String BUBBLE_SENT_TXT = "#FFFFFF";
    private static final String BUBBLE_RECV     = "#D8C8F5";
    private static final String BUBBLE_RECV_TXT = "#2A1A4A";

    // ======================= VARIABLES =======================

    private String       username;
    private String       selectedContact;
    private List<String> allUsers    = new ArrayList<>();
    private List<String> onlineUsers = new ArrayList<>();

    private Socket         socket;
    private BufferedReader bufferedReader;
    private BufferedWriter bufferedWriter;

    // ✅ Flag pour distinguer fermeture volontaire vs perte réseau
    private volatile boolean deconnexionVolontaire = false;

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

    public void disconnect() {
        System.out.println("[CLIENT] disconnect() pour : " + username);
        deconnexionVolontaire = true;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        HelloApplication.activeChatController = null;
        try { Platform.runLater(() -> setMyStatus(false)); }
        catch (Exception ignored) {}
    }

    // ======================= CHARGER UTILISATEURS =======================

    private void chargerTousLesUtilisateurs() {
        EntityManager em = JPAUtil.getEntityManagerFactory().createEntityManager();
        try {
            List<User> users = em.createQuery(
                    "SELECT u FROM User u", User.class).getResultList();
            System.out.println("=== TOTAL USERS EN BASE : " + users.size() + " ===");
            allUsers.clear();
            for (User u : users) {
                if (!u.getUsername().equals(username))
                    allUsers.add(u.getUsername());
            }
            System.out.println("=== CONTACTS A AFFICHER : " + allUsers.size() + " ===");
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
            Platform.runLater(() -> setMyStatus(true));
            startListening();
        } catch (IOException e) {
            Platform.runLater(() -> {
                setMyStatus(false);
                afficherAlerte("Impossible de se connecter au serveur !");
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

                    // RG3 : expulsion session dupliquée
                    if (line.equals("KICKED")) {
                        Platform.runLater(this::forcerDeconnexion);
                        break;
                    }

                    if (line.startsWith("USERLIST|")) {
                        Platform.runLater(() -> updateOnlineUsers(line));

                    } else if (line.startsWith("READ_ACK|")) {
                        // ✅ Le destinataire a ouvert notre conversation → ticks bleus
                        String reader = line.replace("READ_ACK|", "");
                        if (reader.equalsIgnoreCase(selectedContact)) {
                            Platform.runLater(() -> mettreAJourTicksBleus());
                        }

                    } else if (line.startsWith("DELIVERED_ACK|")) {
                        // ✅ Le destinataire vient de se connecter → messages livrés → 2 traits gris
                        String delivered = line.replace("DELIVERED_ACK|", "");
                        if (delivered.equalsIgnoreCase(selectedContact)) {
                            Platform.runLater(() -> mettreAJourTicksDouble());
                        }

                    } else {
                        String[] parts = line.split("\\|", 2);
                        if (parts.length == 2) {
                            String sender  = parts[0];
                            String content = parts[1];
                            if (sender.equals(selectedContact)) {
                                Platform.runLater(() ->
                                        afficherBulle(content, false, StatusMessage.RECU));
                            }
                        }
                    }
                }
            } catch (IOException e) {
                if (!deconnexionVolontaire) {
                    Platform.runLater(() -> {
                        setMyStatus(false);
                        forcerDeconnexion();
                    });
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }

    // ======================= DECONNEXION FORCEE (RG10 / RG3) =======================

    private void forcerDeconnexion() {
        disconnect();
        try {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Déconnecté");
            alert.setHeaderText(null);
            alert.setContentText("Vous avez été déconnecté.\n" +
                    "(Perte réseau ou connexion depuis un autre appareil)");
            alert.showAndWait();

            Stage stage = (Stage) currentUserLabel.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/org/example/chatapp/hello-view.fxml"));

            stage.setOnCloseRequest(e -> {
                javafx.application.Platform.exit();
                System.exit(0);
            });
            stage.setScene(new Scene(loader.load()));
            stage.setTitle("FBChat - Connexion");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ======================= MISE A JOUR STATUTS =======================

    private void updateOnlineUsers(String msg) {
        String raw = msg.replace("USERLIST|", "");
        onlineUsers = new ArrayList<>();
        for (String u : raw.split(",")) {
            if (!u.trim().isEmpty()) onlineUsers.add(u.trim());
        }

        // ✅ FORCER le redessin complet : vider + reremplir la liste
        List<String> snapshot = new ArrayList<>(contactsList.getItems());
        contactsList.getItems().clear();
        contactsList.getItems().setAll(snapshot);

        if (selectedContact != null)
            updateContactStatusHeader(selectedContact);
    }

    // ======================= STATUT MOI =======================

    private void setMyStatus(boolean online) {
        Color  c   = online ? ONLINE_COLOR : OFFLINE_COLOR;
        String txt = online ? "En ligne" : "Hors ligne";
        String bg  = online
                ? "-fx-background-color: rgba(78,205,196,0.2);"
                : "-fx-background-color: rgba(136,136,136,0.2);";

        myStatusDot.setFill(c);
        myStatusDotBadge.setFill(c);
        myStatusLabel.setText(txt);
        myStatusLabel.setStyle(
                "-fx-text-fill: " + toHex(c)
                        + "; -fx-font-size: 10px; -fx-font-weight: bold;");
        myStatusLabel.getParent().setStyle(
                bg + "-fx-background-radius: 20; -fx-padding: 2 8 2 6;");
    }

    // ======================= STATUT CONTACT HEADER =======================

    private void updateContactStatusHeader(String contact) {
        boolean online = onlineUsers.contains(contact);
        Color   c      = online ? ONLINE_COLOR : OFFLINE_COLOR;
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
                    setText(null); setGraphic(null);
                    setStyle("-fx-background-color: transparent;");
                    return;
                }

                boolean online = onlineUsers.contains(user);

                HBox cell = new HBox(10);
                cell.setAlignment(Pos.CENTER_LEFT);
                cell.setPadding(new Insets(8, 10, 8, 8));

                AnchorPane ap = new AnchorPane();
                ap.setPrefSize(40, 40);
                ap.setMinSize(40, 40);

                Circle bg = new Circle(18);
                bg.setFill(online ? Color.web("#A8D8F0") : Color.web("#9B8AB0"));
                AnchorPane.setTopAnchor(bg, 2.0);
                AnchorPane.setLeftAnchor(bg, 2.0);

                Label init = new Label(String.valueOf(user.charAt(0)).toUpperCase());
                init.setStyle("-fx-text-fill: #3D1A47; -fx-font-size: 13px; -fx-font-weight: bold;");
                AnchorPane.setTopAnchor(init, 10.0);
                AnchorPane.setLeftAnchor(init, 10.0);

                Circle dot = new Circle(6);
                dot.setFill(online ? ONLINE_COLOR : OFFLINE_COLOR);
                dot.setStroke(Color.web("#6a3bbf"));
                dot.setStrokeWidth(2);
                AnchorPane.setBottomAnchor(dot, 0.0);
                AnchorPane.setRightAnchor(dot, 0.0);

                ap.getChildren().addAll(bg, init, dot);

                VBox info = new VBox(2);
                HBox.setHgrow(info, Priority.ALWAYS);

                Label name = new Label(user);
                name.setStyle("-fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold;");

                Label statusTxt = new Label(online ? "En ligne" : "Hors ligne");
                statusTxt.setStyle("-fx-text-fill: "
                        + (online ? "#4ECDC4" : "rgba(255,255,255,0.45)")
                        + "; -fx-font-size: 10px;");

                info.getChildren().addAll(name, statusTxt);
                cell.getChildren().addAll(ap, info);

                setGraphic(cell);
                setText(null);
                setStyle("-fx-background-color: "
                        + (isSelected() ? "rgba(255,255,255,0.15)" : "transparent")
                        + "; -fx-background-radius: 10; -fx-padding: 2 0;");
            }
        });

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
            envoyerLecture(selectedContact);
        });
    }

    // ======================= HISTORIQUE (RG8) =======================

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
                afficherBulle(m.getContenu(), isMine, m.getStatut());
            }
        } finally {
            em.close();
        }
    }

    // ======================= ENVOI NOTIFICATION DE LECTURE =======================

    private void envoyerLecture(String contact) {
        if (socket == null || socket.isClosed()) return;
        try {
            bufferedWriter.write("READ|" + username + "|" + contact);
            bufferedWriter.newLine();
            bufferedWriter.flush();
        } catch (IOException e) {
            // ignore
        }
    }

    // ======================= TICKS BLEUS (LU) =======================

    /**
     * ✅ Cible uniquement les bulles ENVOYÉES (CENTER_RIGHT)
     * Appelée quand READ_ACK reçu → le contact a lu nos messages
     */
    private void mettreAJourTicksBleus() {
        for (javafx.scene.Node node : messagesBox.getChildren()) {
            if (node instanceof HBox wrapper) {
                if (wrapper.getAlignment() == Pos.CENTER_RIGHT
                        && wrapper.getChildren().size() == 2) {
                    javafx.scene.Node second = wrapper.getChildren().get(1);
                    if (second instanceof VBox bubble) {
                        for (javafx.scene.Node bubbleChild : bubble.getChildren()) {
                            if (bubbleChild instanceof HBox bottom) {
                                for (javafx.scene.Node tickNode : bottom.getChildren()) {
                                    if (tickNode instanceof Label tick) {
                                        tick.setText("✓✓");
                                        tick.setStyle("-fx-font-size: 11px; -fx-text-fill: #53BDEB;");
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ======================= TICKS DOUBLE GRIS (RECU) =======================

    /**
     * ✅ Passe ✓ → ✓✓ gris sur les bulles envoyées
     * Appelée quand DELIVERED_ACK reçu → le contact vient de se connecter
     */
    private void mettreAJourTicksDouble() {
        for (javafx.scene.Node node : messagesBox.getChildren()) {
            if (node instanceof HBox wrapper) {
                if (wrapper.getAlignment() == Pos.CENTER_RIGHT
                        && wrapper.getChildren().size() == 2) {
                    javafx.scene.Node second = wrapper.getChildren().get(1);
                    if (second instanceof VBox bubble) {
                        for (javafx.scene.Node bubbleChild : bubble.getChildren()) {
                            if (bubbleChild instanceof HBox bottom) {
                                for (javafx.scene.Node tickNode : bottom.getChildren()) {
                                    if (tickNode instanceof Label tick) {
                                        // ✅ Seulement si encore 1 trait (ne pas écraser les bleus)
                                        if (tick.getText().equals("✓")) {
                                            tick.setText("✓✓");
                                            tick.setStyle("-fx-font-size: 11px; -fx-text-fill: #C9B8E8;");
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ======================= ENVOI MESSAGE =======================

    @FXML
    private void sendMessage() {
        if (socket == null || socket.isClosed()) {
            afficherAlerte("Non connecté au serveur !");
            return;
        }
        if (selectedContact == null) {
            afficherAlerte("Sélectionne un contact d'abord !");
            return;
        }
        String content = messageField.getText();
        if (content == null || content.trim().isEmpty()) return;
        if (content.length() > 1000) {
            afficherAlerte("Le message ne doit pas dépasser 1000 caractères !");
            return;
        }

        StatusMessage statut = onlineUsers.contains(selectedContact)
                ? StatusMessage.RECU
                : StatusMessage.ENVOYE;

        try {
            bufferedWriter.write(username + "|" + selectedContact + "|" + content);
            bufferedWriter.newLine();
            bufferedWriter.flush();
            afficherBulle(content, true, statut);
            messageField.clear();
        } catch (IOException e) {
            afficherErreurConnexion();
        }
    }

    // ======================= BULLES STYLE WHATSAPP =======================

    private void afficherBulle(String content, boolean isMine, StatusMessage statut) {

        HBox wrapper = new HBox();
        wrapper.setPadding(new Insets(2, 12, 2, 12));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        VBox bubble = new VBox(3);
        bubble.setMaxWidth(340);
        bubble.setMinWidth(60);

        Label txtLabel = new Label(content);
        txtLabel.setWrapText(true);
        txtLabel.setMaxWidth(310);
        txtLabel.setPadding(new Insets(0, 0, 2, 0));

        if (isMine) {
            bubble.setPadding(new Insets(8, 14, 6, 14));
            bubble.setStyle(
                    "-fx-background-color: " + BUBBLE_SENT + ";" +
                            "-fx-background-radius: 18 4 18 18;" +
                            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.18), 4, 0, 1, 2);");
            txtLabel.setStyle(
                    "-fx-text-fill: " + BUBBLE_SENT_TXT + ";" +
                            "-fx-font-size: 13px;");

            HBox bottom = new HBox(3);
            bottom.setAlignment(Pos.CENTER_RIGHT);

            Label tick = new Label(getTickSymbol(statut));
            tick.setStyle(
                    "-fx-font-size: 11px;" +
                            "-fx-text-fill: " + getTickColor(statut) + ";");
            bottom.getChildren().add(tick);

            bubble.getChildren().addAll(txtLabel, bottom);

            wrapper.setAlignment(Pos.CENTER_RIGHT);
            wrapper.getChildren().addAll(spacer, bubble);

        } else {
            bubble.setPadding(new Insets(8, 14, 8, 14));
            bubble.setStyle(
                    "-fx-background-color: " + BUBBLE_RECV + ";" +
                            "-fx-background-radius: 4 18 18 18;" +
                            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.12), 4, 0, 1, 2);");
            txtLabel.setStyle(
                    "-fx-text-fill: " + BUBBLE_RECV_TXT + ";" +
                            "-fx-font-size: 13px;");

            bubble.getChildren().add(txtLabel);

            wrapper.setAlignment(Pos.CENTER_LEFT);
            wrapper.getChildren().addAll(bubble, spacer);
        }

        messagesBox.getChildren().add(wrapper);
        Platform.runLater(() -> messagesScroll.setVvalue(1.0));
    }

    // ======================= INDICATEURS WHATSAPP =======================

    /**
     * ✓     gris  = ENVOYE  (destinataire offline)
     * ✓✓    gris  = RECU    (destinataire a reçu)
     * ✓✓    bleu  = LU      (destinataire a lu)
     */
    private String getTickSymbol(StatusMessage statut) {
        if (statut == null) return "✓";
        return switch (statut) {
            case ENVOYE -> "✓";
            case RECU   -> "✓✓";
            case LU     -> "✓✓";
        };
    }

    private String getTickColor(StatusMessage statut) {
        if (statut == null) return "#999999";
        return switch (statut) {
            case ENVOYE -> "#C9B8E8";
            case RECU   -> "#C9B8E8";
            case LU     -> "#53BDEB";
        };
    }

    // ======================= LOGOUT =======================

    @FXML
    private void logout() {
        disconnect();
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/org/example/chatapp/hello-view.fxml"));
            Stage stage = (Stage) logoutButton.getScene().getWindow();
            stage.setOnCloseRequest(e -> {
                javafx.application.Platform.exit();
                System.exit(0);
            });
            stage.setScene(new Scene(loader.load()));
            stage.setTitle("FBChat - Connexion");
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