package org.example.chatapp;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

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

    private String username;

    // Méthode pour initialiser le nom d'utilisateur depuis HelloController
    public void setUsername(String username) {
        this.username = username;
        usernameLabel.setText(username);
    }

    // Méthode appelée quand l'utilisateur clique sur "Envoyer"
    @FXML
    private void sendMessage() {
        String message = messageField.getText();
        if (message != null && !message.isEmpty()) {
            System.out.println(username + " : " + message);

            // Ici tu peux ajouter le message à messagesBox pour l'affichage visuel
            // Exemple simple avec un Label :
            javafx.scene.control.Label msgLabel = new javafx.scene.control.Label(username + " : " + message);
            messagesBox.getChildren().add(msgLabel);

            messageField.clear();
        }
    }
}