package net.mwtw.hippoStaff.grant;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record GrantRecord(
        UUID targetUuid,
        String targetName,
        String group,
        String grantedBy,
        String reason,
        long grantedAtEpochMillis,
        Long expiresAtEpochMillis,
        GrantAction action
) {
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("targetUuid", this.targetUuid.toString());
        map.put("targetName", this.targetName);
        map.put("group", this.group);
        map.put("grantedBy", this.grantedBy);
        map.put("reason", this.reason);
        map.put("grantedAt", this.grantedAtEpochMillis);
        map.put("expiresAt", this.expiresAtEpochMillis);
        map.put("action", this.action.name());
        return map;
    }
}
