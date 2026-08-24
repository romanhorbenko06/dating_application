package com.example.dating_application.Service;

import com.example.dating_application.Exception.BusinessException;
import com.example.dating_application.DTO.Response.TagResponseDTO;
import com.example.dating_application.Entity.Tag;
import com.example.dating_application.Entity.User;
import com.example.dating_application.Entity.UserTags;
import com.example.dating_application.Repo.TagRepository;
import com.example.dating_application.Repo.UserRepository;
import com.example.dating_application.Repo.UserTagsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TagService {

    private final TagRepository tagRepository;
    private final UserTagsRepository userTagsRepository;
    private final UserRepository userRepository;

    public TagService(TagRepository tagRepository,
                      UserTagsRepository userTagsRepository,
                      UserRepository userRepository) {
        this.tagRepository = tagRepository;
        this.userTagsRepository = userTagsRepository;
        this.userRepository = userRepository;
    }

    public List<Tag> getAllTags() {
        return tagRepository.findAll();
    }

    public List<Tag> getUserTags(Long userId) {
        return userTagsRepository.findByUserUserId(userId).stream()
                .map(UserTags::getTag)
                .collect(Collectors.toList());
    }

    /** Додає тег користувачу; ідемпотентно (повторне додавання без ефекту). */
    public void addTagToUser(Long userId, Long tagId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("User not found"));
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new BusinessException("Tag not found"));

        if (userTagsRepository.existsByUserUserIdAndTagTagId(userId, tagId)) {
            return;
        }

        UserTags userTag = new UserTags();
        userTag.setUser(user);
        userTag.setTag(tag);
        userTagsRepository.save(userTag);
    }

    @Transactional
    public void removeTagFromUser(Long userId, Long tagId) {
        userTagsRepository.deleteByUserUserIdAndTagTagId(userId, tagId);
    }

    public TagResponseDTO toResponseDTO(Tag tag) {
        return new TagResponseDTO(tag.getTagId(), tag.getTagName());
    }
}