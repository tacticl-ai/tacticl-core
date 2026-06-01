package io.tacticl.data.cloudorchestrator.repository;

import io.tacticl.data.cloudorchestrator.entity.Skill;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkillRepositoryTest {

    @Mock private SkillRepository repo;
    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void findByActive_returnsActiveSkills() throws Exception {
        Skill s = Skill.create("web_search", "Web Search", "desc",
                mapper.readTree("{}"), "BraveSearchActivity");
        when(repo.findByActive(true)).thenReturn(List.of(s));

        List<Skill> result = repo.findByActive(true);

        assertThat(result).containsExactly(s);
    }

    @Test
    void findByIdIn_returnsBatch() throws Exception {
        Skill a = Skill.create("a", "A", "d", mapper.readTree("{}"), "ActA");
        Skill b = Skill.create("b", "B", "d", mapper.readTree("{}"), "ActB");
        when(repo.findByIdIn(List.of("a", "b"))).thenReturn(List.of(a, b));

        List<Skill> result = repo.findByIdIn(List.of("a", "b"));

        assertThat(result).containsExactly(a, b);
    }
}
