package com.example.dating_application.Сontroller;

import com.example.dating_application.Exception.BusinessException;
import com.example.dating_application.DTO.Response.BlockResponseDTO;
import com.example.dating_application.Entity.User;
import com.example.dating_application.Service.BlockService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@CrossOrigin
@RestController
@RequestMapping("/api/blocks")
public class BlockController {

    private final BlockService blockService;

    public BlockController(BlockService blockService) {
        this.blockService = blockService;
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User) {
            return ((User) authentication.getPrincipal()).getUserId();
        }
        throw new BusinessException("User not authenticated");
    }

    @PostMapping("/{userId}")
    public ResponseEntity<BlockResponseDTO> blockUser(@PathVariable Long userId) {
        Long currentUserId = getCurrentUserId();
        var block = blockService.block(currentUserId, userId);
        return ResponseEntity.ok(blockService.toResponseDTO(block));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> unblockUser(@PathVariable Long userId) {
        Long currentUserId = getCurrentUserId();
        blockService.unblock(currentUserId, userId);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "User unblocked");
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<BlockResponseDTO>> getMyBlocks() {
        Long currentUserId = getCurrentUserId();
        List<BlockResponseDTO> blocks = blockService.getMyBlocks(currentUserId).stream()
                .map(blockService::toResponseDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(blocks);
    }
}