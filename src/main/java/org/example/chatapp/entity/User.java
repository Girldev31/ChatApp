package org.example.chatapp.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.chatapp.enumeration.Status;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected long id;

    @Column(nullable = false, unique = true)
    protected String username;

    @Column(nullable = false)
    protected String password;

    @Enumerated(EnumType.STRING)
    protected Status status;

    protected LocalDateTime dateCreation;

    public User() {
        this.dateCreation = LocalDateTime.now();
        this.status = Status.OFFLINE;
    }

    public User(long id, String username, String password, Status status, LocalDateTime dateCreation) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.status = status;
        this.dateCreation = dateCreation;
    }

    public User(String username, String password, Status status, LocalDateTime dateCreation) {
        this.username = username;
        this.password = password;
        this.status = status;
        this.dateCreation = dateCreation;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public LocalDateTime getDateCreation() {
        return dateCreation;
    }

    public void setDateCreation(LocalDateTime dateCreation) {
        this.dateCreation = dateCreation;
    }
}
