package com.example.dating_application.Repo;

import com.example.dating_application.Entity.IslandGame;
import com.example.dating_application.Entity.IslandGameStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IslandGameRepository extends JpaRepository<IslandGame, Long> {

    /** identity зростає, тож gameId задає порядок. */
    Optional<IslandGame> findFirstByChatChatIdOrderByGameIdDesc(Long chatId);

    List<IslandGame> findByStatusInAndExpiresAtBefore(Collection<IslandGameStatus> statuses, LocalDateTime moment);

    void deleteByChatChatId(Long chatId);
}
