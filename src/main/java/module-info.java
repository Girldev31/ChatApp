module org.example.chatapp {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.persistence;
    requires static lombok;
    requires javafx.base;
    requires  java.sql;
    requires org.hibernate.orm.core;
    requires java.desktop;

    opens org.example.chatapp.entity;
    opens org.example.chatapp to javafx.fxml;
    exports org.example.chatapp;
    opens org.example.chatapp.utils to javafx.fxml;
}