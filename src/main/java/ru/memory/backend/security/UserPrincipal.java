package ru.memory.backend.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import ru.memory.backend.entity.User;

import java.util.Collection;
import java.util.List;

/**
 * Принципал запроса: актуальный пользователь из БД.
 * Роль и блокировка проверяются по данным из БД при каждом запросе,
 * поэтому блокировка учётной записи и смена роли действуют мгновенно.
 */
public class UserPrincipal implements UserDetails {

    private final transient User user;

    public UserPrincipal(User user) {
        this.user = user;
    }

    public User getUser() {
        return user;
    }

    public int getUserId() {
        return user.getId();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()));
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getLogin();
    }

    @Override
    public boolean isEnabled() {
        return !user.isBlocked();
    }
}
