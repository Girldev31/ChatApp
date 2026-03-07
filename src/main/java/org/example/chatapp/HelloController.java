package org.example.chatapp;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import org.example.chatapp.entity.User;
import org.example.chatapp.utils.JPAUtil;

import javax.persistence.EntityManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

public class HelloController {

    @FXML private TextField     username_input;
    @FXML private PasswordField password_input;

    @FXML
    void login(ActionEvent event) {
        String username = username_input.getText().trim();
        String password = password_input.getText();

        if (username.isEmpty() || password.isEmpty()) {
            afficherAlerte(Alert.AlertType.WARNING,
                    "Champs manquants", "Veuillez remplir tous les champs !");
            return;
        }

        EntityManager em = JPAUtil.getEntityManagerFactory().createEntityManager();
        try {
            List<User> users = em.createQuery(
                            "SELECT u FROM User u WHERE u.username = :username", User.class)
                    .setParameter("username", username)
                    .getResultList();

            if (users.isEmpty()) {
                afficherAlerte(Alert.AlertType.ERROR,
                        "Erreur", "Nom d'utilisateur introuvable.");
                return;
            }

            if (!hashPassword(password).equals(users.get(0).getPassword())) {
                afficherAlerte(Alert.AlertType.ERROR,
                        "Erreur", "Mot de passe incorrect !");
                return;
            }

            // ✅ Ouvrir le chat
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/org/example/chatapp/chat_view.fxml"));
            AnchorPane root = loader.load();

            ChatController chatController = loader.getController();
            chatController.setUsername(username);

            // ✅ Enregistrer le controller actif globalement
            HelloApplication.activeChatController = chatController;

            Stage stage = (Stage) username_input.getScene().getWindow();

            // ✅ setOnCloseRequest sur la nouvelle scène aussi
            stage.setOnCloseRequest(e -> {
                if (HelloApplication.activeChatController != null) {
                    HelloApplication.activeChatController.disconnect();
                }
                javafx.application.Platform.exit();
                System.exit(0);
            });

            stage.setScene(new Scene(root));
            stage.setTitle("FBChat - " + username);
            stage.setResizable(false);
            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            em.close();
        }
    }

    @FXML
    void goToRegister(MouseEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("inscription-view.fxml"));
            AnchorPane root = loader.load();
            Stage stage = (Stage) username_input.getScene().getWindow();

            // ✅ Sur la page login, pas de chatController actif
            HelloApplication.activeChatController = null;
            stage.setOnCloseRequest(e -> {
                javafx.application.Platform.exit();
                System.exit(0);
            });

            stage.setScene(new Scene(root));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String hashPassword(String plain) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(plain.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return plain;
        }
    }

    private void afficherAlerte(Alert.AlertType type, String titre, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(titre);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}