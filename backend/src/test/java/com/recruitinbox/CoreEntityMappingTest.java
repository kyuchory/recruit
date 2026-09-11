package com.recruitinbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import com.recruitinbox.application.Application;
import com.recruitinbox.application.ApplicationRepository;
import com.recruitinbox.application.ApplicationStatus;
import com.recruitinbox.application.ReviewStatus;
import com.recruitinbox.applicationevent.ApplicationEvent;
import com.recruitinbox.applicationevent.ApplicationEventRepository;
import com.recruitinbox.applicationevent.ApplicationEventType;
import com.recruitinbox.applicationevent.EventResult;
import com.recruitinbox.applicationevent.EventStatus;
import com.recruitinbox.applicationevent.ScheduleKind;
import com.recruitinbox.link.Link;
import com.recruitinbox.link.LinkRepository;
import com.recruitinbox.user.User;
import com.recruitinbox.user.UserRepository;

/**
 * Mapping-integrity + owner-scoping checks against the real Flyway schema
 * (PostgreSQL comes from Testcontainers -- see {@link com.recruitinbox.support.AbstractIntegrationTest}).
 * {@code ddl-auto=validate} is forced, so context startup itself proves the
 * entities line up with V1/V2.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
class CoreEntityMappingTest extends com.recruitinbox.support.AbstractIntegrationTest {

    @Autowired
    TestEntityManager em;
    @Autowired
    UserRepository users;
    @Autowired
    LinkRepository links;
    @Autowired
    ApplicationRepository applications;
    @Autowired
    ApplicationEventRepository events;

    private User newUser(String email) {
        User u = new User();
        u.setEmail(email);
        u.setTimezone("Asia/Seoul");
        return em.persist(u);
    }

    private Link newLink(UUID ownerId) {
        Link l = new Link();
        l.setOwnerId(ownerId);
        l.setOriginalUrl("https://careers.example.com/jobs/42");
        l.setNormalizedUrl("https://careers.example.com/jobs/42");
        l.setUrlHash("a".repeat(64));
        l.setSourceChannel("url");
        l.setTitle("예시회사 백엔드 채용");
        return em.persist(l);
    }

    private Application newApplication(UUID ownerId, UUID linkId, String positionKey) {
        Application a = new Application();
        a.setOwnerId(ownerId);
        a.setLinkId(linkId);
        a.setPositionKey(positionKey);
        a.setCompanyName("예시회사");
        a.setPositionTitle("백엔드 개발자");
        return em.persist(a);
    }

    @Test
    void persistsAndReloadsTheFullGraphWithEnumsAndJsonb() {
        User owner = newUser("owner+" + UUID.randomUUID() + "@example.com");
        Link link = newLink(owner.getId());
        Application app = newApplication(owner.getId(), link.getId(), "default");

        // UNKNOWN placeholder, CUSTOM type with an explicit label
        ApplicationEvent custom = new ApplicationEvent();
        custom.setOwnerId(owner.getId());
        custom.setApplicationId(app.getId());
        custom.setType(ApplicationEventType.CUSTOM);
        custom.setCustomLabel("사전 인적성 스크리닝");
        custom.setSortOrder(0);
        custom.setScheduleKind(ScheduleKind.UNKNOWN);
        em.persist(custom);

        // EXACT window: scheduled_at == start_at, end_at after start_at
        Instant start = Instant.parse("2026-09-28T04:00:00Z");
        ApplicationEvent coding = new ApplicationEvent();
        coding.setOwnerId(owner.getId());
        coding.setApplicationId(app.getId());
        coding.setType(ApplicationEventType.CODING_TEST);
        coding.setSortOrder(1);
        coding.setScheduleKind(ScheduleKind.EXACT);
        coding.setScheduledAt(start);
        coding.setStartAt(start);
        coding.setEndAt(start.plus(2, ChronoUnit.HOURS));
        coding.setStatus(EventStatus.SCHEDULED);
        coding.setResult(EventResult.WAITING);
        coding.setFieldMeta(Map.of(
                "start_at", Map.of("source", "USER", "confirmed_at", "2026-09-10T01:00:00Z")));
        em.persist(coding);

        // DATE_ONLY deadline
        ApplicationEvent deadline = new ApplicationEvent();
        deadline.setOwnerId(owner.getId());
        deadline.setApplicationId(app.getId());
        deadline.setType(ApplicationEventType.DOCUMENT_DEADLINE);
        deadline.setSortOrder(2);
        deadline.setScheduleKind(ScheduleKind.DATE_ONLY);
        deadline.setScheduledDate(LocalDate.of(2026, 9, 18));
        em.persist(deadline);

        em.flush();
        em.clear();

        Application reloaded = applications.findByIdAndOwnerId(app.getId(), owner.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ApplicationStatus.INTERESTED);
        assertThat(reloaded.getReviewStatus()).isEqualTo(ReviewStatus.PENDING);
        assertThat(reloaded.getNotes()).isEmpty();
        assertThat(reloaded.getFieldMeta()).isEmpty();
        assertThat(reloaded.getVersion()).isZero();
        assertThat(reloaded.getCreatedAt()).isNotNull();

        List<ApplicationEvent> timeline =
                events.findByApplicationIdAndOwnerIdOrderBySortOrderAscIdAsc(app.getId(), owner.getId());
        assertThat(timeline).extracting(ApplicationEvent::getType).containsExactly(
                ApplicationEventType.CUSTOM,
                ApplicationEventType.CODING_TEST,
                ApplicationEventType.DOCUMENT_DEADLINE);

        ApplicationEvent reloadedCustom = timeline.get(0);
        assertThat(reloadedCustom.getCustomLabel()).isEqualTo("사전 인적성 스크리닝");
        assertThat(reloadedCustom.getScheduleKind()).isEqualTo(ScheduleKind.UNKNOWN);
        assertThat(reloadedCustom.getStatus()).isEqualTo(EventStatus.UNSCHEDULED);

        ApplicationEvent reloadedCoding = timeline.get(1);
        assertThat(reloadedCoding.getScheduleKind()).isEqualTo(ScheduleKind.EXACT);
        assertThat(reloadedCoding.getScheduledAt()).isEqualTo(reloadedCoding.getStartAt());
        assertThat(reloadedCoding.getEndAt()).isEqualTo(start.plus(2, ChronoUnit.HOURS));
        assertThat(reloadedCoding.getResult()).isEqualTo(EventResult.WAITING);
        @SuppressWarnings("unchecked")
        Map<String, Object> startMeta = (Map<String, Object>) reloadedCoding.getFieldMeta().get("start_at");
        assertThat(startMeta).containsEntry("source", "USER");

        assertThat(timeline.get(2).getScheduledDate()).isEqualTo(LocalDate.of(2026, 9, 18));
    }

    @Test
    void ownerScopedFindersRejectAnotherUsersRows() {
        User a = newUser("a+" + UUID.randomUUID() + "@example.com");
        User b = newUser("b+" + UUID.randomUUID() + "@example.com");
        Link link = newLink(a.getId());
        Application app = newApplication(a.getId(), link.getId(), "default");
        ApplicationEvent ev = new ApplicationEvent();
        ev.setOwnerId(a.getId());
        ev.setApplicationId(app.getId());
        ev.setType(ApplicationEventType.NCS);
        ev.setScheduleKind(ScheduleKind.UNKNOWN);
        em.persist(ev);
        em.flush();
        em.clear();

        // owner A sees everything
        assertThat(links.findByIdAndOwnerId(link.getId(), a.getId())).isPresent();
        assertThat(applications.findByIdAndOwnerId(app.getId(), a.getId())).isPresent();
        assertThat(events.findByIdAndOwnerId(ev.getId(), a.getId())).isPresent();
        assertThat(applications.findByOwnerIdOrderByCreatedAtDescIdDesc(a.getId(), PageRequest.of(0, 20)))
                .isNotEmpty();

        // owner B sees none of A's rows, even with the correct ids
        assertThat(links.findByIdAndOwnerId(link.getId(), b.getId())).isEmpty();
        assertThat(applications.findByIdAndOwnerId(app.getId(), b.getId())).isEmpty();
        assertThat(events.findByIdAndOwnerId(ev.getId(), b.getId())).isEmpty();
        assertThat(events.findByApplicationIdAndOwnerIdOrderBySortOrderAscIdAsc(app.getId(), b.getId()))
                .isEmpty();
        assertThat(events.countByApplicationIdAndOwnerId(app.getId(), b.getId())).isZero();
    }

    @Test
    void customEventWithoutLabelViolatesTheDbCheck() {
        User owner = newUser("c+" + UUID.randomUUID() + "@example.com");
        Link link = newLink(owner.getId());
        Application app = newApplication(owner.getId(), link.getId(), "default");

        em.flush();

        ApplicationEvent bad = new ApplicationEvent();
        bad.setOwnerId(owner.getId());
        bad.setApplicationId(app.getId());
        bad.setType(ApplicationEventType.CUSTOM);
        bad.setCustomLabel("   "); // blank -> NULLIF(btrim(...),'') IS NULL
        bad.setScheduleKind(ScheduleKind.UNKNOWN);

        // Goes through the repository proxy, so the raw Hibernate
        // ConstraintViolationException is translated to Spring's DAO exception.
        assertThatThrownBy(() -> events.saveAndFlush(bad))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("application_events_check");
    }

    @Test
    void jpaVersionIncrementsOnUpdate() {
        User owner = newUser("v+" + UUID.randomUUID() + "@example.com");
        Link link = newLink(owner.getId());
        Application app = newApplication(owner.getId(), link.getId(), "default");
        em.flush();
        em.clear();

        Application loaded = applications.findByIdAndOwnerId(app.getId(), owner.getId()).orElseThrow();
        assertThat(loaded.getVersion()).isZero();
        loaded.setNotes("제출 완료");
        applications.saveAndFlush(loaded);

        assertThat(loaded.getVersion()).isEqualTo(1L);
    }
}
