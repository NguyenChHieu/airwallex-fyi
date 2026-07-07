package com.airwallexfyi.monitor

import com.airwallexfyi.articles.ArticleExtractor
import com.airwallexfyi.articles.ContentHashService
import com.airwallexfyi.articles.ExtractedArticle
import com.airwallexfyi.articles.RichTextFlattener
import com.airwallexfyi.config.AppProperties
import com.airwallexfyi.digests.DailyDigestFormatter
import com.airwallexfyi.digests.DailyDigestService
import com.airwallexfyi.digests.DigestDeliveryPostRepository
import com.airwallexfyi.digests.DigestDeliveryRepository
import com.airwallexfyi.digests.DigestDeliveryStatus
import com.airwallexfyi.digests.DigestEligibilityService
import com.airwallexfyi.digests.DigestMessageType
import com.airwallexfyi.digests.LatestUpdatesService
import com.airwallexfyi.notifications.NotificationResult
import com.airwallexfyi.notifications.NotificationStatus
import com.airwallexfyi.notifications.TelegramNotifier
import com.airwallexfyi.notifications.TelegramSendResponse
import com.airwallexfyi.notifications.TelegramTransport
import com.airwallexfyi.notifications.TelegramUpdate
import com.airwallexfyi.notifications.WhatsAppAlertPayload
import com.airwallexfyi.notifications.WhatsAppNotifier
import com.airwallexfyi.posts.PostRecord
import com.airwallexfyi.posts.PostRepository
import com.airwallexfyi.posts.ProcessingStatus
import com.airwallexfyi.posts.SourceType
import com.airwallexfyi.sources.AirwallexHttpClient
import com.airwallexfyi.sources.AirwallexSourceDiscoveryService
import com.airwallexfyi.sources.SitemapEntry
import com.airwallexfyi.subscribers.SubscriberChannelRecord
import com.airwallexfyi.subscribers.SubscriberChannelRepository
import com.airwallexfyi.subscribers.SubscriberChannelType
import com.airwallexfyi.subscribers.SubscriberRecord
import com.airwallexfyi.subscribers.SubscriberRepository
import com.airwallexfyi.subscribers.SubscriberSeedService
import com.airwallexfyi.subscribers.SubscriberStatus
import com.airwallexfyi.subscribers.TelegramStatusService
import com.airwallexfyi.subscribers.TelegramSubscriptionService
import com.airwallexfyi.state.AppStateRepository
import com.airwallexfyi.summaries.AiSummaryClient
import com.airwallexfyi.summaries.ArticleSummaryService
import com.airwallexfyi.summaries.SummaryGenerationException
import com.airwallexfyi.summaries.SummaryRecord
import com.airwallexfyi.summaries.SummaryRepository
import com.airwallexfyi.summaries.StructuredSummary
import java.time.Instant
import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import tools.jackson.databind.ObjectMapper

@SpringBootTest(
    properties = [
        "airwallex-fyi.source.first-run-seed-limit=25",
        "spring.datasource.url=jdbc:h2:mem:monitor_run_service_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DEFAULT_NULL_ORDERING=HIGH",
    ],
)
class MonitorRunServiceTest @Autowired constructor(
    private val postRepository: PostRepository,
    private val summaryRepository: SummaryRepository,
    private val postStateService: PostStateService,
    private val subscriberRepository: SubscriberRepository,
    private val subscriberChannelRepository: SubscriberChannelRepository,
    private val digestDeliveryRepository: DigestDeliveryRepository,
    private val digestDeliveryPostRepository: DigestDeliveryPostRepository,
    private val objectMapper: ObjectMapper,
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val plainJdbcTemplate: JdbcTemplate,
) {
    @BeforeEach
    fun deleteRows() {
        digestDeliveryPostRepository.deleteAll()
        digestDeliveryRepository.deleteAll()
        summaryRepository.deleteAll()
        postRepository.deleteAll()
        subscriberChannelRepository.deleteAll()
        subscriberRepository.deleteAll()
        plainJdbcTemplate.update("DELETE FROM app_state")
    }

    @Test
    fun `run once seeds discovered articles and sends no change digest without ai`() {
        val urls = listOf(blogUrl("seed-one"), blogUrl("seed-two"))
        val aiClient = FakeAiSummaryClient(summary())
        val notifier = FakeWhatsAppNotifier(NotificationStatus.DRY_RUN)
        val service = monitorService(
            sitemapXml = sitemap(urls),
            articleBodies = urls.associateWith { fixture("/fixtures/airwallex/blog-agentos.html") },
            aiClient = aiClient,
            notifier = notifier,
        )

        val result = service.runOnce()

        assertThat(result.status).isEqualTo(MonitorRunStatus.COMPLETED)
        assertThat(result.sitemapFetched).isTrue()
        assertThat(result.discoveredCount).isEqualTo(2)
        assertThat(result.seededCount).isEqualTo(2)
        assertThat(result.baselinedCount).isZero()
        assertThat(result.newCount).isZero()
        assertThat(result.summarizedCount).isZero()
        assertThat(result.approvalNeededCount).isZero()
        assertThat(result.digestNoChangeCount).isEqualTo(1)
        assertThat(result.digestSentCount).isZero()
        assertThat(result.samplePayloads.single()).isEqualTo(DailyDigestFormatter.NO_CHANGES_TEXT)
        assertThat(result.externalCallsTriggered).isFalse()
        assertThat(result.twilioCallsTriggered).isFalse()
        assertThat(aiClient.calls).isZero()
        assertThat(notifier.calls).isEqualTo(1)
        assertThat(subscriberChannelRepository.count()).isEqualTo(1)
        assertThat(postRepository.findAll().map { it.processingStatus }.toSet()).containsExactly(ProcessingStatus.SEEDED.name)
    }

    @Test
    fun `first run baselines older sitemap urls without extracting them`() {
        val urls = (0..25).map { blogUrl("seed-window-$it") }
        val service = monitorService(
            sitemapXml = sitemap(urls),
            articleBodies = urls.drop(1).associateWith { fixture("/fixtures/airwallex/blog-agentos.html") },
        )

        val result = service.runOnce()

        assertThat(result.status).isEqualTo(MonitorRunStatus.COMPLETED)
        assertThat(result.discoveredCount).isEqualTo(26)
        assertThat(result.seededCount).isEqualTo(25)
        assertThat(result.baselinedCount).isEqualTo(1)
        assertThat(result.failedCount).isZero()
        assertThat(postRepository.findByUrl(urls.first())?.processingStatus).isEqualTo(ProcessingStatus.BASELINED.name)
        assertThat(postRepository.findByUrl(urls.first())?.articleBody).isNull()
        assertThat(postRepository.findByUrl(urls.last())?.processingStatus).isEqualTo(ProcessingStatus.SEEDED.name)
        assertThat(postRepository.findByUrl(urls.last())?.articleBody).isNotBlank()
    }

    @Test
    fun `run once skips known unchanged urls and duplicate same day digest on repeat run`() {
        val urls = listOf(blogUrl("repeat-one"), blogUrl("repeat-two"))
        val notifier = FakeWhatsAppNotifier(NotificationStatus.DRY_RUN)
        val service = monitorService(
            sitemapXml = sitemap(urls),
            articleBodies = urls.associateWith { fixture("/fixtures/airwallex/blog-agentos.html") },
            notifier = notifier,
        )
        service.runOnce()

        val second = service.runOnce()

        assertThat(second.status).isEqualTo(MonitorRunStatus.COMPLETED)
        assertThat(second.seededCount).isZero()
        assertThat(second.baselinedCount).isZero()
        assertThat(second.newCount).isZero()
        assertThat(second.updatedCount).isZero()
        assertThat(second.skippedCount).isEqualTo(2)
        assertThat(second.digestSkippedDuplicateCount).isEqualTo(1)
        assertThat(notifier.calls).isEqualTo(1)
        assertThat(postRepository.count()).isEqualTo(2)
    }

    @Test
    fun `skipped known urls are not rescanned for missing summary approvals`() {
        val url = blogUrl("old-missing-summary")
        val known = saveKnownPost(url)
        postStateService.updateProcessingStatus(known.identifier(), ProcessingStatus.DISCOVERED)
        val service = monitorService(
            sitemapXml = sitemap(listOf(url), lastmod = requireNotNull(known.sitemapLastmod)),
            articleBodies = emptyMap(),
        )

        val result = service.runOnce()

        assertThat(result.skippedCount).isEqualTo(1)
        assertThat(result.approvalNeededCount).isZero()
        assertThat(postRepository.findByUrl(url)?.processingStatus).isEqualTo(ProcessingStatus.DISCOVERED.name)
    }

    @Test
    fun `unchanged update check without summary is marked approval needed`() {
        val url = blogUrl("missing-summary-update-check")
        val originalLastmod = Instant.parse("2026-06-20T00:00:00Z")
        val bumpedLastmod = Instant.parse("2026-06-21T00:00:00Z")
        val articleHtml = fixture("/fixtures/airwallex/blog-agentos.html")
        val extracted = articleExtractor(mapOf(url to articleHtml)).extract(
            SitemapEntry(url = url, sourceType = SourceType.BLOG, sitemapLastmod = originalLastmod),
        )
        val known = saveKnownPost(url, contentHash = extracted.contentHash, sitemapLastmod = originalLastmod)
        postStateService.updateProcessingStatus(known.identifier(), ProcessingStatus.DISCOVERED)
        val aiClient = FakeAiSummaryClient(summary())
        val service = monitorService(
            sitemapXml = sitemap(listOf(url), lastmod = bumpedLastmod),
            articleBodies = mapOf(url to articleHtml),
            aiClient = aiClient,
        )

        val result = service.runOnce()

        assertThat(result.skippedCount).isEqualTo(1)
        assertThat(result.updatedCount).isZero()
        assertThat(result.approvalNeededCount).isEqualTo(1)
        assertThat(result.sampleApprovalNeeded.single().reason).isEqualTo("missing_summary")
        assertThat(result.summarizedCount).isZero()
        assertThat(aiClient.calls).isZero()
        assertThat(postRepository.findByUrl(url)?.processingStatus).isEqualTo(ProcessingStatus.APPROVAL_NEEDED.name)
    }

    @Test
    fun `second run with a new url summarizes once and sends one digest`() {
        saveKnownPost(blogUrl("already-known"))
        val newUrl = blogUrl("fresh-update")
        val aiClient = FakeAiSummaryClient(summary())
        val notifier = FakeWhatsAppNotifier(NotificationStatus.DRY_RUN)
        val service = monitorService(
            sitemapXml = sitemap(listOf(newUrl)),
            articleBodies = mapOf(newUrl to fixture("/fixtures/airwallex/blog-agentos.html")),
            aiClient = aiClient,
            notifier = notifier,
        )

        val result = service.runOnce()

        assertThat(result.status).isEqualTo(MonitorRunStatus.COMPLETED)
        assertThat(result.newCount).isEqualTo(1)
        assertThat(result.summarizedCount).isEqualTo(1)
        assertThat(result.digestSentCount).isEqualTo(1)
        assertThat(result.summaryFailedCount).isZero()
        assertThat(result.samplePayloads.single()).contains("Airwallex FYI -")
        assertThat(result.samplePayloads.single()).contains("Read: $newUrl")
        assertThat(result.externalCallsTriggered).isTrue()
        assertThat(result.twilioCallsTriggered).isFalse()
        assertThat(aiClient.calls).isEqualTo(1)
        assertThat(notifier.calls).isEqualTo(1)
        val post = requireNotNull(postRepository.findByUrl(newUrl))
        assertThat(post.processingStatus).isEqualTo(ProcessingStatus.SUMMARY_READY.name)
        assertThat(summaryRepository.findByPostId(post.identifier())).isNotNull
    }

    @Test
    fun `live notifier success records digest sent and twilio call flag`() {
        saveKnownPost(blogUrl("known-before-live"))
        val newUrl = blogUrl("live-update")
        val notifier = FakeWhatsAppNotifier(NotificationStatus.SENT, twilioCalled = true)
        val service = monitorService(
            sitemapXml = sitemap(listOf(newUrl)),
            articleBodies = mapOf(newUrl to fixture("/fixtures/airwallex/blog-agentos.html")),
            notifier = notifier,
        )

        val result = service.runOnce()

        assertThat(result.digestSentCount).isEqualTo(1)
        assertThat(result.twilioCallsTriggered).isTrue()
        assertThat(postRepository.findByUrl(newUrl)?.processingStatus).isEqualTo(ProcessingStatus.SUMMARY_READY.name)
    }

    @Test
    fun `summary failure is admin visible and sends no change digest`() {
        saveKnownPost(blogUrl("known-before-failure"))
        val newUrl = blogUrl("summary-failure")
        val aiClient = FakeAiSummaryClient(failure = SummaryGenerationException("invalid json"))
        val notifier = FakeWhatsAppNotifier(NotificationStatus.DRY_RUN)
        val service = monitorService(
            sitemapXml = sitemap(listOf(newUrl)),
            articleBodies = mapOf(newUrl to fixture("/fixtures/airwallex/blog-agentos.html")),
            aiClient = aiClient,
            notifier = notifier,
        )

        val result = service.runOnce()

        assertThat(result.status).isEqualTo(MonitorRunStatus.PARTIAL_FAILURE)
        assertThat(result.summaryFailedCount).isEqualTo(1)
        assertThat(result.digestNoChangeCount).isEqualTo(1)
        assertThat(result.sampleErrors.single().reason).contains("Summary failed: invalid json")
        assertThat(notifier.calls).isEqualTo(1)
        val post = requireNotNull(postRepository.findByUrl(newUrl))
        assertThat(post.processingStatus).isEqualTo(ProcessingStatus.SUMMARY_FAILED.name)
        assertThat(summaryRepository.findByPostId(post.identifier())).isNull()
    }

    @Test
    fun `digest failure is recorded without changing canonical post status`() {
        saveKnownPost(blogUrl("known-before-digest-failure"))
        val newUrl = blogUrl("digest-failure")
        val notifier = FakeWhatsAppNotifier(NotificationStatus.FAILED, errorMessage = "twilio down", twilioCalled = true)
        val service = monitorService(
            sitemapXml = sitemap(listOf(newUrl)),
            articleBodies = mapOf(newUrl to fixture("/fixtures/airwallex/blog-agentos.html")),
            notifier = notifier,
        )

        val result = service.runOnce()

        assertThat(result.status).isEqualTo(MonitorRunStatus.PARTIAL_FAILURE)
        assertThat(result.summarizedCount).isEqualTo(1)
        assertThat(result.digestFailedCount).isEqualTo(1)
        assertThat(result.sampleDigestErrors.single()).contains("twilio down")
        assertThat(result.twilioCallsTriggered).isTrue()
        assertThat(notifier.calls).isEqualTo(1)
        assertThat(postRepository.findByUrl(newUrl)?.processingStatus).isEqualTo(ProcessingStatus.SUMMARY_READY.name)
    }

    @Test
    fun `multiple new posts become one combined digest message`() {
        saveKnownPost(blogUrl("known-before-multi"))
        val first = blogUrl("multi-one")
        val second = blogUrl("multi-two")
        val notifier = FakeWhatsAppNotifier(NotificationStatus.DRY_RUN)
        val service = monitorService(
            sitemapXml = sitemap(listOf(first, second)),
            articleBodies = mapOf(
                first to fixture("/fixtures/airwallex/blog-agentos.html"),
                second to fixture("/fixtures/airwallex/blog-agentos.html"),
            ),
            notifier = notifier,
        )

        val result = service.runOnce()

        val channel = requireNotNull(
            subscriberChannelRepository.findByChannelAndRecipient(SubscriberChannelType.WHATSAPP, testProperties().whatsapp.to),
        )
        val delivery = requireNotNull(
            digestDeliveryRepository.findBySubscriberChannelIdAndLocalDate(channel.identifier(), LocalDate.now()),
        )
        assertThat(result.newCount).isEqualTo(2)
        assertThat(result.summarizedCount).isEqualTo(2)
        assertThat(result.digestSentCount).isEqualTo(1)
        assertThat(notifier.calls).isEqualTo(1)
        assertThat(notifier.payloads.single().body).contains("Read: $first")
        assertThat(notifier.payloads.single().body).contains("Read: $second")
        assertThat(digestDeliveryPostRepository.findByDigestDeliveryIdOrderByDisplayOrderAsc(delivery.identifier())).hasSize(2)
    }

    @Test
    fun `one subscriber channel failure does not block another subscriber`() {
        saveKnownPost(blogUrl("known-before-subscriber-failure"))
        val secondChannel = createChannel("whatsapp:+15550000003")
        val newUrl = blogUrl("subscriber-failure")
        val notifier = FakeWhatsAppNotifier(
            status = NotificationStatus.DRY_RUN,
            failingRecipients = setOf(testProperties().whatsapp.to),
        )
        val service = monitorService(
            sitemapXml = sitemap(listOf(newUrl)),
            articleBodies = mapOf(newUrl to fixture("/fixtures/airwallex/blog-agentos.html")),
            notifier = notifier,
        )

        val result = service.runOnce()

        val defaultChannel = requireNotNull(
            subscriberChannelRepository.findByChannelAndRecipient(SubscriberChannelType.WHATSAPP, testProperties().whatsapp.to),
        )
        val failedDelivery = requireNotNull(
            digestDeliveryRepository.findBySubscriberChannelIdAndLocalDate(defaultChannel.identifier(), LocalDate.now()),
        )
        val successfulDelivery = requireNotNull(
            digestDeliveryRepository.findBySubscriberChannelIdAndLocalDate(secondChannel.identifier(), LocalDate.now()),
        )
        assertThat(result.digestFailedCount).isEqualTo(1)
        assertThat(result.digestSentCount).isEqualTo(1)
        assertThat(notifier.calls).isEqualTo(2)
        assertThat(failedDelivery.status).isEqualTo(DigestDeliveryStatus.FAILED)
        assertThat(successfulDelivery.status).isEqualTo(DigestDeliveryStatus.DRY_RUN)
    }

    @Test
    fun `known url content hash change becomes approval needed and stays out of digest`() {
        val url = blogUrl("changed-known")
        val existing = saveKnownPost(url, contentHash = "old-hash", sitemapLastmod = Instant.parse("2026-06-20T00:00:00Z"))
        summaryRepository.save(SummaryRecord.from(existing.identifier(), summary(), "gemini-test", "gemini-summary-v1", objectMapper))
        val aiClient = FakeAiSummaryClient(summary())
        val notifier = FakeWhatsAppNotifier(NotificationStatus.DRY_RUN)
        val service = monitorService(
            sitemapXml = sitemap(listOf(url), lastmod = Instant.parse("2026-06-21T00:00:00Z")),
            articleBodies = mapOf(url to fixture("/fixtures/airwallex/blog-agentos.html")),
            aiClient = aiClient,
            notifier = notifier,
        )

        val result = service.runOnce()

        assertThat(result.updatedCount).isEqualTo(1)
        assertThat(result.approvalNeededCount).isEqualTo(1)
        assertThat(result.sampleApprovalNeeded.single().reason).isEqualTo("content_changed")
        assertThat(result.summarizedCount).isZero()
        assertThat(result.digestNoChangeCount).isEqualTo(1)
        assertThat(notifier.payloads.single().body).isEqualTo(DailyDigestFormatter.NO_CHANGES_TEXT)
        assertThat(aiClient.calls).isZero()
        assertThat(postRepository.findByUrl(url)?.processingStatus).isEqualTo(ProcessingStatus.APPROVAL_NEEDED.name)
        assertThat(summaryRepository.count()).isEqualTo(1)
    }

    @Test
    fun `per article extraction failure is sampled and does not stop the run`() {
        val good = blogUrl("good")
        val bad = blogUrl("bad")
        val notifier = FakeWhatsAppNotifier(NotificationStatus.DRY_RUN)
        val service = monitorService(
            sitemapXml = sitemap(listOf(good, bad)),
            articleBodies = mapOf(good to fixture("/fixtures/airwallex/blog-agentos.html")),
            notifier = notifier,
        )

        val result = service.runOnce()

        assertThat(result.status).isEqualTo(MonitorRunStatus.PARTIAL_FAILURE)
        assertThat(result.seededCount).isEqualTo(1)
        assertThat(result.approvalNeededCount).isZero()
        assertThat(result.failedCount).isEqualTo(1)
        assertThat(result.digestNoChangeCount).isEqualTo(1)
        val error = result.sampleErrors.single()
        assertThat(error.url).isEqualTo(bad)
        assertThat(error.reason).contains("missing fixture")
        assertThat(postRepository.count()).isEqualTo(1)
        assertThat(notifier.calls).isEqualTo(1)
    }

    @Test
    fun `sitemap discovery failure writes no posts and does not run digest`() {
        val notifier = FakeWhatsAppNotifier(NotificationStatus.DRY_RUN)
        val service = monitorService(
            sitemapXml = "",
            articleBodies = emptyMap(),
            notifier = notifier,
            sourceDiscoveryService = AirwallexSourceDiscoveryService(testProperties(), ThrowingHttpClient(IllegalStateException("sitemap down"))),
        )

        val result = service.runOnce()

        assertThat(result.status).isEqualTo(MonitorRunStatus.FAILED)
        assertThat(result.sitemapFetched).isFalse()
        assertThat(result.failedCount).isEqualTo(1)
        assertThat(result.summarizedCount).isZero()
        assertThat(result.digestSentCount).isZero()
        assertThat(result.digestNoChangeCount).isZero()
        assertThat(result.twilioCallsTriggered).isFalse()
        assertThat(notifier.calls).isZero()
        val error = result.sampleErrors.single()
        assertThat(error.url).isNull()
        assertThat(error.reason).contains("sitemap down")
        assertThat(postRepository.count()).isZero()
    }

    private fun monitorService(
        sitemapXml: String,
        articleBodies: Map<String, String>,
        aiClient: FakeAiSummaryClient = FakeAiSummaryClient(summary()),
        notifier: FakeWhatsAppNotifier = FakeWhatsAppNotifier(NotificationStatus.DRY_RUN),
        telegramNotifier: FakeTelegramNotifier = FakeTelegramNotifier(),
        properties: AppProperties = testProperties(),
        sourceDiscoveryService: AirwallexSourceDiscoveryService = AirwallexSourceDiscoveryService(properties, StaticHttpClient(sitemapXml)),
    ): MonitorRunService {
        val eligibilityService = DigestEligibilityService(summaryRepository, postRepository)
        return MonitorRunService(
            sourceDiscoveryService = sourceDiscoveryService,
            articleExtractor = articleExtractor(articleBodies),
            postStateService = postStateService,
            summaryRepository = summaryRepository,
            articleSummaryService = summaryService(aiClient, properties),
            subscriberSeedService = SubscriberSeedService(properties, subscriberRepository, subscriberChannelRepository),
            telegramSubscriptionService = TelegramSubscriptionService(
                properties = properties,
                telegramTransport = FakeTelegramTransport(),
                appStateRepository = AppStateRepository(plainJdbcTemplate),
                subscriberRepository = subscriberRepository,
                subscriberChannelRepository = subscriberChannelRepository,
                latestUpdatesService = LatestUpdatesService(summaryRepository, postRepository, objectMapper),
                telegramStatusService = TelegramStatusService(
                    properties = properties,
                    subscriberChannelRepository = subscriberChannelRepository,
                    digestDeliveryRepository = digestDeliveryRepository,
                    postRepository = postRepository,
                ),
            ),
            dailyDigestService = DailyDigestService(
                properties = properties,
                subscriberChannelRepository = subscriberChannelRepository,
                digestDeliveryRepository = digestDeliveryRepository,
                digestDeliveryPostRepository = digestDeliveryPostRepository,
                digestEligibilityService = eligibilityService,
                dailyDigestFormatter = DailyDigestFormatter(objectMapper),
                whatsAppNotifier = notifier,
                telegramNotifier = telegramNotifier,
            ),
        )
    }

    private fun summaryService(aiClient: AiSummaryClient, properties: AppProperties): ArticleSummaryService = ArticleSummaryService(
        aiSummaryClient = aiClient,
        summaryRepository = summaryRepository,
        objectMapper = objectMapper,
        properties = properties.copy(ai = AppProperties.Ai(model = "gemini-test")),
        jdbcTemplate = jdbcTemplate,
    )

    private fun articleExtractor(articleBodies: Map<String, String>): ArticleExtractor = ArticleExtractor(
        httpClient = MapHttpClient(articleBodies),
        richTextFlattener = RichTextFlattener(),
        contentHashService = ContentHashService(),
    )

    private fun saveKnownPost(
        url: String,
        contentHash: String = "known-hash",
        sitemapLastmod: Instant = Instant.parse("2026-06-20T00:00:00Z"),
    ): PostRecord = postRepository.save(
        PostRecord(
            url = url,
            sourceType = SourceType.BLOG.name,
            title = "Known post",
            description = "Already known",
            author = "Airwallex",
            publishedAt = sitemapLastmod,
            sitemapLastmod = sitemapLastmod,
            discoveredAt = sitemapLastmod,
            contentHash = contentHash,
            articleBody = "Known article body that is long enough for a stored post.",
            processingStatus = ProcessingStatus.ALERT_SENT.name,
        ),
    )

    private fun createChannel(recipient: String): SubscriberChannelRecord {
        val now = Instant.parse("2026-06-22T00:00:00Z")
        val subscriber = subscriberRepository.save(
            SubscriberRecord(displayName = "Subscriber $recipient", createdAt = now, updatedAt = now),
        )
        return subscriberChannelRepository.save(
            SubscriberChannelRecord(
                subscriberId = subscriber.identifier(),
                channel = SubscriberChannelType.WHATSAPP,
                recipient = recipient,
                status = SubscriberStatus.ACTIVE,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun sitemap(urls: List<String>, lastmod: Instant = Instant.parse("2026-06-20T00:00:00Z")): String = buildString {
        appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        appendLine("<urlset>")
        urls.forEachIndexed { index, url ->
            appendLine("  <url>")
            appendLine("    <loc>$url</loc>")
            appendLine("    <lastmod>${lastmod.plusSeconds(index.toLong())}</lastmod>")
            appendLine("  </url>")
        }
        appendLine("</urlset>")
    }

    private fun summary(): StructuredSummary = StructuredSummary.validated(
        headline = "Airwallex updates its platform",
        bullets = listOf(
            "New payment tooling shipped",
            "Finance teams get faster workflows",
            "The update links back to the source",
        ),
        whyItMatters = "It points to Airwallex investing in operational finance workflows.",
        tags = listOf("payments", "platform"),
        sourceType = SourceType.BLOG,
    )

    private fun testProperties(): AppProperties = AppProperties(
        whatsapp = AppProperties.WhatsApp(to = "whatsapp:+15550000002"),
    )

    private fun blogUrl(slug: String): String = "https://www.airwallex.com/global/blog/$slug"

    private fun fixture(path: String): String =
        requireNotNull(javaClass.getResource(path)) { "Missing fixture $path" }.readText()

    private class StaticHttpClient(private val body: String) : AirwallexHttpClient {
        override fun fetchText(url: String): String = body
    }

    private class MapHttpClient(private val bodies: Map<String, String>) : AirwallexHttpClient {
        override fun fetchText(url: String): String =
            bodies[url] ?: throw IllegalStateException("missing fixture for $url")
    }

    private class ThrowingHttpClient(private val failure: RuntimeException) : AirwallexHttpClient {
        override fun fetchText(url: String): String = throw failure
    }

    private class FakeAiSummaryClient(
        private val summary: StructuredSummary? = null,
        private val failure: RuntimeException? = null,
    ) : AiSummaryClient {
        var calls = 0

        override fun summarize(article: ExtractedArticle): StructuredSummary {
            calls += 1
            failure?.let { throw it }
            return requireNotNull(summary)
        }
    }

    private class FakeWhatsAppNotifier(
        private val status: NotificationStatus,
        private val errorMessage: String? = null,
        private val twilioCalled: Boolean = false,
        private val failingRecipients: Set<String> = emptySet(),
    ) : WhatsAppNotifier {
        var calls = 0
        val payloads = mutableListOf<WhatsAppAlertPayload>()

        override fun send(payload: WhatsAppAlertPayload): NotificationResult {
            calls += 1
            payloads += payload
            if (payload.recipient in failingRecipients) {
                return NotificationResult(
                    status = NotificationStatus.FAILED,
                    payloadPreview = payload.preview,
                    errorMessage = "simulated failure for ${payload.recipient}",
                    twilioCalled = false,
                )
            }
            return NotificationResult(
                status = status,
                payloadPreview = payload.preview,
                providerMessageId = if (status == NotificationStatus.SENT) "SM123" else null,
                errorMessage = errorMessage,
                twilioCalled = twilioCalled,
            )
        }
    }

    private class FakeTelegramNotifier : TelegramNotifier {
        override fun send(payload: WhatsAppAlertPayload): NotificationResult = NotificationResult(
            status = NotificationStatus.DRY_RUN,
            payloadPreview = payload.preview,
            twilioCalled = false,
        )
    }

    private class FakeTelegramTransport : TelegramTransport {
        override fun sendMessage(botToken: String, chatId: String, body: String): TelegramSendResponse =
            TelegramSendResponse("telegram-test-message")

        override fun getUpdates(botToken: String, offset: Long?): List<TelegramUpdate> = emptyList()
    }
}

