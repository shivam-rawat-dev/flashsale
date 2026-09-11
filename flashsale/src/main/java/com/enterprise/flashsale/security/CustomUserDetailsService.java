package com.enterprise.flashsale.security;

import com.enterprise.flashsale.entity.AppUser;
import com.enterprise.flashsale.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found: " + email)
                );

        String rawRole = user.getRole() != null ? user.getRole() : "ROLE_USER";
        String normalizedRole = rawRole.startsWith("ROLE_") ? rawRole : "ROLE_" + rawRole;
        String cleanRole = rawRole.startsWith("ROLE_") ? rawRole.substring(5) : rawRole;

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(normalizedRole));
        authorities.add(new SimpleGrantedAuthority(cleanRole));

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPassword())
                .authorities(authorities)
                .build();
    }
}
