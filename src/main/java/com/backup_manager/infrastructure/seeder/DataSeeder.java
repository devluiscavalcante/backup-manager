package com.backup_manager.infrastructure.seeder;

import com.backup_manager.domain.model.AppUser;
import com.backup_manager.infrastructure.persistence.AppUserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@Profile("dev")
public class DataSeeder {

    @Bean
    CommandLineRunner initDatabase(AppUserRepository repo, PasswordEncoder encoder) {
        return args -> {
            if (repo.findByUsername("admin").isEmpty()) {
                AppUser admin = new AppUser();
                admin.setUsername("admin");
                admin.setPassword(encoder.encode("123456"));
                admin.setRole("ROLE_ADMIN");
                repo.save(admin);
                System.out.println("✅ Usuário padrão criado: admin / 123456");
            }
        };
    }
}
