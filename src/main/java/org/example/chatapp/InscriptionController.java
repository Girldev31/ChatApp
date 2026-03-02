package org.example.chatapp;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;

import java.io.IOException;

public class InscriptionController {

    @FXML
    private TextField fullname_input;

    @FXML
    private TextField email_input;

    @FXML
    private TextField username_input;

    @FXML
    private PasswordField password_input;

    @FXML
    private PasswordField confirm_password_input;

    @FXML
    private void register() {
        String fullname = fullname_input.getText();
        String email = email_input.getText();
        String username = username_input.getText();
        String password = password_input.getText();
        String confirm = confirm_password_input.getText();

        if (fullname.isEmpty() || email.isEmpty() || username.isEmpty() || password.isEmpty() || confirm.isEmpty()) {
            System.out.println("Tous les champs doiventetreremplis !");
            return;
        }

        if (!password.equals(confirm)) {
            System.out.println("Les mots de passe ne correspondent pas !");
            return;
        }


        System.out.println("Inscription réussie pour : " + username);


        goToLogin();
    }


    @FXML
    private void goToLogin() {
        try {

            FXMLLoader loader = new FXMLLoader(getClass().getResource("hello-view.fxml"));


            AnchorPane root = loader.load();

            Stage stage = (Stage) fullname_input.getScene().getWindow();
            stage.setScene(new Scene(root));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}