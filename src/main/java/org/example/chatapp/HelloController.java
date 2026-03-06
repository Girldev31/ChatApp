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

    @FXML private PasswordField password_input;
    @FXML private TextField username_input;

    // ======================= LOGIN =======================

    @FXML
    void login(ActionEvent event) {

        String username = username_input.getText().trim();
        String password = password_input.getText();

        // ── Champs vides ─────────────────────────────────────────────────────
        if (username.isEmpty() || password.isEmpty()) {
            afficherAlerte(Alert.AlertType.WARNING,
                    "Champs manquants", "Veuillez remplir tous les champs !");
            return;
        }

        // ── Vérification en base de données ──────────────────────────────────
        EntityManager em = JPAUtil.getEntityManagerFactory().createEntityManager();
        try {

            List<User> users = em.createQuery(
                            "SELECT u FROM User u WHERE u.username = :username", User.class)
                    .setParameter("username", username)
                    .getResultList();

            // Username introuvable
            if (users.isEmpty()) {
                afficherAlerte(Alert.AlertType.ERROR,
                        "Erreur", "Nom d'utilisateur introuvable. Vérifiez votre username.");
                return;
            }

            User user = users.get(0);

            // Vérifier le mot de passe hashé (SHA-256)
            String hashedInput = hashPassword(password);
            if (!hashedInput.equals(user.getPassword())) {
                afficherAlerte(Alert.AlertType.ERROR,
                        "Erreur", "Mot de passe incorrect !");
                return;
            }

            // ── Authentification réussie → ouvrir le chat ────────────────────
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/org/example/chatapp/chat_view.fxml"));
            AnchorPane root = loader.load();

            ChatController chatController = loader.getController();
            chatController.setUsername(username);

            Stage stage = (Stage) username_input.getScene().getWindow();

            // ✅ Fermeture de la fenêtre → déconnexion propre (RG4 + RG10)
            stage.setOnCloseRequest(e -> chatController.disconnect());

            stage.setScene(new Scene(root));
            stage.setTitle("Chat - " + username);
            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            em.close();
        }
    }

    // ======================= INSCRIPTION =======================

    @FXML
    void goToRegister(MouseEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("inscription-view.fxml"));
            AnchorPane root = loader.load();
            Stage stage = (Stage) username_input.getScene().getWindow();
            stage.setScene(new Scene(root));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ======================= UTILS =======================

    /**
     * Hash SHA-256 — même algorithme que InscriptionController.
     */
    private String hashPassword(String plain) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(plain.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return plain; // fallback
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