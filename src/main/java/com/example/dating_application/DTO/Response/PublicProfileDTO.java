package com.example.dating_application.DTO.Response;

import com.example.dating_application.Entity.ChildrenStatus;
import com.example.dating_application.Entity.DatingGoal;
import com.example.dating_application.Entity.EducationLevel;
import com.example.dating_application.Entity.Gender;
import com.example.dating_application.Entity.Temperament;

import java.time.LocalDate;


public class PublicProfileDTO {
    private Long userId;
    private String name;
    private Gender gender;
    private LocalDate dateOfBirth;
    private String characterisation;
    private String city;
    private DatingGoal datingGoal;
    private EducationLevel educationLevel;
    private Temperament temperament;
    private ChildrenStatus childrenStatus;

    public PublicProfileDTO() {
    }

    public PublicProfileDTO(Long userId, String name, Gender gender,
                            LocalDate dateOfBirth, String characterisation,
                            String city, DatingGoal datingGoal,
                            EducationLevel educationLevel, Temperament temperament,
                            ChildrenStatus childrenStatus) {
        this.userId = userId;
        this.name = name;
        this.gender = gender;
        this.dateOfBirth = dateOfBirth;
        this.characterisation = characterisation;
        this.city = city;
        this.datingGoal = datingGoal;
        this.educationLevel = educationLevel;
        this.temperament = temperament;
        this.childrenStatus = childrenStatus;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getCharacterisation() {
        return characterisation;
    }

    public void setCharacterisation(String characterisation) {
        this.characterisation = characterisation;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public DatingGoal getDatingGoal() {
        return datingGoal;
    }

    public void setDatingGoal(DatingGoal datingGoal) {
        this.datingGoal = datingGoal;
    }

    public EducationLevel getEducationLevel() {
        return educationLevel;
    }

    public void setEducationLevel(EducationLevel educationLevel) {
        this.educationLevel = educationLevel;
    }

    public Temperament getTemperament() {
        return temperament;
    }

    public void setTemperament(Temperament temperament) {
        this.temperament = temperament;
    }

    public ChildrenStatus getChildrenStatus() {
        return childrenStatus;
    }

    public void setChildrenStatus(ChildrenStatus childrenStatus) {
        this.childrenStatus = childrenStatus;
    }
}