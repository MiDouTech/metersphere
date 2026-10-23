package io.metersphere.system.notice;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@AllArgsConstructor
@lombok.NoArgsConstructor
@EqualsAndHashCode
public class Receiver {
    private String userId;
    private String type;
}
