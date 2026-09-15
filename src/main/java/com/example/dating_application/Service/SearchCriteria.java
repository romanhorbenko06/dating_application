package com.example.dating_application.Service;

import com.example.dating_application.Entity.DatingGoal;
import com.example.dating_application.Entity.Gender;

public class SearchCriteria {
    private Gender gender;
    private Integer minAge;
    private Integer maxAge;
    private String city;
    private DatingGoal datingGoal;

    public SearchCriteria() {
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public Integer getMinAge() {
        return minAge;
    }

    public void setMinAge(Integer minAge) {
        this.minAge = minAge;
    }

    public Integer getMaxAge() {
        return maxAge;
    }

    public void setMaxAge(Integer maxAge) {
        this.maxAge = maxAge;
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
}