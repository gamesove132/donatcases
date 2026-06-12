package ua.donatecases.models;

public enum CaseType {
    PRIVILEGE("privilege"),    // Звичайні привілеї
    WHALE("whale"),            // Кити (великі нагороди)
    TIMED_LONG("timed_long"),  // Привілей на 3 тижні
    TIMED_SHORT("timed_short"),// Привілей на 3 дні
    KITS("kits");              // Кіти (набори предметів)

    private final String id;

    CaseType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static CaseType fromString(String s) {
        for (CaseType t : values()) {
            if (t.id.equalsIgnoreCase(s)) return t;
        }
        return null;
    }
}
