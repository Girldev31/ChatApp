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
            this.socket        = socket;
            this.bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            this.bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            this.clientUsername = bufferedReader.readLine();

            kickIfAlreadyConnected(clientUsername);
            updateUserStatus(clientUsername, Status.ONLINE);
            clientHandlers.add(this);

            System.out.println("[LOG] " + clientUsername + " connecté.");

            deliverPendingMessages();
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
                if (messageFromClient == null) break;
                handlePrivateMessage(messageFromClient);
            } catch (IOException e) {
                closeEverything(socket, bufferedReader, bufferedWriter);
                break;
            }
        }
        closeEverything(socket, bufferedReader, bufferedWriter);
    }

    // ===================== KICK SESSION DUPLIQUE =====================

    private void kickIfAlreadyConnected(String username) {
        List<ClientHandler> snapshot = new ArrayList<>(clientHandlers);
        for (ClientHandler ch : snapshot) {
            if (username.equalsIgnoreCase(ch.clientUsername)) {
                try {
                    ch.bufferedWriter.write("KICKED");
                    ch.bufferedWriter.newLine();
                    ch.bufferedWriter.flush();
                    System.out.println("[RG3] Session dupliquée → KICKED : " + username);
                } catch (IOException e) {
                    System.err.println("[ERREUR] Impossible d'envoyer KICKED à " + username);
                }
                ch.closeEverything(ch.socket, ch.bufferedReader, ch.bufferedWriter);
                break;
            }
        }
    }

    // ===================== GESTION MESSAGE =====================

    private void handlePrivateMessage(String message) {

        // ✅ Gérer la notification de lecture : "READ|reader|contact"
        if (message.startsWith("READ|")) {
            String[] parts = message.split("\\|", 3);
            if (parts.length == 3) {
                handleReadReceipt(parts[1], parts[2]);
            }
            return;
        }

        String[] parts = message.split("\\|", 3);
        if (parts.length < 3) {
            System.err.println("[ERREUR] Message mal formaté : " + message);
            return;
        }

        String sender   = parts[0];
        String receiver = parts[1];
        String content  = parts[2];

        if (content == null || content.trim().isEmpty()) return;
        if (content.length() > 1000) return;

        // ✅ Normaliser le receiver (insensible casse)
        String realReceiver = findRealUsername(receiver);
        if (realReceiver == null) {
            System.out.println("[ERREUR] saveMessage — destinataire introuvable : " + receiver);
            return;
        }

        saveMessage(sender, realReceiver, content);

        boolean destinataireConnecte = false;
        synchronized (clientHandlers) {
            for (ClientHandler ch : clientHandlers) {
                if (realReceiver.equalsIgnoreCase(ch.clientUsername)) {
                    try {
                        ch.bufferedWriter.write(sender + "|" + content);
                        ch.bufferedWriter.newLine();
                        ch.bufferedWriter.flush();
                        destinataireConnecte = true;
                    } catch (IOException e) {
                        System.err.println("[ERREUR] Impossible d'envoyer à " + realReceiver);
                    }
                }
            }
        }

        if (!destinataireConnecte) {
            System.out.println("[INFO] " + realReceiver + " est offline — message sauvegardé.");
        }
        System.out.println("[LOG] " + sender + " → " + realReceiver + " : " + content);
    }

    // ===================== NOTIFICATION DE LECTURE =====================

    private void handleReadReceipt(String reader, String contact) {
        EntityManager em = JPAUtil.getEntityManagerFactory().createEntityManager();
        try {
            em.getTransaction().begin();
            List<Message> msgs = em.createQuery(
                            "SELECT m FROM Message m " +
                                    "WHERE m.sender.username = :contact " +
                                    "AND m.receiver.username = :reader " +
                                    "AND m.statut != :lu", Message.class)
                    .setParameter("contact", contact)
                    .setParameter("reader", reader)
                    .setParameter("lu", StatusMessage.LU)
                    .getResultList();
            for (Message m : msgs) m.setStatut(StatusMessage.LU);
            em.getTransaction().commit();
            System.out.println("[LOG] " + reader + " a lu " + msgs.size() + " msg(s) de " + contact);
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
        } finally {
            em.close();
        }

        // ✅ Envoyer READ_ACK à l'expéditeur s'il est connecté
        synchronized (clientHandlers) {
            for (ClientHandler ch : clientHandlers) {
                if (ch.clientUsername.equalsIgnoreCase(contact)) {
                    try {
                        ch.bufferedWriter.write("READ_ACK|" + reader);
                        ch.bufferedWriter.newLine();
                        ch.bufferedWriter.flush();
                        System.out.println("[LOG] READ_ACK envoyé à " + contact + " (lu par " + reader + ")");
                    } catch (IOException e) {
                        System.err.println("[ERREUR] Envoi READ_ACK : " + e.getMessage());
                    }
                    break;
                }
            }
        }
    }

    // ===================== MESSAGES EN ATTENTE =====================

    private void deliverPendingMessages() {
        EntityManager em = JPAUtil.getEntityManagerFactory().createEntityManager();
        try {
            List<Message> pending = em.createQuery(
                            "SELECT m FROM Message m " +
                                    "WHERE m.receiver.username = :username AND m.statut = :statut " +
                                    "ORDER BY m.dateEnvoi ASC", Message.class)
                    .setParameter("username", clientUsername)
                    .setParameter("statut", StatusMessage.ENVOYE)
                    .getResultList();

            // ✅ Garder la liste des expéditeurs à notifier (DELIVERED_ACK)
            List<String> expediteursNotifies = new ArrayList<>();

            for (Message m : pending) {
                try {
                    bufferedWriter.write(m.getSender().getUsername() + "|" + m.getContenu());
                    bufferedWriter.newLine();
                    bufferedWriter.flush();

                    em.getTransaction().begin();
                    m.setStatut(StatusMessage.RECU);
                    em.getTransaction().commit();

                    // ✅ Mémoriser l'expéditeur pour lui envoyer DELIVERED_ACK
                    String expediteur = m.getSender().getUsername();
                    if (!expediteursNotifies.contains(expediteur)) {
                        expediteursNotifies.add(expediteur);
                    }

                } catch (IOException e) {
                    System.err.println("[ERREUR] Livraison message échouée.");
                }
            }

            // ✅ Envoyer DELIVERED_ACK à chaque expéditeur connecté
            // → le client expéditeur passera ses ticks de ✓ à ✓✓ gris
            for (String expediteur : expediteursNotifies) {
                synchronized (clientHandlers) {
                    for (ClientHandler ch : clientHandlers) {
                        if (ch.clientUsername.equalsIgnoreCase(expediteur)) {
                            try {
                                ch.bufferedWriter.write("DELIVERED_ACK|" + clientUsername);
                                ch.bufferedWriter.newLine();
                                ch.bufferedWriter.flush();
                                System.out.println("[LOG] DELIVERED_ACK envoyé à " + expediteur
                                        + " (messages livrés à " + clientUsername + ")");
                            } catch (IOException e) {
                                System.err.println("[ERREUR] Envoi DELIVERED_ACK : " + e.getMessage());
                            }
                            break;
                        }
                    }
                }
            }

        } finally {
            em.close();
        }
    }

    // ===================== USER LIST =====================

    public static void broadcastUserList() {
        List<ClientHandler> snapshot = new ArrayList<>(clientHandlers);

        StringBuilder sb = new StringBuilder("USERLIST|");
        for (ClientHandler ch : snapshot) sb.append(ch.clientUsername).append(",");

        String msg = sb.toString();
        if (msg.endsWith(",")) msg = msg.substring(0, msg.length() - 1);

        List<ClientHandler> toRemove = new ArrayList<>();
        for (ClientHandler ch : snapshot) {
            try {
                if (ch.socket != null && !ch.socket.isClosed()) {
                    ch.bufferedWriter.write(msg);
                    ch.bufferedWriter.newLine();
                    ch.bufferedWriter.flush();
                } else {
                    toRemove.add(ch);
                }
            } catch (IOException e) {
                toRemove.add(ch);
            }
        }
        clientHandlers.removeAll(toRemove);
    }

    // ===================== DECONNEXION =====================

    public void removeClientHandler() {
        if (clientHandlers.contains(this)) clientHandlers.remove(this);
        updateUserStatus(clientUsername, Status.OFFLINE);
        System.out.println("[LOG] " + clientUsername + " déconnecté.");
        broadcastUserList();
    }

    public void closeEverything(Socket socket, BufferedReader bufferedReader, BufferedWriter bufferedWriter) {
        removeClientHandler();
        try {
            if (bufferedReader != null) bufferedReader.close();
            if (bufferedWriter != null) bufferedWriter.close();
            if (socket        != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ===================== DB =====================

    /**
     * ✅ Trouve le vrai username en base (insensible à la casse)
     */
    private String findRealUsername(String username) {
        EntityManager em = JPAUtil.getEntityManagerFactory().createEntityManager();
        try {
            List<User> users = em.createQuery(
                            "SELECT u FROM User u WHERE LOWER(u.username) = LOWER(:username)", User.class)
                    .setParameter("username", username)
                    .getResultList();
            return users.isEmpty() ? null : users.get(0).getUsername();
        } finally {
            em.close();
        }
    }

    private void updateUserStatus(String username, Status status) {
        EntityManager em = JPAUtil.getEntityManagerFactory().createEntityManager();
        try {
            em.getTransaction().begin();
            List<User> users = em.createQuery(
                            "SELECT u FROM User u WHERE u.username = :username", User.class)
                    .setParameter("username", username)
                    .getResultList();
            if (!users.isEmpty()) users.get(0).setStatus(status);
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
                System.out.println("[ERREUR] saveMessage — user introuvable.");
                return;
            }

            boolean receiverOnline = clientHandlers.stream()
                    .anyMatch(ch -> receiverUsername.equalsIgnoreCase(ch.clientUsername));
            StatusMessage statut = receiverOnline ? StatusMessage.RECU : StatusMessage.ENVOYE;

            Message msg = new Message();
            msg.setSender(senderList.get(0));
            msg.setReceiver(receiverList.get(0));
            msg.setContenu(content);
            msg.setDateEnvoi(LocalDateTime.now());
            msg.setStatut(statut);

            em.getTransaction().begin();
            em.persist(msg);
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