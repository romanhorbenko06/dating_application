package com.example.dating_application.DTO.Request;

import com.example.dating_application.Entity.ChildrenStatus;
import com.example.dating_application.Entity.DatingGoal;
import com.example.dating_application.Entity.EducationLevel;
import com.example.dating_application.Entity.Gender;
import com.example.dating_application.Entity.Temperament;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/** PATCH анкети: DTO запам'ятовує, які ключі були в тілі, тож явний null очищає поле. */
public class UserPatchDTO {

    @Size(min = 2, max = 50, message = "Name must be between 2 and 50 characters")
    private String name;

    private Gender gender;

    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    @Size(max = 1000, message = "Characterisation must not exceed 1000 characters")
    private String characterisation;

    @Size(max = 100, message = "City must not exceed 100 characters")
    private String city;

    private DatingGoal datingGoal;

    private EducationLevel educationLevel;

    private Temperament temperament;

    private ChildrenStatus childrenStatus;

    @JsonIgnore
    private final Set<String> provided = new HashSet<>();

    public UserPatchDTO() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        provided.add("name");
    }

    public boolean hasName() {
        return provided.contains("name");
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
        provided.add("gender");
    }

    public boolean hasGender() {
        return provided.contains("gender");
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
        provided.add("dateOfBirth");
    }

    public boolean hasDateOfBirth() {
        return provided.contains("dateOfBirth");
    }

    public String getCharacterisation() {
        return characterisation;
    }

    public void setCharacterisation(String characterisation) {
        this.characterisation = characterisation;
        provided.add("characterisation");
    }

    public boolean hasCharacterisation() {
        return provided.contains("characterisation");
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
        provided.add("city");
    }

    public boolean hasCity() {
        return provided.contains("city");
    }

    public DatingGoal getDatingGoal() {
        return datingGoal;
    }

    public void setDatingGoal(DatingGoal datingGoal) {
        this.datingGoal = datingGoal;
        provided.add("datingGoal");
    }

    public boolean hasDatingGoal() {
        return provided.contains("datingGoal");
    }

    public EducationLevel getEducationLevel() {
        return educationLevel;
    }

    public void setEducationLevel(EducationLevel educationLevel) {
        this.educationLevel = educationLevel;
        provided.add("educationLevel");
    }

    public boolean hasEducationLevel() {
        return provided.contains("educationLevel");
    }

    public Temperament getTemperament() {
        return temperament;
    }

    public void setTemperament(Temperament temperament) {
        this.temperament = temperament;
        provided.add("temperament");
    }

    public boolean hasTemperament() {
        return provided.contains("temperament");
    }

    public ChildrenStatus getChildrenStatus() {
        return childrenStatus;
    }

    public void setChildrenStatus(ChildrenStatus childrenStatus) {
        this.childrenStatus = childrenStatus;
        provided.add("childrenStatus");
    }

    public boolean hasChildrenStatus() {
        return provided.contains("childrenStatus");
    }
}
