package org.example.chatapp;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;

import java.io.IOException;

public class HelloController {




    @FXML
    private PasswordField password_input;

    @FXML
    private TextField username_input;

    @FXML
    void login(ActionEvent event) {
        String username = username_input.getText();
        String password = password_input.getText();

        if (username.isEmpty() || password.isEmpty()) {
            System.out.println("Veuillez remplir tous les champs !");
            return;
        }


        System.out.println("Connexion réussie pour : " + username);

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/chatapp/chat_view.fxml"));
            AnchorPane root = loader.load();

            Stage stage = (Stage) username_input.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Chat");
            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    @FXML
    void goToRegister(MouseEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("inscription-view.fxml"));
            AnchorPane root = loader.load();
            Stage stage = (Stage) username_input.getScene().getWindow();
            stage.setScene(new Scene(root));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
