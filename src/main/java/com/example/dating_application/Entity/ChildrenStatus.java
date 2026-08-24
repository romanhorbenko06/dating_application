package com.example.dating_application.Entity;

/**
 * Інформація про дітей (FR-8.7). Необов'язкове поле профілю.
 *
 * Значення поєднують два питання, які на побаченнях завжди йдуть разом:
 * чи є діти зараз і чи хоче людина дітей далі — так фронту достатньо
 * одного селекта замість двох.
 */
public enum ChildrenStatus {
    NO_CHILDREN_WANT_SOMEDAY,
    NO_CHILDREN_DONT_WANT,
    HAVE_CHILDREN_WANT_MORE,
    HAVE_CHILDREN_DONT_WANT_MORE,
    UNDECIDED,
    PREFER_NOT_TO_SAY
}