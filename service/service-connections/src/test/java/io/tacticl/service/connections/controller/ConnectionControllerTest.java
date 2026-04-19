package io.tacticl.service.connections.controller;

import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.tacticl.business.connections.service.ConnectionRegistryService;
import io.tacticl.data.connections.entity.Connection;
import io.tacticl.service.connections.dto.ConnectionSummaryDto;
import io.tacticl.service.connections.dto.OAuthUrlResponseDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConnectionControllerTest {

    @Mock ConnectionRegistryService connectionRegistryService;
    @InjectMocks ConnectionController controller;

    private AuthenticatedUser user(String id) {
        AuthenticatedUser u = mock(AuthenticatedUser.class);
        when(u.getUserId()).thenReturn(id);
        return u;
    }

    @Test
    void getConnections_returns200WithList() {
        var conn = Connection.create("user1", "GITHUB", "vault/path", "@alice", List.of("repo"));
        when(connectionRegistryService.listConnections("user1")).thenReturn(List.of(conn));

        ResponseEntity<List<ConnectionSummaryDto>> response = controller.listConnections(user("user1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).platform()).isEqualTo("GITHUB");
        assertThat(response.getBody().get(0).platformUsername()).isEqualTo("@alice");
    }

    @Test
    void getOAuthUrl_returns200WithUrl() {
        when(connectionRegistryService.generateAuthUrl(eq("GITHUB"), anyString(), anyString()))
            .thenReturn("https://github.com/auth");

        ResponseEntity<OAuthUrlResponseDto> response =
            controller.generateAuthUrl("GITHUB", "https://app/cb", user("user1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().authUrl()).isEqualTo("https://github.com/auth");
    }

    @Test
    void deleteConnection_returns204() {
        ResponseEntity<Void> response = controller.disconnect("conn-1", user("user1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(connectionRegistryService).disconnect("user1", "conn-1");
    }
}
