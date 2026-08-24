package com.example.dating_application.DTO.Request;

import com.example.dating_application.Entity.ChildrenStatus;
import com.example.dating_application.Entity.DatingGoal;
import com.example.dating_application.Entity.EducationLevel;
import com.example.dating_application.Entity.Gender;
import com.example.dating_application.Entity.Temperament;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class UserUpdateDTO {

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 50, message = "Name must be between 2 and 50 characters")
    private String name;

    @NotNull(message = "Gender is required")
    private Gender gender;

    @NotNull(message = "Date of birth is required")
    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    @Size(max = 1000, message = "Characterisation must not exceed 1000 characters")
    private String characterisation;

    @Size(max = 100, message = "City must not exceed 100 characters")
    private String city;

    @NotNull(message = "Dating goal is required")
    private DatingGoal datingGoal;

    // Необов'язкові: null = «не вказано». Оскільки це PUT (повна заміна анкети),
    // відсутнє поле в тілі запиту очищає раніше збережене значення.
    private EducationLevel educationLevel;

    private Temperament temperament;

    private ChildrenStatus childrenStatus;

    public UserUpdateDTO() {
    }

    public UserUpdateDTO(String name, Gender gender, LocalDate dateOfBirth, String characterisation,
                         String city, DatingGoal datingGoal, EducationLevel educationLevel,
                         Temperament temperament, ChildrenStatus childrenStatus) {
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