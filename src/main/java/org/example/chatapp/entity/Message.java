package org.example.chatapp.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.chatapp.enumeration.StatusMessage;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "Message")
@Data
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected Long id;

    @ManyToOne
    @JoinColumn(name = "sender_id", nullable = false)
    protected User sender;

    @ManyToOne
    @JoinColumn(name = "receiver_id", nullable = false)
    protected User receiver;

    @Column(nullable = false, columnDefinition = "TEXT")
    protected String contenu;

    protected LocalDateTime dateEnvoi;

    @Enumerated(EnumType.STRING)
    protected StatusMessage statut;

    public Message() {
        this.dateEnvoi = LocalDateTime.now();
        this.statut = StatusMessage.ENVOYE;
    }

    public Message(Long id, User sender, User receiver, String contenu, LocalDateTime dateEnvoi, StatusMessage statut) {
        this.id = id;
        this.sender = sender;
        this.receiver = receiver;
        this.contenu = contenu;
        this.dateEnvoi = dateEnvoi;
        this.statut = statut;
    }

    public Message(User sender, User receiver, String contenu, LocalDateTime dateEnvoi, StatusMessage statut) {
        this.sender = sender;
        this.receiver = receiver;
        this.contenu = contenu;
        this.dateEnvoi = dateEnvoi;
        this.statut = statut;
    }
}
