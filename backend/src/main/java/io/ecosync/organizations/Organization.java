package io.ecosync.organizations;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name = "organization")
class Organization {
    @Id private UUID id;
    private String slug;
    @Column(name = "display_name") private String displayName;
    private boolean enabled;
    @Version @Column(name = "lock_version") private long version;
    protected Organization() { }
}
