package io.ecosync.identity;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name = "app_user")
public class UserAccount {
    @Id private UUID id;
    private String login;
    @Column(name = "display_name") private String displayName;
    @Column(name = "password_hash") private String passwordHash;
    private boolean enabled;
    @Column(name = "platform_admin") private boolean platformAdmin;
    protected UserAccount() { }
    public UUID id() { return id; }
    public String login() { return login; }
    public String displayName() { return displayName; }
    public String passwordHash() { return passwordHash; }
    public boolean enabled() { return enabled; }
    public boolean platformAdmin() { return platformAdmin; }
}
