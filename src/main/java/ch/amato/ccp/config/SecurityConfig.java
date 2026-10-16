package ch.amato.ccp.config;

import ch.amato.ccp.repository.DemoDataRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/css/**", "/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/transactions", true)
                        .permitAll())
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                        .permitAll());
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * Demo-Benutzer für alle fiktiven Kunden. Das Passwort ist ein reiner Demo-Wert
     * und kann über die Umgebungsvariable CCP_DEMO_PASSWORD überschrieben werden.
     */
    @Bean
    UserDetailsService userDetailsService(DemoDataRepository repository,
                                          PasswordEncoder encoder,
                                          @Value("${ccp.demo.password}") String demoPassword) {
        List<UserDetails> users = repository.usernames().stream()
                .map(name -> User.withUsername(name)
                        .password(encoder.encode(demoPassword))
                        .roles("CUSTOMER")
                        .build())
                .toList();
        return new InMemoryUserDetailsManager(users);
    }
}
