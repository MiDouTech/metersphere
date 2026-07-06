package io.metersphere.system.service.department;

import lombok.Data;

@Data
class SyncPartResult {
    private int total;
    private int success;
    private int failed;
    private int created;
    private int updated;
    private int disabled;
    private String errorMessage = "";

    void appendError(String error) {
        if (error == null || error.isBlank()) {
            return;
        }
        this.errorMessage = this.errorMessage + error;
    }
}
