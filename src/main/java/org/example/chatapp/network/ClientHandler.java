package org.example.chatapp.network;

import org.example.chatapp.entity.Message;
import org.example.chatapp.entity.User;
import org.example.chatapp.enumeration.Status;
import org.example.chatapp.enumeration.StatusMessage;
import org.example.chatapp.utils.JPAUtil;

import javax.persistence.EntityManager;
import java.io.*;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ClientHandler implements Runnable {

    public static List<ClientHandler> clientHandlers =
            Collections.synchronizedList(new ArrayList<>());

    private Socket socket;
    private BufferedReader bufferedReader;
    private BufferedWriter bufferedWriter;
    private String clientUsername;

    // ===================== CONSTRUCTEUR =====================

    public ClientHandler(Socket socket) {
        try {
            this.socket = socket;
            this.bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            this.bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Lire le username envoyé par le client en premier
            this.clientUsername = bufferedReader.readLine();

            // RG4 : passer ONLINE à la connexion
            updateUserStatus(clientUsername, Status.ONLINE);

            // Ajouter dans la liste des clients connectés
            clientHandlers.add(this);
            sendUserListToAllClients();


            // RG12 : log connexion
            System.out.println("[LOG] " + clientUsername + " connecté.");

            // Envoyer les messages en attente (RG6)
            deliverPendingMessages();

            // Notifier tous les clients de la nouvelle liste
            broadcastUserList();

        } catch (IOException e) {
            closeEverything(socket, bufferedReader, bufferedWriter);
        }
    }

    // ===================== RUN =====================

    @Override
    public void run() {
        String messageFromClient;

        while (socket.isConnected()) {
            try {
                messageFromClient = bufferedReader.readLine();

                // null = client déconnecté proprement
                if (messageFromClient == null) break;

                handlePrivateMessage(messageFromClient);

            } catch (IOException e) {
                closeEverything(socket, bufferedReader, bufferedWriter);
                break;
            }
        }
    }

    // ===================== GESTION DES MESSAGES =====================

    private void handlePrivateMessage(String message) {
        // Format attendu : "sender|receiver|content"
        String[] parts = message.split("\\|", 3);

        if (parts.length < 3) {
            System.err.println("[ERREUR] Message mal formaté : " + message);
            return;
        }

        String sender  = parts[0];
        String receiver = parts[1];
        String content  = parts[2];

        // RG7 : validation contenu
        if (content == null || content.trim().isEmpty()) {
            System.err.println("[ERREUR] Message vide ignoré.");
            return;
        }
        if (content.length() > 1000) {
            System.err.println("[ERREUR] Message trop long ignoré.");
            return;
        }

        // Sauvegarder en base dans tous les cas
        saveMessage(sender, receiver, content);

        // Chercher si le destinataire est connecté
        boolean destinataireConnecte = false;
        for (ClientHandler clientHandler : clientHandlers) {
            if (clientHandler.clientUsername.equals(receiver)) {
                try {
                    clientHandler.bufferedWriter.write(sender + "|" + content);
                    clientHandler.bufferedWriter.newLine();
                    clientHandler.bufferedWriter.flush();
                    destinataireConnecte = true;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        // RG6 : message déjà sauvegardé, sera livré à la reconnexion
        if (!destinataireConnecte) {
            System.out.println("[INFO] " + receiver + " est offline — message sauvegardé.");
        }

        // RG12 : journalisation
        System.out.println("[LOG] " + sender + " → " + receiver + " : " + content);
    }

    // ===================== MESSAGES EN ATTENTE (RG6) =====================

    private void deliverPendingMessages() {
        EntityManager em = JPAUtil.getEntityManagerFactory().createEntityManager();
        try {
            List<Message> pending = em.createQuery(
                            "SELECT m FROM org.example.chatapp.entity.Message m " +
                                    "WHERE m.receiver.username = :username " +
                                    "AND m.statut = :statut " +
                                    "ORDER BY m.dateEnvoi ASC",
                            Message.class)
                    .setParameter("username", clientUsername)
                    .setParameter("statut", StatusMessage.ENVOYE)
                    .getResultList();

            if (!pending.isEmpty()) {
                System.out.println("[INFO] Livraison de " + pending.size() + " message(s) en attente à " + clientUsername);
            }

            for (Message m : pending) {
                try {
                    bufferedWriter.write(m.getSender().getUsername() + "|" + m.getContenu());
                    bufferedWriter.newLine();
                    bufferedWriter.flush();

                    // Mettre à jour le statut en RECU
                    em.getTransaction().begin();
                    m.setStatut(StatusMessage.RECU);
                    em.getTransaction().commit();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } finally {
            em.close();
        }
    }

    // ===================== BROADCAST LISTE USERS =====================

    public static void broadcastUserList() {
        // Construire la liste : "USERLIST|alice,bob,charlie,"
        StringBuilder userList = new StringBuilder("USERLIST|");
        for (ClientHandler ch : clientHandlers) {
            userList.append(ch.clientUsername).append(",");
        }
        String msg = userList.toString();

        // Envoyer à tous les clients connectés
        for (ClientHandler ch : clientHandlers) {
            try {
                ch.bufferedWriter.write(msg);
                ch.bufferedWriter.newLine();
                ch.bufferedWriter.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // ===================== DÉCONNEXION =====================

    public void removeClientHandler() {
        clientHandlers.remove(this);

        // RG4 : passer OFFLINE
        updateUserStatus(clientUsername, Status.OFFLINE);

        // Notifier tous les clients
        broadcastUserList();

        // RG12 : log déconnexion
        System.out.println("[LOG] " + clientUsername + " déconnecté.");
    }

    public void closeEverything(Socket socket, BufferedReader bufferedReader, BufferedWriter bufferedWriter) {
        removeClientHandler();
        try {
            if (bufferedReader != null) bufferedReader.close();
            if (bufferedWriter != null) bufferedWriter.close();
            if (socket != null)         socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ===================== BASE DE DONNÉES =====================

    private void updateUserStatus(String username, Status status) {
        EntityManager em = JPAUtil.getEntityManagerFactory().createEntityManager();
        try {
            em.getTransaction().begin();

            List<User> users = em.createQuery(
                            "SELECT u FROM User u WHERE u.username = :username", User.class)
                    .setParameter("username", username)
                    .getResultList();

            if (!users.isEmpty()) {
                User user = users.get(0);
                user.setStatus(status);
            } else {
                System.out.println("[WARN] Utilisateur introuvable : " + username);
            }

            em.getTransaction().commit();
        } finally {
            em.close();
        }
    }

    private void sendUserListToAllClients(){

        StringBuilder userList = new StringBuilder("USERLIST|");

        synchronized (clientHandlers){
            for(ClientHandler ch : clientHandlers){
                userList.append(ch.clientUsername).append(",");
            }
        }

        for(ClientHandler ch : clientHandlers){
            try{
                ch.bufferedWriter.write(userList.toString());
                ch.bufferedWriter.newLine();
                ch.bufferedWriter.flush();
            }catch(IOException e){
                e.printStackTrace();
            }
        }
    }

    private void saveMessage(String sender, String receiver, String content) {
        EntityManager em = JPAUtil.getEntityManagerFactory().createEntityManager();
        try {
            em.getTransaction().begin();

            List<User> senderList = em.createQuery(
                            "SELECT u FROM User u WHERE u.username = :username", User.class)
                    .setParameter("username", sender)
                    .getResultList();

            List<User> receiverList = em.createQuery(
                            "SELECT u FROM User u WHERE u.username = :username", User.class)
                    .setParameter("username", receiver)
                    .getResultList();

            if(senderList.isEmpty() || receiverList.isEmpty()){
                System.out.println("[ERREUR] Sender ou Receiver introuvable.");
                em.getTransaction().rollback();
                return;
            }

            User senderUser = senderList.get(0);
            User receiverUser = receiverList.get(0);

        } finally {
            em.close();
        }
    }
}