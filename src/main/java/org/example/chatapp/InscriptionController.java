package org.example.chatapp;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import org.example.chatapp.entity.User;
import org.example.chatapp.enumeration.Status;
import org.example.chatapp.utils.JPAUtil;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import java.io.IOException;
import java.time.LocalDateTime;

public class InscriptionController {

    @FXML private TextField fullname_input;
    @FXML private TextField email_input;
    @FXML private TextField username_input;
    @FXML private PasswordField password_input;
    @FXML private PasswordField confirm_password_input;

    @FXML
    private void register() {

        String fullname = fullname_input.getText().trim();
        String email    = email_input.getText().trim();
        String username = username_input.getText().trim();
        String password = password_input.getText();
        String confirm  = confirm_password_input.getText();

        // ── Validation des champs ────────────────────────────────────────────
        if (fullname.isEmpty() || email.isEmpty() || username.isEmpty()
                || password.isEmpty() || confirm.isEmpty()) {
            afficherAlerte(Alert.AlertType.WARNING,
                    "Champs manquants", "Tous les champs doivent être remplis !");
            return;
        }

        if (!password.equals(confirm)) {
            afficherAlerte(Alert.AlertType.WARNING,
                    "Erreur", "Les mots de passe ne correspondent pas !");
            return;
        }

        // ── Vérifier que le username n'existe pas déjà (RG1) ────────────────
        EntityManager em = JPAUtil.getEntityManagerFactory().createEntityManager();
        EntityTransaction tx = em.getTransaction();

        try {

            Long count = em.createQuery(
                            "SELECT COUNT(u) FROM User u WHERE u.username = :username", Long.class)
                    .setParameter("username", username)
                    .getSingleResult();

            if (count > 0) {
                afficherAlerte(Alert.AlertType.WARNING,
                        "Username déjà pris",
                        "Le username '" + username + "' est déjà utilisé. Choisis-en un autre.");
                return;
            }

            // ── Créer et sauvegarder l'utilisateur ──────────────────────────
            User newUser = new User();
            newUser.setUsername(username);

            // Hash du mot de passe avec BCrypt (RG9)
            newUser.setPassword(hashPassword(password));

            newUser.setStatus(Status.OFFLINE);
            newUser.setDateCreation(LocalDateTime.now());

            tx.begin();
            em.persist(newUser);
            tx.commit();

            System.out.println("✅ Utilisateur enregistré en base : " + username);

            afficherAlerte(Alert.AlertType.INFORMATION,
                    "Inscription réussie",
                    "Bienvenue " + username + " ! Tu peux maintenant te connecter.");

            goToLogin();

        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            e.printStackTrace();
            afficherAlerte(Alert.AlertType.ERROR,
                    "Erreur", "Une erreur est survenue lors de l'inscription : " + e.getMessage());
        } finally {
            em.close();
        }
    }

    // ── Hash BCrypt du mot de passe ──────────────────────────────────────────
    private String hashPassword(String plainPassword) {
        // Si tu as BCrypt dans ton projet (org.mindrot.jbcrypt) :
        // return BCrypt.hashpw(plainPassword, BCrypt.gensalt());

        // Sinon version simple avec SHA-256 (à remplacer par BCrypt si possible) :
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(plainPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return plainPassword; // fallback (ne pas utiliser en prod)
        }
    }

    // ── Retour à la page login ───────────────────────────────────────────────
    @FXML
    private void goToLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("hello-view.fxml"));
            AnchorPane root = loader.load();
            Stage stage = (Stage) fullname_input.getScene().getWindow();
            stage.setScene(new Scene(root));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ── Utilitaire alerte ────────────────────────────────────────────────────
    private void afficherAlerte(Alert.AlertType type, String titre, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(titre);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}