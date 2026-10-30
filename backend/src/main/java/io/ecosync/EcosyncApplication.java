package io.ecosync;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication
public class EcosyncApplication {
    public static void main(String[] args) {
        var context = SpringApplication.run(EcosyncApplication.class, args);
        if (context.getEnvironment().getProperty("ecosync.setup-only", Boolean.class, false)) {
            System.exit(SpringApplication.exit(context));
        }
    }
}
