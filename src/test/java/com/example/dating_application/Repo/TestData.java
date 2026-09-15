package com.example.dating_application.Repo;

import com.example.dating_application.Entity.Block;
import com.example.dating_application.Entity.Chat;
import com.example.dating_application.Entity.DatingGoal;
import com.example.dating_application.Entity.Gender;
import com.example.dating_application.Entity.Message;
import com.example.dating_application.Entity.Request;
import com.example.dating_application.Entity.RequestStatus;
import com.example.dating_application.Entity.Role;
import com.example.dating_application.Entity.Tag;
import com.example.dating_application.Entity.User;
import com.example.dating_application.Entity.UserTags;
import jakarta.persistence.EntityManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Фікстури для тестів на живій базі.
 *
 * Окремий клас, бо ті самі дані потрібні кільком тестам, а через EntityManager —
 * щоб не тягнути в тест півдюжини репозиторіїв заради двох рядків.
 * Email унікальний за рахунок лічильника: у межах одного контексту тести
 * створюють десятки користувачів, і збіг адреси впав би на обмеженні БД.
 */
final class TestData {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private final EntityManager em;

    TestData(EntityManager em) {
        this.em = em;
    }

    User user(String name) {
        return user(name, Gender.FEMALE, "Київ", DatingGoal.FRIENDSHIP, LocalDate.of(1995, 5, 5));
    }

    User user(String name, Gender gender, String city, DatingGoal goal, LocalDate birth) {
        User u = new User();
        u.setName(name);
        u.setEmail("user" + COUNTER.incrementAndGet() + "@test.local");
        u.setPasswordhash("{noop}irrelevant");
        u.setGender(gender);
        u.setCity(city);
        u.setDatingGoal(goal);
        u.setDateOfBirth(birth);
        u.setRole(Role.DATER);
        u.setBlocked(false);
        em.persist(u);
        return u;
    }

    User admin() {
        User u = user("Адмін");
        u.setRole(Role.ADMIN);
        return u;
    }

    User banned(String name) {
        User u = user(name);
        u.setBlocked(true);
        u.setBlockedAt(LocalDateTime.now());
        return u;
    }

    Request request(User from, User to, RequestStatus status) {
        Request r = new Request();
        r.setFromUser(from);
        r.setToUser(to);
        r.setStatus(status);
        r.setCreatedAt(LocalDateTime.now());
        em.persist(r);
        return r;
    }

    Block block(User blocker, User blocked) {
        Block b = new Block();
        b.setBlocker(blocker);
        b.setBlocked(blocked);
        b.setCreatedAt(LocalDateTime.now());
        em.persist(b);
        return b;
    }

    Chat chat(User a, User b) {
        Chat c = new Chat();
        c.setUser1(a);
        c.setUser2(b);
        em.persist(c);
        return c;
    }

    Message message(Chat chat, User sender, String text, LocalDateTime sentAt) {
        Message m = new Message();
        m.setChat(chat);
        m.setSender(sender);
        m.setContent(text);
        m.setSentAt(sentAt);
        m.setIsRead(false);
        m.setDeleted(false);
        em.persist(m);
        return m;
    }

    Tag tag(String name) {
        Tag t = new Tag();
        t.setTagName(name + COUNTER.incrementAndGet());
        em.persist(t);
        return t;
    }

    void give(User user, Tag tag) {
        UserTags ut = new UserTags();
        ut.setUser(user);
        ut.setTag(tag);
        em.persist(ut);
    }

    /** Скидає persistence context: далі запити підуть у БД, а не в кеш першого рівня. */
    void flush() {
        em.flush();
        em.clear();
    }
}
