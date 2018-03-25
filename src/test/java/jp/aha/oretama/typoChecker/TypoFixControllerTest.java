package jp.aha.oretama.typoChecker;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.aha.oretama.typoChecker.model.Event;
import jp.aha.oretama.typoChecker.model.Suggestion;
import jp.aha.oretama.typoChecker.model.Token;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.languagetool.AnalyzedSentence;
import org.languagetool.rules.CommaWhitespaceRule;
import org.languagetool.rules.DemoRule;
import org.languagetool.rules.DoublePunctuationRule;
import org.languagetool.rules.EmptyLineRule;
import org.languagetool.rules.Example;
import org.languagetool.rules.LongSentenceRule;
import org.languagetool.rules.MultipleWhitespaceRule;
import org.languagetool.rules.OpenNMTRule;
import org.languagetool.rules.Rule;
import org.languagetool.rules.RuleMatch;
import org.languagetool.rules.SentenceWhitespaceRule;
import org.languagetool.rules.UppercaseSentenceStartRule;
import org.languagetool.rules.WhiteSpaceAtBeginOfParagraph;
import org.languagetool.rules.WhiteSpaceBeforeParagraphEnd;
import org.languagetool.rules.en.AvsAnRule;
import org.languagetool.rules.en.CompoundRule;
import org.languagetool.rules.en.ContractionSpellingRule;
import org.languagetool.rules.en.EnglishDashRule;
import org.languagetool.rules.en.EnglishUnpairedBracketsRule;
import org.languagetool.rules.en.EnglishWordRepeatBeginningRule;
import org.languagetool.rules.en.EnglishWordRepeatRule;
import org.languagetool.rules.en.EnglishWrongWordInContextRule;
import org.languagetool.rules.en.WordCoherencyRule;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @author aha-oretama
 */
@RunWith(SpringRunner.class)
@WebMvcTest(TypoFixController.class)
@Slf4j
public class TypoFixControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @MockBean
    private SpellCheckerService service;

    @MockBean
    private GitHubTemplate template;

    private Token token = new Token();
    private Event event = new Event();
    private String rawDiff = "This is raw diff.";
    private List<Suggestion> suggestions = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        token = new Token();
        suggestions.add(new Suggestion("src/main/java/test.java",99, null));

        doReturn(token).when(template).getAuthToken(event);
        doReturn(rawDiff).when(template).getRawDiff(event, token);
        doReturn(suggestions).when(service).getSuggestions(rawDiff);
        doReturn(true).when(template).postComment(event, suggestions, token);
    }

    @Test
    public void pingReturnOk() throws Exception {
        Map<String, String> expected = new HashMap<>();
        expected.put("message", "Ping is OK");

        this.mvc.perform(get("/ping"))
                .andExpect(status().isOk())
                .andExpect(content().string(mapper.writeValueAsString(expected)));
    }

    @Test
    public void typoCheckerNotAcceptNoHeader() throws Exception {
        Map<String, String> expected = new HashMap<>();
        expected.put("message", "Event is not pull_request.");

        this.mvc.perform(post("/typo-fixer"))
                .andExpect(status().isOk())
                .andExpect(content().string(mapper.writeValueAsString(expected)));
    }

    @Test
    public void typoCheckerNotAcceptExceptPullRequest() throws Exception {
        Map<String, String> expected = new HashMap<>();
        expected.put("message", "Event is not pull_request.");

        this.mvc.perform(post("/typo-fixer").header("X-GitHub-Event","issue_comment"))
                .andExpect(status().isOk())
                .andExpect(content().string(mapper.writeValueAsString(expected)));
    }

    @Test
    public void typoCheckerNotWorkExceptTargetAction() throws Exception {
        // Arrange
        event.setAction("assigned");
        String body = mapper.writeValueAsString(event);
        log.info(body);

        Map<String, String> expected = new HashMap<>();
        expected.put("message", "This comment event is not a target.");

        // Act
        this.mvc.perform(post("/typo-fixer").header("X-GitHub-Event","pull_request").content(body).contentType(MediaType.APPLICATION_JSON))
                // Assert
                .andExpect(status().isOk())
                .andExpect(content().string(mapper.writeValueAsString(expected)));
    }

    @Test
    public void typoCheckerAcceptPullRequestOpened() throws Exception {
        // Arrange
        event.setAction("opened");
        String body = mapper.writeValueAsString(event);

        Map<String, String> expected = new HashMap<>();
        expected.put("message", "Comment succeeded.");

        // Act
        this.mvc.perform(post("/typo-fixer").header("X-GitHub-Event","pull_request").content(body).contentType(MediaType.APPLICATION_JSON))
        // Assert
                .andExpect(status().isOk())
                .andExpect(content().string(mapper.writeValueAsString(expected)));
    }
}