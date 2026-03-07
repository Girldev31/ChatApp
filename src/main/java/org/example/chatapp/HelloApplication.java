package org.example.chatapp;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.chatapp.utils.JPAUtil;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import java.io.IOException;

public class HelloApplication extends Application {

    // ✅ Référence statique au ChatController actif
    public static ChatController activeChatController = null;

    @Override
    public void start(Stage stage) throws IOException {

        try {
            EntityManagerFactory emf = JPAUtil.getEntityManagerFactory();
            EntityManager em = emf.createEntityManager();
            System.out.println("✅ Connexion à la base réussie !");
            em.close();
        } catch (Exception e) {
            System.out.println("❌ Erreur de connexion !");
            e.printStackTrace();
        }

        FXMLLoader fxmlLoader = new FXMLLoader(
                HelloApplication.class.getResource("hello-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 620, 440);
        stage.setTitle("FBChat - Connexion");
        stage.setScene(scene);
        stage.setResizable(false);

        // ✅ Fermeture fenêtre → déconnexion propre avant de quitter
        stage.setOnCloseRequest(e -> {
            if (activeChatController != null) {
                activeChatController.disconnect();
            }
            Platform.exit();
            System.exit(0);
        });

        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}