package com.example.dating_application.Service;

import com.example.dating_application.Exception.BusinessException;
import com.example.dating_application.DTO.Response.BlockResponseDTO;
import com.example.dating_application.Entity.Block;
import com.example.dating_application.Entity.Role;
import com.example.dating_application.Entity.User;
import com.example.dating_application.Repo.BlockRepository;
import com.example.dating_application.Repo.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Блокування дейтером дейтера.
 *
 * Ефект симетричний: після блокування користувачі зникають один в одного
 * зі стрічки, не можуть надсилати лайки, писати в чат і бачити фото —
 * незалежно від того, хто саме натиснув «заблокувати».
 * Зняти блок може лише той, хто його поставив.
 */
@Service
public class BlockService {

    private final BlockRepository blockRepository;
    private final UserRepository userRepository;

    public BlockService(BlockRepository blockRepository, UserRepository userRepository) {
        this.blockRepository = blockRepository;
        this.userRepository = userRepository;
    }

    public Block block(Long blockerId, Long blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new BusinessException("You cannot block yourself");
        }

        User blocked = userRepository.findById(blockedId)
                .orElseThrow(() -> new BusinessException("User to block not found"));

        if (blocked.getRole() == Role.ADMIN) {
            throw new BusinessException("Administrators cannot be blocked");
        }

        // Ідемпотентно: повторний виклик не створює другий рядок
        var existing = blockRepository.findByBlockerUserIdAndBlockedUserId(blockerId, blockedId);
        if (existing.isPresent()) {
            return existing.get();
        }

        User blocker = userRepository.findById(blockerId)
                .orElseThrow(() -> new BusinessException("User not found"));

        Block block = new Block();
        block.setBlocker(blocker);
        block.setBlocked(blocked);
        block.setCreatedAt(LocalDateTime.now());

        return blockRepository.save(block);
    }

    public void unblock(Long blockerId, Long blockedId) {
        Block block = blockRepository.findByBlockerUserIdAndBlockedUserId(blockerId, blockedId)
                .orElseThrow(() -> new BusinessException("You have not blocked this user"));

        blockRepository.delete(block);
    }

    public List<Block> getMyBlocks(Long blockerId) {
        return blockRepository.findByBlockerUserIdOrderByCreatedAtDesc(blockerId);
    }

    /** Чи є блок між двома користувачами в будь-якому напрямку. */
    public boolean isBlockedBetween(Long userA, Long userB) {
        return blockRepository.existsBetween(userA, userB);
    }

    public BlockResponseDTO toResponseDTO(Block block) {
        return new BlockResponseDTO(
                block.getBlockId(),
                block.getBlocked().getUserId(),
                block.getBlocked().getName(),
                block.getCreatedAt()
        );
    }
}