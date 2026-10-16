package com.skillhub.model.enums;

public enum CourseQuestionType {
    MULTIPLE_CHOICE("Multiple Choice (Single Answer)"),
    MULTIPLE_SELECT("Multiple Select (Multiple Correct Answers)"),
    TRUE_FALSE("True / False"),
    SHORT_ANSWER("Short Answer"),
    ESSAY("Long Answer / Essay"),
    FILL_BLANKS("Fill in the Blanks"),
    MATCHING("Matching Questions"),
    ORDERING("Ordering / Sequence Questions"),
    NUMERICAL("Numerical Answer"),
    CODE("Code Writing Questions");

    private final String displayName;

    CourseQuestionType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static CourseQuestionType fromString(String val) {
        if (val == null || val.trim().isEmpty()) {
            return MULTIPLE_CHOICE;
        }
        for (CourseQuestionType type : values()) {
            if (type.name().equalsIgnoreCase(val.trim())) {
                return type;
            }
        }
        return MULTIPLE_CHOICE;
    }
}
