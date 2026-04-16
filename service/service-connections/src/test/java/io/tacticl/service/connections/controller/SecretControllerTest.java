package io.tacticl.service.connections.controller;

import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.tacticl.business.connections.service.SecretsVaultService;
import io.tacticl.data.connections.entity.SecretMetadata;
import io.tacticl.data.connections.entity.TestResult;
import io.tacticl.service.connections.dto.CreateSecretRequestDto;
import io.tacticl.service.connections.dto.SecretMetadataDto;
import io.tacticl.service.connections.dto.SecretTestResultDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecretControllerTest {

    @Mock SecretsVaultService secretsVaultService;
    @InjectMocks SecretController controller;

    private AuthenticatedUser user(String id) {
        AuthenticatedUser u = mock(AuthenticatedUser.class);
        when(u.getUserId()).thenReturn(id);
        return u;
    }

    @Test
    void listSecrets_returnsSecretList() {
        SecretMetadata s = SecretMetadata.create("user-1", "MY_KEY", "OpenAI");
        when(secretsVaultService.listSecrets("user-1")).thenReturn(List.of(s));

        ResponseEntity<?> resp = controller.listSecrets(user("user-1"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) resp.getBody()).hasSize(1);
    }

    @Test
    void createSecret_returns201() {
        SecretMetadata s = SecretMetadata.create("user-1", "MY_KEY", "OpenAI");
        when(secretsVaultService.store("user-1", "MY_KEY", "OpenAI", "sk-value")).thenReturn(s);
        var body = new CreateSecretRequestDto("MY_KEY", "OpenAI", "sk-value");

        ResponseEntity<?> resp = controller.createSecret(user("user-1"), body);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void deleteSecret_returns204() {
        ResponseEntity<?> resp = controller.deleteSecret(user("user-1"), "secret-1");

        verify(secretsVaultService).delete("user-1", "secret-1");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void testSecret_returnsResult() {
        when(secretsVaultService.testSecret("user-1", "secret-1")).thenReturn(TestResult.VALID);

        ResponseEntity<?> resp = controller.testSecret(user("user-1"), "secret-1");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isInstanceOf(SecretTestResultDto.class);
        assertThat(((SecretTestResultDto) resp.getBody()).result()).isEqualTo("VALID");
    }
}
