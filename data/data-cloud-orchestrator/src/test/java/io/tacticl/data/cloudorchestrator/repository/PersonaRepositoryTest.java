package io.tacticl.data.cloudorchestrator.repository;

import io.tacticl.data.cloudorchestrator.entity.Persona;
import io.tacticl.data.cloudorchestrator.entity.PersonaFamily;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure-mock tests for the {@link PersonaRepository} interface. Verifies method
 * signatures resolve and call-throughs work; integration with real Mongo is
 * covered downstream.
 */
@ExtendWith(MockitoExtension.class)
class PersonaRepositoryTest {

    @Mock private PersonaRepository repo;

    @Test
    void findByFamily_returnsListFromMock() {
        Persona p = Persona.create("product-manager", PersonaFamily.CONVERSATIONAL,
                "PM", "desc", "prompt", "claude-sonnet-4-6", List.of(), null);
        when(repo.findByFamily(PersonaFamily.CONVERSATIONAL)).thenReturn(List.of(p));

        List<Persona> result = repo.findByFamily(PersonaFamily.CONVERSATIONAL);

        assertThat(result).containsExactly(p);
        verify(repo).findByFamily(PersonaFamily.CONVERSATIONAL);
    }

    @Test
    void findByIdAndActive_returnsOptional() {
        Persona p = Persona.create("architect", PersonaFamily.PDLC,
                "Architect", "d", "p", "claude-haiku-4-5", List.of(), null);
        when(repo.findByIdAndActive("architect", true)).thenReturn(Optional.of(p));

        Optional<Persona> result = repo.findByIdAndActive("architect", true);

        assertThat(result).contains(p);
    }
}
