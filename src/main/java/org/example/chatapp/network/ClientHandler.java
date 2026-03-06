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
            this.socket         = socket;
            this.bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            this.bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Lire le username envoyé par le client en premier
            this.clientUsername = bufferedReader.readLine();

            // RG4 : passer ONLINE à la connexion
            updateUserStatus(clientUsername, Status.ONLINE);

            // Ajouter dans la liste des clients connectés
            clientHandlers.add(this);

            // RG12 : log connexion
            System.out.println("[LOG] " + clientUsername + " connecté.");

            // Envoyer les messages en attente (RG6)
            deliverPendingMessages();

            // UN SEUL broadcast de la liste — notifie tout le monde
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

                if (messageFromClient == null) break; // client déconnecté proprement

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

        String sender   = parts[0];
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

        // Sauvegarder en base
        saveMessage(sender, receiver, content);

        // Chercher si le destinataire est connecté et lui envoyer
        boolean destinataireConnecte = false;
        synchronized (clientHandlers) {
            for (ClientHandler ch : clientHandlers) {
                if (ch.clientUsername.equals(receiver)) {
                    try {
                        ch.bufferedWriter.write(sender + "|" + content);
                        ch.bufferedWriter.newLine();
                        ch.bufferedWriter.flush();
                        destinataireConnecte = true;
                    } catch (IOException e) {
                        System.err.println("[ERREUR] Impossible d'envoyer à " + receiver);
                    }
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
                            "SELECT m FROM Message m " +
                                    "WHERE m.receiver.username = :username " +
                                    "AND m.statut = :statut " +
                                    "ORDER BY m.dateEnvoi ASC",
                            Message.class)
                    .setParameter("username", clientUsername)
                    .setParameter("statut", StatusMessage.ENVOYE)
                    .getResultList();

            if (!pending.isEmpty()) {
                System.out.println("[INFO] Livraison de " + pending.size()
                        + " message(s) en attente à " + clientUsername);
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
                    System.err.println("[ERREUR] Livraison message en attente échouée.");
                }
            }
        } finally {
            em.close();
        }
    }

    // ===================== BROADCAST LISTE USERS =====================

    public static void broadcastUserList() {

        // ✅ Copie snapshot pour éviter ConcurrentModificationException
        List<ClientHandler> snapshot = new ArrayList<>(clientHandlers);

        // Construire le message avec uniquement les connectés
        StringBuilder sb = new StringBuilder("USERLIST|");
        for (ClientHandler ch : snapshot) {
            sb.append(ch.clientUsername).append(",");
        }

        String msg = sb.toString();
        if (msg.endsWith(",")) {
            msg = msg.substring(0, msg.length() - 1);
        }

        // ✅ Envoyer à tous, retirer silencieusement les sockets fermés
        List<ClientHandler> aRetirer = new ArrayList<>();

        for (ClientHandler ch : snapshot) {
            try {
                if (ch.socket != null && !ch.socket.isClosed() && ch.bufferedWriter != null) {
                    ch.bufferedWriter.write(msg);
                    ch.bufferedWriter.newLine();
                    ch.bufferedWriter.flush();
                } else {
                    aRetirer.add(ch);
                }
            } catch (IOException e) {
                // Socket fermé → on le retire silencieusement sans log d'erreur
                aRetirer.add(ch);
            }
        }

        // Nettoyer les clients dont le socket est fermé
        clientHandlers.removeAll(aRetirer);
    }

    // ===================== DÉCONNEXION =====================

    public void removeClientHandler() {
        // ✅ Retire le client AVANT de broadcaster
        clientHandlers.remove(this);

        // RG4 : passer OFFLINE en base
        updateUserStatus(clientUsername, Status.OFFLINE);

        // RG12 : log déconnexion
        System.out.println("[LOG] " + clientUsername + " déconnecté.");

        // ✅ Notifie les clients restants (ce client est déjà retiré)
        broadcastUserList();
    }

    public void closeEverything(Socket socket, BufferedReader bufferedReader, BufferedWriter bufferedWriter) {
        // ✅ Appelle removeClientHandler() une seule fois
        if (clientHandlers.contains(this)) {
            removeClientHandler();
        }
        try {
            if (bufferedReader != null) bufferedReader.close();
            if (bufferedWriter != null) bufferedWriter.close();
            if (socket         != null) socket.close();
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
                users.get(0).setStatus(status);
            } else {
                System.out.println("[WARN] Utilisateur introuvable : " + username);
            }

            em.getTransaction().commit();
        } finally {
            em.close();
        }
    }

    private void saveMessage(String senderUsername, String receiverUsername, String content) {
        EntityManager em = JPAUtil.getEntityManagerFactory().createEntityManager();
        try {
            List<User> senderList = em.createQuery(
                            "SELECT u FROM User u WHERE u.username = :username", User.class)
                    .setParameter("username", senderUsername)
                    .getResultList();

            List<User> receiverList = em.createQuery(
                            "SELECT u FROM User u WHERE u.username = :username", User.class)
                    .setParameter("username", receiverUsername)
                    .getResultList();

            if (senderList.isEmpty() || receiverList.isEmpty()) {
                System.out.println("[ERREUR] saveMessage — sender ou receiver introuvable.");
                return;
            }

            User sender   = senderList.get(0);
            User receiver = receiverList.get(0);

            // Déterminer le statut selon si le destinataire est connecté
            boolean receiverOnline = clientHandlers.stream()
                    .anyMatch(ch -> ch.clientUsername.equals(receiverUsername));

            StatusMessage statut = receiverOnline ? StatusMessage.RECU : StatusMessage.ENVOYE;

            // Créer et persister le message
            Message message = new Message();
            message.setSender(sender);
            message.setReceiver(receiver);
            message.setContenu(content);
            message.setDateEnvoi(LocalDateTime.now());
            message.setStatut(statut);

            em.getTransaction().begin();
            em.persist(message);
            em.getTransaction().commit();

            System.out.println("[DB] Message sauvegardé : " + senderUsername
                    + " → " + receiverUsername + " [" + statut + "]");

        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            System.err.println("[ERREUR] saveMessage : " + e.getMessage());
        } finally {
            em.close();
        }
    }
}