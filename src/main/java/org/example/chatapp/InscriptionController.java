package org.example.chatapp;

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
import org.example.chatapp.enumeration.Status;
import org.example.chatapp.utils.JPAUtil;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;

public class InscriptionController {

    @FXML private TextField fullname_input;
    @FXML private TextField email_input;
    @FXML private TextField username_input;
    @FXML private PasswordField password_input;
    @FXML private PasswordField confirm_password_input;

    // ===================== INSCRIPTION =====================

    @FXML
    private void register() {
        String fullname  = fullname_input.getText().trim();
        String email     = email_input.getText().trim();
        String username  = username_input.getText().trim();
        String password  = password_input.getText();
        String confirm   = confirm_password_input.getText();

        // Validation champs vides
        if (fullname.isEmpty() || email.isEmpty() || username.isEmpty()
                || password.isEmpty() || confirm.isEmpty()) {
            afficherAlerte(Alert.AlertType.WARNING,
                    "Champs manquants", "Tous les champs doivent être remplis !");
            return;
        }

        // Validation mot de passe
        if (!password.equals(confirm)) {
            afficherAlerte(Alert.AlertType.WARNING,
                    "Erreur", "Les mots de passe ne correspondent pas !");
            return;
        }

        // Validation complexité mot de passe
        if (!isPasswordValid(password)) {
            afficherAlerte(Alert.AlertType.WARNING, "Mot de passe faible",
                    "Le mot de passe doit contenir au moins :\n" +
                            "• 1 chiffre\n• 1 lettre\n• 1 caractère spécial (!@#$%^&*)");
            return;
        }

        EntityManager em = JPAUtil.getEntityManagerFactory().createEntityManager();
        EntityTransaction tx = em.getTransaction();

        try {
            // RG1 : username unique
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

            // RG9 : hash du mot de passe
            User newUser = new User();
            newUser.setUsername(username);
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
                    "Erreur", "Une erreur est survenue : " + e.getMessage());
        } finally {
            em.close();
        }
    }

    // ===================== RETOUR AU LOGIN =====================

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

    // ===================== UTILS =====================

    private boolean isPasswordValid(String password) {
        boolean hasLetter  = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit   = password.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = password.chars().anyMatch(c ->
                "!@#$%^&*".indexOf(c) >= 0);
        return hasLetter && hasDigit && hasSpecial;
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