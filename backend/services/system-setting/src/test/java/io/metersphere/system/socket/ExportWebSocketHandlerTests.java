package io.metersphere.system.socket;

import jakarta.websocket.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExportWebSocketHandlerTests {
    @AfterEach void clear() { ExportWebSocketHandler.ONLINE_EXPORT_EXCEL_SESSIONS.clear(); }

    @Test void untrustedClientCannotForwardAResultToAnotherTask() {
        Session victim = mock(Session.class);
        ExportWebSocketHandler.ONLINE_EXPORT_EXCEL_SESSIONS.put("victim",victim);
        new ExportWebSocketHandler().onMessage("attacker","{\"fileId\":\"victim\",\"msgType\":\"EXEC_RESULT\",\"isSuccessful\":true}");
        verifyNoInteractions(victim);
    }

    @Test void closingOldConnectionDoesNotRemoveReplacement() throws Exception {
        Session old = mock(Session.class); Session replacement = mock(Session.class);
        ExportWebSocketHandler.ONLINE_EXPORT_EXCEL_SESSIONS.put("file",replacement);
        new ExportWebSocketHandler().onClose("file",old);
        assertSame(replacement,ExportWebSocketHandler.ONLINE_EXPORT_EXCEL_SESSIONS.get("file"));
        verify(old).close(); verifyNoInteractions(replacement);
    }
}
