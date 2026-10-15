package com.openq.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class BookingFormValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void init() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void close() {
        factory.close();
    }

    private static BookingForm form(String name, String email, String phone) {
        BookingForm f = new BookingForm();
        f.setPatientName(name);
        f.setPatientEmail(email);
        f.setPatientPhone(phone);
        return f;
    }

    private static Set<String> invalidFields(BookingForm f) {
        return validator.validate(f).stream()
                .map(ConstraintViolation::getPropertyPath).map(Object::toString).collect(Collectors.toSet());
    }

    @Test
    void validForm_hasNoViolations() {
        assertThat(invalidFields(form("Jane Patient", "jane@example.com", "+91 98765 43210"))).isEmpty();
    }

    @Test
    void phoneIsOptional() {
        assertThat(invalidFields(form("Jane", "jane@example.com", null))).isEmpty();
        assertThat(invalidFields(form("Jane", "jane@example.com", ""))).isEmpty();
    }

    @Test
    void blankNameAndBadEmailAreRejected() {
        assertThat(invalidFields(form("   ", "not-an-email", null))).containsExactlyInAnyOrder("patientName", "patientEmail");
    }

    @Test
    void missingFieldsAreRejected() {
        assertThat(invalidFields(form(null, null, null))).containsExactlyInAnyOrder("patientName", "patientEmail");
    }

    @Test
    void badPhoneIsRejected() {
        assertThat(invalidFields(form("Jane", "jane@example.com", "call me maybe"))).containsExactly("patientPhone");
    }

    @Test
    void tooLongNameIsRejected() {
        assertThat(invalidFields(form("x".repeat(101), "jane@example.com", null))).containsExactly("patientName");
    }
}
