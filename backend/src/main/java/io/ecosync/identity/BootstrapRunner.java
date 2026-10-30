package io.ecosync.identity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
@Component @ConditionalOnProperty(name = "ecosync.bootstrap.enabled", havingValue = "true")
class BootstrapRunner implements ApplicationRunner {
    private final BootstrapService service;
    private final String login, password, organization;
    private final boolean setupOnly;
    BootstrapRunner(BootstrapService service, @Value("${ecosync.bootstrap.username}") String login,
            @Value("${ecosync.bootstrap.password}") String password,
            @Value("${ecosync.bootstrap.organization}") String organization,
            @Value("${ecosync.setup-only}") boolean setupOnly) {
        this.service = service; this.login = login; this.password = password;
        this.organization = organization; this.setupOnly = setupOnly;
    }
    @Override public void run(ApplicationArguments args) {
        if (!setupOnly) throw new IllegalStateException("Administrator provisioning requires the one-shot setup process.");
        service.provision(login, password, organization);
    }
}
