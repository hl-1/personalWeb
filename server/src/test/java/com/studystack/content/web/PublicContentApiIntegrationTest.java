package com.studystack.content.web;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studystack.content.domain.Article;
import com.studystack.content.domain.ArticleRepository;
import com.studystack.content.domain.Category;
import com.studystack.content.domain.CategoryRepository;
import com.studystack.content.domain.Tag;
import com.studystack.content.domain.TagRepository;
import com.studystack.shared.slug.SlugPolicy;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false"
})
class PublicContentApiIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.7-alpine");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ArticleRepository articles;

    @Autowired
    CategoryRepository categories;

    @Autowired
    TagRepository tags;

    @Autowired
    SlugPolicy slugPolicy;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    EntityManager entityManager;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearContentData() {
        jdbcTemplate.execute(
                "truncate table content_article_tag, content_article, content_category, content_tag cascade");
    }

    @Test
    void listsArticlesAnonymouslyWithPaginationFiltersAndFieldWhitelist() throws Exception {
        PublishedFixture fixture = persistFixture();

        mockMvc.perform(get("/api/v1/articles")
                        .param("category", "  JAVA  ")
                        .param("tag", " SPRING "))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.*", hasSize(5)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].*", hasSize(8)))
                .andExpect(jsonPath("$.items[0].id").value(fixture.visible().id().toString()))
                .andExpect(jsonPath("$.items[0].slug").value("public-article"))
                .andExpect(jsonPath("$.items[0].category").value("java"))
                .andExpect(jsonPath("$.items[0].tags[0]").value("spring"))
                .andExpect(jsonPath("$.items[0].status").doesNotExist())
                .andExpect(jsonPath("$.items[0].bodyMarkdown").doesNotExist())
                .andExpect(jsonPath("$.items[0].version").doesNotExist());
    }

    @Test
    void returnsSanitizedArticleDetailAndGenericNotFoundProblems() throws Exception {
        persistFixture();

        mockMvc.perform(get("/api/v1/articles/public-article"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.*", hasSize(12)))
                .andExpect(jsonPath("$.slug").value("public-article"))
                .andExpect(jsonPath("$.contentHtml").value(org.hamcrest.Matchers.containsString("<h1>Safe</h1>")))
                .andExpect(jsonPath("$.contentHtml").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("script"))))
                .andExpect(jsonPath("$.canonicalPath").value("/blog/public-article"))
                .andExpect(jsonPath("$.bodyMarkdown").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist());

        for (String slug : List.of("draft-hidden", "missing-article", "--invalid--")) {
            MvcResult result = mockMvc.perform(get("/api/v1/articles/{slug}", slug))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.*", hasSize(6)))
                    .andExpect(jsonPath("$.type").value("urn:studystack:problem:article-not-found"))
                    .andExpect(jsonPath("$.title").value("Article not found"))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.detail").value("The requested article is unavailable"))
                    .andExpect(jsonPath("$.instance").value("/api/v1/articles/" + slug))
                    .andExpect(jsonPath("$.code").value("article_not_found"))
                    .andReturn();
            assertFalse(result.getResponse().getContentAsString().contains("secret"));
        }
    }

    @Test
    void listsOnlyPublicTaxonomiesWithPublishedCounts() throws Exception {
        persistFixture();

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].*", hasSize(3)))
                .andExpect(jsonPath("$[0].name").value("Java"))
                .andExpect(jsonPath("$[0].slug").value("java"))
                .andExpect(jsonPath("$[0].publishedArticleCount").value(1));

        mockMvc.perform(get("/api/v1/tags"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].*", hasSize(3)))
                .andExpect(jsonPath("$[0].name").value("Spring"))
                .andExpect(jsonPath("$[0].slug").value("spring"))
                .andExpect(jsonPath("$[0].publishedArticleCount").value(1));
    }

    @Test
    void rejectsInvalidPaginationAsStableProblems() throws Exception {
        for (String path : List.of(
                "/api/v1/articles?page=-1",
                "/api/v1/articles?size=0",
                "/api/v1/articles?size=51",
                "/api/v1/articles?page=not-a-number")) {
            mockMvc.perform(get(path))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.*", hasSize(6)))
                    .andExpect(jsonPath("$.type").value("urn:studystack:problem:invalid-request"))
                    .andExpect(jsonPath("$.title").value("Invalid request"))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.detail").value("One or more request parameters are invalid"))
                    .andExpect(jsonPath("$.instance").value("/api/v1/articles"))
                    .andExpect(jsonPath("$.code").value("invalid_request"));
        }
    }

    private PublishedFixture persistFixture() {
        Instant currentTime = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Category java = new Category(UUID.randomUUID(), "Java", slugPolicy.create("java"), currentTime);
        Category empty = new Category(UUID.randomUUID(), "Empty", slugPolicy.create("empty"), currentTime);
        Tag spring = new Tag(UUID.randomUUID(), "Spring", slugPolicy.create("spring"), currentTime);
        Tag emptyTag = new Tag(UUID.randomUUID(), "Empty", slugPolicy.create("empty"), currentTime);
        Article visible = article(
                "public-article",
                java,
                "# Safe\n\n<script>secret</script>",
                currentTime.minusSeconds(120));
        visible.addTag(spring, currentTime.minusSeconds(121));
        visible.publish(currentTime.minusSeconds(120));
        Article draft = article("draft-hidden", java, "draft secret", currentTime.minusSeconds(60));
        draft.addTag(spring, currentTime.minusSeconds(60));

        inTransaction(() -> {
            categories.save(java);
            categories.save(empty);
            tags.save(spring);
            tags.save(emptyTag);
            articles.save(visible);
            articles.save(draft);
            entityManager.flush();
        });
        return new PublishedFixture(visible);
    }

    private Article article(String slug, Category category, String markdown, Instant timestamp) {
        return new Article(
                UUID.randomUUID(),
                slugPolicy.create(slug),
                "Title " + slug,
                "Summary " + slug,
                markdown,
                category,
                "SEO " + slug,
                "SEO description " + slug,
                timestamp);
    }

    private void inTransaction(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
    }

    private record PublishedFixture(Article visible) {
    }
}
