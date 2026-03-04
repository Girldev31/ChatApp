package org.example.chatapp;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import org.example.chatapp.entity.Message;
import org.example.chatapp.utils.JPAUtil;
import java.util.List;
import javax.persistence.EntityManager;
import javafx.scene.paint.Color;
import java.awt.*;
import java.io.*;
import java.net.Socket;

public class ChatController {

    // Liste des contacts (facultatif pour ton chat)
    @FXML
    private ListView<String> contactsList;

    // Champ où l'utilisateur tape son message
    @FXML
    private TextField messageField;

    // Boîte pour afficher les messages
    @FXML
    private VBox messagesBox;

    // Label pour afficher le nom de l'utilisateur connecté
    @FXML
    private Label usernameLabel;

    private String username;// utilisateur connecté
    private String selectedContact;    // contact sélectionné dans la liste

    private Socket socket;
    private BufferedReader bufferedReader;
    private BufferedWriter bufferedWriter;

    // Méthode pour initialiser le nom d'utilisateur depuis HelloController
    public void setUsername(String username) {
        this.username = username;
        connectToServer();
        setupContactClickListener();
    }

    private void connectToServer() {
        try {
            socket = new Socket("localhost", 1234);
            bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Envoyer le username au serveur (ClientHandler l'attend en premier)
            bufferedWriter.write(username);
            bufferedWriter.newLine();
            bufferedWriter.flush();

            // Lancer l'écoute des messages entrants
            startListening();

        } catch (IOException e) {
            afficherErreurConnexion();
        }
    }

    private void startListening() {
        Thread listenerThread = new Thread(() -> {
            try {
                String messageFromServer;
                while ((messageFromServer = bufferedReader.readLine()) != null) {
                    final String msg = messageFromServer;

                    if (msg.startsWith("USERLIST|")) {
                        // Mise à jour de la liste des contacts
                        Platform.runLater(() -> updateContactsList(msg));
                    } else {
                        // Message normal : format "sender|content"
                        String[] parts = msg.split("\\|", 2);
                        if (parts.length == 2) {
                            String sender = parts[0];
                            String content = parts[1];
                            // Afficher seulement si c'est la conversation ouverte
                            if (sender.equals(selectedContact)) {
                                Platform.runLater(() -> afficherBulle(content, false));
                            }
                        }
                    }
                }
            } catch (IOException e) {
                Platform.runLater(this::afficherErreurConnexion); // RG10
            }
        });
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    private void updateContactsList(String userListMessage) {
        // Format : "USERLIST|alice,bob,charlie,"
        String raw = userListMessage.replace("USERLIST|", "");
        String[] users = raw.split(",");

        contactsList.getItems().clear();
        for (String user : users) {
            if (!user.isEmpty() && !user.equals(username)) {
                contactsList.getItems().add(user);
            }
        }
    }

    private void setupContactClickListener() {
        contactsList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String user, boolean empty) {
                super.updateItem(user, empty);
                if (empty || user == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    // Style de chaque cellule contact
                    HBox cell = new HBox(10);
                    cell.setAlignment(Pos.CENTER_LEFT);
                    cell.setPadding(new Insets(8, 12, 8, 12));

                    Label avatar = new Label("👤");
                    avatar.setFont(Font.font(20));

                    Label name = new Label(user);
                    name.setTextFill(Color.WHITE);
                    name.setFont(Font.font(14));

                    cell.getChildren().addAll(avatar, name);
                    setGraphic(cell);
                    setStyle("-fx-background-color: transparent;");
                }
            }
        });

        // Clic sur un contact → charger la conversation
        contactsList.setOnMouseClicked(event -> {
            String clicked = contactsList.getSelectionModel().getSelectedItem();
            if (clicked != null && !clicked.equals(selectedContact)) {
                selectedContact = clicked;
                messagesBox.getChildren().clear();
                chargerHistorique(selectedContact);
            }
        });
    }


    private void chargerHistorique(String contact) {
        EntityManager em = JPAUtil.getEntityManagerFactory().createEntityManager();
        try {
            // RG8 : ordre chronologique
            List<Message> messages = em.createQuery(
                            "SELECT m FROM Message m " +
                                    "WHERE (m.sender.username = :me AND m.receiver.username = :contact) " +
                                    "OR (m.sender.username = :contact AND m.receiver.username = :me) " +
                                    "ORDER BY m.dateEnvoi ASC",
                            Message.class)
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

    // Méthode appelée quand l'utilisateur clique sur "Envoyer"
    @FXML
    private void sendMessage() {
        String content = messageField.getText();

        if (selectedContact == null) {
            afficherAlerte("Sélectionne un contact d'abord !");
            return;
        }

        // RG7 : validation
        if (content == null || content.trim().isEmpty()) return;
        if (content.length() > 1000) {
            afficherAlerte("Message trop long (max 1000 caractères)");
            return;
        }

        try {
            // Format : "sender|receiver|content"
            bufferedWriter.write(username + "|" + selectedContact + "|" + content);
            bufferedWriter.newLine();
            bufferedWriter.flush();

            // Afficher ma bulle immédiatement
            afficherBulle(content, true);
            messageField.clear();

        } catch (IOException e) {
            afficherErreurConnexion(); // RG10
        }
    }

    private void afficherBulle(String content, boolean isMine) {
        HBox wrapper = new HBox();
        wrapper.setPadding(new Insets(2, 10, 2, 10));

        Label bulle = new Label(content);
        bulle.setWrapText(true);
        bulle.setMaxWidth(400);
        bulle.setPadding(new Insets(10, 15, 10, 15));
        bulle.setFont(Font.font(13));

        if (isMine) {
            // Ma bulle → droite, violet
            bulle.setStyle("-fx-background-color: #8a4fff; " +
                    "-fx-text-fill: white; " +
                    "-fx-background-radius: 18 18 4 18;");
            wrapper.setAlignment(Pos.CENTER_RIGHT);
        } else {
            // Bulle reçue → gauche, gris clair
            bulle.setStyle("-fx-background-color: #f0ebff; " +
                    "-fx-text-fill: #333; " +
                    "-fx-background-radius: 18 18 18 4;");
            wrapper.setAlignment(Pos.CENTER_LEFT);
        }

        wrapper.getChildren().add(bulle);
        messagesBox.getChildren().add(wrapper);
    }

    private void afficherErreurConnexion() {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Connexion perdue");
        alert.setContentText("La connexion au serveur a été perdue. Vous êtes hors ligne.");
        alert.show();
    }

    private void afficherAlerte(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setContentText(message);
        alert.show();
    }
}