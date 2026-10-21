package com.theclub.clubservice.service;

import com.theclub.clubservice.client.UserClient;
import com.theclub.clubservice.dto.ChatDtos;
import com.theclub.clubservice.dto.PageResponse;
import com.theclub.clubservice.dto.UploadedImage;
import com.theclub.clubservice.model.ChatBlock;
import com.theclub.clubservice.model.ChatConversation;
import com.theclub.clubservice.model.ChatMessage;
import com.theclub.clubservice.model.ChatRead;
import com.theclub.clubservice.model.ChatReport;
import com.theclub.clubservice.model.ChatReportStatus;
import com.theclub.clubservice.model.Club;
import com.theclub.clubservice.model.ClubChatMute;
import com.theclub.clubservice.model.ConversationKind;
import com.theclub.clubservice.model.MembershipRole;
import com.theclub.clubservice.repository.ChatBlockRepository;
import com.theclub.clubservice.repository.ChatConversationRepository;
import com.theclub.clubservice.repository.ChatMessageRepository;
import com.theclub.clubservice.repository.ChatReadRepository;
import com.theclub.clubservice.repository.ChatReportRepository;
import com.theclub.clubservice.repository.ClubChatMuteRepository;
import com.theclub.clubservice.repository.ClubMembershipRepository;
import com.theclub.common.media.MediaProperties;
import com.theclub.common.media.MediaStore;
import com.theclub.common.observability.BusinessMetrics;
import com.theclub.common.security.AuthenticatedUser;
import com.theclub.common.web.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Club group chats (the club's subscribers and owner) and direct messages (two people who share a club).
 * Messages are stored in MySQL first and then pushed to whoever is connected ({@link ChatHub}).
 */
@Service
public class ChatService {

    static final int DEFAULT_PAGE = 50;
    static final int MAX_PAGE = 100;
    /** Stand-in for "nobody", because {@code not in ()} isn't valid SQL. */
    static final UUID NOBODY = new UUID(0, 0);
    static final String TYPE_CHAT_MESSAGE = "CHAT_MESSAGE";
    static final String REASON_MUTED = "MUTED";
    static final String REASON_BLOCKED = "BLOCKED";
    static final String REASON_NOT_CONNECTED = "NOT_CONNECTED";
    /** How far back {@link #relayRecent()} looks for messages written on other replicas. */
    static final Duration RELAY_WINDOW = Duration.ofSeconds(10);

    private final ChatConversationRepository conversations;
    private final ChatMessageRepository messages;
    private final ChatReadRepository reads;
    private final ChatBlockRepository blocks;
    private final ChatReportRepository reports;
    private final ClubChatMuteRepository mutes;
    private final ClubMembershipRepository memberships;
    private final ClubService clubService;
    private final NotificationPublisher notifications;
    private final MediaStore media;
    private final MediaProperties mediaProperties;
    private final ChatHub hub;
    private final Clock clock;
    private final TransactionTemplate readOnly;
    private final TransactionTemplate newTransaction;

    public ChatService(ChatConversationRepository conversations, ChatMessageRepository messages, ChatReadRepository reads,
                       ChatBlockRepository blocks, ChatReportRepository reports, ClubChatMuteRepository mutes,
                       ClubMembershipRepository memberships, ClubService clubService, NotificationPublisher notifications,
                       MediaStore media, MediaProperties mediaProperties, ChatHub hub, Clock clock,
                       PlatformTransactionManager transactions) {
        this.conversations = conversations;
        this.messages = messages;
        this.reads = reads;
        this.blocks = blocks;
        this.reports = reports;
        this.mutes = mutes;
        this.memberships = memberships;
        this.clubService = clubService;
        this.notifications = notifications;
        this.media = media;
        this.mediaProperties = mediaProperties;
        this.hub = hub;
        this.clock = clock;
        this.readOnly = new TransactionTemplate(transactions);
        this.readOnly.setReadOnly(true);
        this.newTransaction = new TransactionTemplate(transactions);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ---- Conversations ------------------------------------------------------------------------------------------

    /** The caller's clubs' group chats and their direct conversations, most recent first. */
    @Transactional(readOnly = true)
    public List<ChatDtos.Conversation> inbox(AuthenticatedUser caller) {
        UUID me = caller.id();
        List<Club> clubs = clubService.clubsOf(me);
        Map<UUID, ChatConversation> groups = clubs.isEmpty() ? Map.of()
                : conversations.findByClubIdIn(clubs.stream().map(Club::getId).toList()).stream()
                .collect(Collectors.toMap(ChatConversation::getClubId, Function.identity()));
        List<ChatConversation> directs = conversations.findDirectOf(me);

        List<ChatConversation> all = new ArrayList<>(groups.values());
        all.addAll(directs);
        Context context = context(me, all);
        Map<UUID, UserClient.UserSummary> others = clubService.profilesOf(
                directs.stream().map(c -> c.otherThan(me)).collect(Collectors.toSet()));

        List<ChatDtos.Conversation> inbox = new ArrayList<>();
        for (Club club : clubs) {
            inbox.add(group(club, groups.get(club.getId()), caller, context));
        }
        for (ChatConversation direct : directs) {
            inbox.add(direct(direct, caller, others.get(direct.otherThan(me)), context));
        }
        inbox.sort(Comparator.comparing(ChatDtos.Conversation::lastMessageAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ChatDtos.Conversation::title, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        return inbox;
    }

    /** Opens a club's group chat (created the first time anyone opens it). */
    public ChatDtos.Conversation openClub(AuthenticatedUser caller, UUID clubId) {
        Club club = clubService.get(clubId);
        requireGroupAccess(club, caller);
        ChatConversation conversation = getOrCreate(() -> conversations.findByClubId(clubId),
                () -> ChatConversation.group(clubId, now()));
        return readOnly.execute(tx -> describe(conversation.getId(), caller));
    }

    /** Opens (or starts) a direct conversation with someone who shares a club with the caller. */
    public ChatDtos.Conversation openDirect(AuthenticatedUser caller, UUID userId) {
        UUID me = caller.id();
        if (me.equals(userId)) {
            throw ApiException.badRequest("You can't message yourself");
        }
        UUID low = ChatConversation.lower(me, userId);
        UUID high = low.equals(me) ? userId : me;
        var existing = conversations.findByUserLowAndUserHigh(low, high);
        if (existing.isEmpty()) {
            if (!memberships.shareAClub(me, userId)) {
                throw notConnected();
            }
            if (blocks.blockedEitherWay(me, userId)) {
                throw blocked();
            }
        }
        ChatConversation conversation = existing.orElseGet(() -> getOrCreate(
                () -> conversations.findByUserLowAndUserHigh(low, high),
                () -> ChatConversation.direct(me, userId, now())));
        return readOnly.execute(tx -> describe(conversation.getId(), caller));
    }

    @Transactional(readOnly = true)
    public ChatDtos.Conversation get(AuthenticatedUser caller, UUID conversationId) {
        return describe(conversationId, caller);
    }

    /** Oldest first; {@code before} (a message ID) pages back through older messages. */
    @Transactional(readOnly = true)
    public List<ChatDtos.Message> messages(AuthenticatedUser caller, UUID conversationId, UUID before, Integer limit) {
        ChatConversation conversation = accessible(conversationId, caller);
        int size = limit == null ? DEFAULT_PAGE : Math.min(Math.max(limit, 1), MAX_PAGE);
        List<ChatMessage> page = new ArrayList<>(messages.page(conversation.getId(), before, hidden(caller.id()),
                PageRequest.of(0, size)));
        page.sort(Comparator.comparing(m -> m.getId().toString()));
        return describe(page);
    }

    @Transactional
    public ChatDtos.Message send(AuthenticatedUser caller, UUID conversationId, ChatDtos.SendMessageRequest request) {
        ChatConversation conversation = accessible(conversationId, caller);
        String reason = whyCantSend(conversation, caller);
        if (REASON_MUTED.equals(reason)) {
            throw ApiException.forbidden("The club owner muted you in this chat").withCode("CHAT_MUTED");
        }
        if (REASON_BLOCKED.equals(reason)) {
            throw blocked();
        }
        if (REASON_NOT_CONNECTED.equals(reason)) {
            throw notConnected();
        }
        String body = request.body() == null || request.body().isBlank() ? null : request.body().strip();
        String photo = request.photoUrl() == null || request.photoUrl().isBlank() ? null : request.photoUrl().strip();
        if (body == null && photo == null) {
            throw ApiException.badRequest("Write a message or add a photo");
        }
        if (photo != null && !photo.startsWith(photoFolderUrl(caller.id()))) {
            throw ApiException.badRequest("Upload the photo with POST /api/v1/chat/photos first");
        }

        boolean firstUnread = !conversation.isGroup()
                && unreadFor(conversation.otherThan(caller.id()), conversation.getId()) == 0;
        LocalDateTime now = now();
        ChatMessage message = messages.save(new ChatMessage(conversation.getId(), caller.id(), body, photo, now));
        conversation.setLastMessageAt(now);
        BusinessMetrics.increment("chat.messages", "kind", conversation.getKind().name().toLowerCase(Locale.ROOT));

        ChatDtos.Message response = describe(List.of(message)).getFirst();
        Set<UUID> recipients = recipients(conversation, caller.id());
        afterCommit(() -> {
            if (hub.claim(response)) {
                hub.deliver(recipients, response);
            }
        });
        // One notification when a direct conversation gets its first unread message, not one per message
        if (firstUnread) {
            notifications.notifyAfterCommit(conversation.otherThan(caller.id()), TYPE_CHAT_MESSAGE,
                    caller.name() + " sent you a message");
        }
        return response;
    }

    /** Everything in the conversation up to now counts as read. */
    public void markRead(AuthenticatedUser caller, UUID conversationId) {
        ChatConversation conversation = readOnly.execute(tx -> accessible(conversationId, caller));
        LocalDateTime now = now();
        try {
            newTransaction.executeWithoutResult(tx -> reads.findByConversationIdAndUserId(conversation.getId(), caller.id())
                    .ifPresentOrElse(r -> r.setLastReadAt(now),
                            () -> reads.saveAndFlush(new ChatRead(conversation.getId(), caller.id(), now))));
        } catch (DataIntegrityViolationException e) {
            // another device marked it read at the same moment
            newTransaction.executeWithoutResult(tx -> reads.findByConversationIdAndUserId(conversation.getId(), caller.id())
                    .ifPresent(r -> r.setLastReadAt(now)));
        }
    }

    /** The sender, and in a club chat the club owner, can delete a message; admins can delete any. */
    @Transactional
    public void deleteMessage(AuthenticatedUser caller, UUID messageId) {
        ChatMessage message = messages.findById(messageId).orElseThrow(() -> ApiException.notFound("Message not found"));
        ChatConversation conversation = accessible(message.getConversationId(), caller);
        if (message.isDeleted()) {
            return;
        }
        boolean moderator = conversation.isGroup() && ClubService.isOwnerOrAdmin(clubService.get(conversation.getClubId()), caller);
        if (!message.getSenderId().equals(caller.id()) && !moderator && !caller.isAdmin()) {
            throw ApiException.forbidden("You can only delete your own messages");
        }
        remove(message, conversation, caller.id());
    }

    private void remove(ChatMessage message, ChatConversation conversation, UUID by) {
        message.delete(by, now());
        ChatDtos.Message response = describe(List.of(message)).getFirst();
        Set<UUID> recipients = recipients(conversation, message.getSenderId());
        afterCommit(() -> {
            if (hub.claim(response)) {
                hub.deliver(recipients, response);
            }
        });
    }

    /** Reports a message to the admins; reporting it again does nothing. */
    @Transactional
    public void report(AuthenticatedUser caller, UUID messageId, ChatDtos.ReportRequest request) {
        ChatMessage message = messages.findById(messageId).orElseThrow(() -> ApiException.notFound("Message not found"));
        accessible(message.getConversationId(), caller);
        if (message.getSenderId().equals(caller.id())) {
            throw ApiException.badRequest("You can't report your own message");
        }
        if (reports.existsByMessageIdAndReporterId(messageId, caller.id())) {
            return;
        }
        String reason = request == null || request.reason() == null || request.reason().isBlank() ? null : request.reason().strip();
        reports.save(new ChatReport(messageId, caller.id(), reason, now()));
        BusinessMetrics.increment("chat.reports");
    }

    public UploadedImage uploadPhoto(AuthenticatedUser caller, MultipartFile file) {
        return new UploadedImage(media.storeImage("chat/" + caller.id(), file).url());
    }

    // ---- Blocks -------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ChatDtos.BlockedUser> blocked(AuthenticatedUser caller) {
        List<ChatBlock> mine = blocks.findByBlockerIdOrderByCreatedAtDesc(caller.id());
        Map<UUID, UserClient.UserSummary> people = clubService.profilesOf(
                mine.stream().map(ChatBlock::getBlockedId).collect(Collectors.toSet()));
        return mine.stream().map(b -> {
            UserClient.UserSummary p = people.get(b.getBlockedId());
            return new ChatDtos.BlockedUser(b.getBlockedId(), p == null ? null : p.name(),
                    p == null ? null : p.profilePicUrl(), b.getCreatedAt());
        }).toList();
    }

    public void block(AuthenticatedUser caller, UUID userId) {
        if (caller.id().equals(userId)) {
            throw ApiException.badRequest("You can't block yourself");
        }
        if (blocks.findByBlockerIdAndBlockedId(caller.id(), userId).isPresent()) {
            return;
        }
        try {
            blocks.save(new ChatBlock(caller.id(), userId, now()));
            BusinessMetrics.increment("chat.blocks");
        } catch (DataIntegrityViolationException e) {
            // blocked from another device at the same moment
        }
    }

    @Transactional
    public void unblock(AuthenticatedUser caller, UUID userId) {
        blocks.findByBlockerIdAndBlockedId(caller.id(), userId).ifPresent(blocks::delete);
    }

    // ---- Club chat moderation -----------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ChatDtos.Mute> mutes(AuthenticatedUser caller, UUID clubId) {
        Club club = clubService.get(clubId);
        ClubService.requireOwnerOrAdmin(club, caller);
        LocalDateTime now = now();
        List<ClubChatMute> active = mutes.findByClubIdOrderByCreatedAtDesc(clubId).stream()
                .filter(m -> m.activeAt(now)).toList();
        Map<UUID, String> names = clubService.namesOf(active.stream().map(ClubChatMute::getUserId).toList());
        return active.stream().map(m -> new ChatDtos.Mute(m.getUserId(), names.get(m.getUserId()), m.getMutedUntil(),
                m.getCreatedAt())).toList();
    }

    @Transactional
    public ChatDtos.Mute mute(AuthenticatedUser caller, UUID clubId, UUID userId, ChatDtos.MuteRequest request) {
        Club club = clubService.get(clubId);
        ClubService.requireOwnerOrAdmin(club, caller);
        if (club.getOwnerId().equals(userId)) {
            throw ApiException.badRequest("The club owner can't be muted");
        }
        LocalDateTime now = now();
        LocalDateTime until = request == null || request.hours() == null ? null : now.plusHours(request.hours());
        ClubChatMute mute = mutes.findByClubIdAndUserId(clubId, userId).orElse(null);
        if (mute == null) {
            mute = mutes.save(new ClubChatMute(clubId, userId, caller.id(), until, now));
        } else {
            mute.setMutedUntil(until);
            mute.setMutedBy(caller.id());
        }
        BusinessMetrics.increment("chat.mutes");
        return new ChatDtos.Mute(userId, clubService.namesOf(List.of(userId)).get(userId), until, now);
    }

    @Transactional
    public void unmute(AuthenticatedUser caller, UUID clubId, UUID userId) {
        Club club = clubService.get(clubId);
        ClubService.requireOwnerOrAdmin(club, caller);
        mutes.findByClubIdAndUserId(clubId, userId).ifPresent(mutes::delete);
    }

    // ---- Reports (admins) ---------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<ChatDtos.Report> reports(AuthenticatedUser caller, ChatReportStatus status, int page, int size) {
        requireAdmin(caller);
        Page<ChatReport> result = reports.findByStatusOrderByCreatedAtAsc(status == null ? ChatReportStatus.OPEN : status,
                ClubService.pageRequest(page, size));
        Map<UUID, ChatMessage> reported = messages.findAllById(
                        result.getContent().stream().map(ChatReport::getMessageId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(ChatMessage::getId, Function.identity()));
        Map<UUID, ChatConversation> where = conversations.findAllById(
                        reported.values().stream().map(ChatMessage::getConversationId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(ChatConversation::getId, Function.identity()));
        Map<UUID, Club> clubs = clubService.getAll(where.values().stream().map(ChatConversation::getClubId)
                        .filter(Objects::nonNull).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Club::getId, Function.identity()));
        Set<UUID> people = new HashSet<>();
        result.getContent().forEach(r -> people.add(r.getReporterId()));
        reported.values().forEach(m -> people.add(m.getSenderId()));
        Map<UUID, UserClient.UserSummary> profiles = clubService.profilesOf(people);

        return PageResponse.of(result, r -> {
            ChatMessage m = reported.get(r.getMessageId());
            ChatConversation c = m == null ? null : where.get(m.getConversationId());
            Club club = c == null || c.getClubId() == null ? null : clubs.get(c.getClubId());
            UserClient.UserSummary reporter = profiles.get(r.getReporterId());
            // admins see what was written even when it has been deleted since
            ChatDtos.Message message = m == null ? null : message(m, profiles.get(m.getSenderId()), true);
            return new ChatDtos.Report(r.getId(), r.getStatus(), r.getReason(), r.getReporterId(),
                    reporter == null ? null : reporter.name(), message, c == null ? null : c.getKind(),
                    club == null ? null : club.getId(), club == null ? null : club.getName(), r.getCreatedAt(),
                    r.getResolvedAt());
        });
    }

    /** Removes the reported message (closing every open report about it) or dismisses this report. */
    @Transactional
    public void resolve(AuthenticatedUser caller, UUID reportId, ChatDtos.ResolveReportRequest request) {
        requireAdmin(caller);
        ChatReport report = reports.findById(reportId).orElseThrow(() -> ApiException.notFound("Report not found"));
        if (report.getStatus() != ChatReportStatus.OPEN) {
            return;
        }
        LocalDateTime now = now();
        if (request != null && request.removeMessage()) {
            ChatMessage message = messages.findById(report.getMessageId()).orElse(null);
            if (message != null && !message.isDeleted()) {
                ChatConversation conversation = conversations.findById(message.getConversationId()).orElseThrow();
                remove(message, conversation, caller.id());
            }
            reports.findByMessageIdAndStatus(report.getMessageId(), ChatReportStatus.OPEN)
                    .forEach(r -> r.resolve(ChatReportStatus.REMOVED, caller.id(), now));
        } else {
            report.resolve(ChatReportStatus.DISMISSED, caller.id(), now);
        }
    }

    // ---- Live updates -------------------------------------------------------------------------------------------

    public SseEmitter stream(AuthenticatedUser caller) {
        return hub.connect(caller.id());
    }

    /**
     * Passes on messages written or deleted on other replicas in the last few seconds to this replica's connections.
     * Messages this replica delivered itself are skipped.
     */
    @Transactional(readOnly = true)
    public void relayRecent() {
        if (hub.idle()) {
            return;
        }
        List<ChatMessage> changed = messages.changedSince(now().minus(RELAY_WINDOW), PageRequest.of(0, 500)).stream()
                .filter(m -> !hub.alreadyDelivered(m.getId(), m.isDeleted()))
                .toList();
        if (changed.isEmpty()) {
            return;
        }
        Map<UUID, ChatConversation> where = conversations.findAllById(
                        changed.stream().map(ChatMessage::getConversationId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(ChatConversation::getId, Function.identity()));
        for (ChatDtos.Message message : describe(changed)) {
            ChatConversation conversation = where.get(message.conversationId());
            if (conversation != null && hub.claim(message)) {
                hub.deliver(recipients(conversation, message.senderId()), message);
            }
        }
    }

    // ---- Helpers ------------------------------------------------------------------------------------------------

    private record Context(Map<UUID, Long> unread, Map<UUID, ChatMessage> last,
                           Map<UUID, UserClient.UserSummary> senders, Map<UUID, ClubChatMute> mutes) {
    }

    private Context context(UUID me, Collection<ChatConversation> all) {
        List<UUID> ids = all.stream().map(ChatConversation::getId).toList();
        List<UUID> hidden = hidden(me);
        Map<UUID, Long> unread = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] row : messages.unreadCounts(me, ids, hidden)) {
                unread.put((UUID) row[0], ((Number) row[1]).longValue());
            }
        }
        Map<UUID, ChatMessage> last = new HashMap<>();
        for (UUID id : ids) {
            messages.latest(id, hidden, PageRequest.of(0, 1)).stream().findFirst().ifPresent(m -> last.put(id, m));
        }
        Map<UUID, UserClient.UserSummary> senders = clubService.profilesOf(
                last.values().stream().map(ChatMessage::getSenderId).collect(Collectors.toSet()));
        LocalDateTime now = now();
        Map<UUID, ClubChatMute> myMutes = mutes.findByUserId(me).stream().filter(m -> m.activeAt(now))
                .collect(Collectors.toMap(ClubChatMute::getClubId, Function.identity()));
        return new Context(unread, last, senders, myMutes);
    }

    private ChatDtos.Conversation describe(UUID conversationId, AuthenticatedUser caller) {
        ChatConversation conversation = accessible(conversationId, caller);
        Context context = context(caller.id(), List.of(conversation));
        if (conversation.isGroup()) {
            return group(clubService.get(conversation.getClubId()), conversation, caller, context);
        }
        UUID other = conversation.otherThan(caller.id());
        return direct(conversation, caller, clubService.profilesOf(List.of(other)).get(other), context);
    }

    private ChatDtos.Conversation group(Club club, ChatConversation conversation, AuthenticatedUser caller,
                                        Context context) {
        ClubChatMute mute = context.mutes().get(club.getId());
        boolean moderator = ClubService.isOwnerOrAdmin(club, caller);
        boolean muted = mute != null && !moderator;
        UUID id = conversation == null ? null : conversation.getId();
        return new ChatDtos.Conversation(id, ConversationKind.GROUP, club.getId(),
                club.getName(), club.getLogoUrl(), null, last(id, context),
                conversation == null ? null : conversation.getLastMessageAt(),
                id == null ? 0 : context.unread().getOrDefault(id, 0L), !muted, muted ? REASON_MUTED : null,
                muted ? mute.getMutedUntil() : null, moderator);
    }

    private ChatDtos.Conversation direct(ChatConversation conversation, AuthenticatedUser caller,
                                         UserClient.UserSummary other, Context context) {
        String reason = whyCantSend(conversation, caller);
        UUID otherId = conversation.otherThan(caller.id());
        return new ChatDtos.Conversation(conversation.getId(), conversation.getKind(), null,
                other == null ? null : other.name(), other == null ? null : other.profilePicUrl(), otherId,
                last(conversation.getId(), context), conversation.getLastMessageAt(),
                context.unread().getOrDefault(conversation.getId(), 0L), reason == null, reason, null, false);
    }

    private ChatDtos.Message last(UUID conversationId, Context context) {
        ChatMessage m = conversationId == null ? null : context.last().get(conversationId);
        return m == null ? null : message(m, context.senders().get(m.getSenderId()), false);
    }

    /** null when the caller may write in the conversation, otherwise MUTED, BLOCKED or NOT_CONNECTED. */
    private String whyCantSend(ChatConversation conversation, AuthenticatedUser caller) {
        if (conversation.isGroup()) {
            Club club = clubService.get(conversation.getClubId());
            if (ClubService.isOwnerOrAdmin(club, caller)) {
                return null;
            }
            return mutes.findByClubIdAndUserId(club.getId(), caller.id()).filter(m -> m.activeAt(now())).isPresent()
                    ? REASON_MUTED : null;
        }
        UUID other = conversation.otherThan(caller.id());
        if (blocks.blockedEitherWay(caller.id(), other)) {
            return REASON_BLOCKED;
        }
        return memberships.shareAClub(caller.id(), other) ? null : REASON_NOT_CONNECTED;
    }

    /** The conversation, if the caller may read it: 403 for a club chat they're not part of, 404 for others' DMs. */
    private ChatConversation accessible(UUID conversationId, AuthenticatedUser caller) {
        ChatConversation conversation = conversations.findById(conversationId)
                .orElseThrow(() -> ApiException.notFound("Conversation not found"));
        if (conversation.isGroup()) {
            requireGroupAccess(clubService.get(conversation.getClubId()), caller);
        } else if (!conversation.hasParticipant(caller.id())) {
            throw ApiException.notFound("Conversation not found");
        }
        return conversation;
    }

    private void requireGroupAccess(Club club, AuthenticatedUser caller) {
        if (ClubService.isOwnerOrAdmin(club, caller)) {
            return;
        }
        MembershipRole role = clubService.roleOf(club.getId(), caller.id());
        if (role == null) {
            throw ApiException.forbidden("The club chat is for the club's subscribers").withCode("SUBSCRIBERS_ONLY");
        }
    }

    /** Who gets a message live: everyone in the conversation (the sender's other devices too), minus whoever blocked the sender. */
    private Set<UUID> recipients(ChatConversation conversation, UUID senderId) {
        Set<UUID> recipients = new LinkedHashSet<>();
        if (conversation.isGroup()) {
            recipients.addAll(clubService.memberIds(conversation.getClubId()));
        } else {
            recipients.add(conversation.getUserLow());
            recipients.add(conversation.getUserHigh());
        }
        recipients.add(senderId);
        blocks.blockersOf(senderId).forEach(recipients::remove);
        return recipients;
    }

    private List<UUID> hidden(UUID userId) {
        List<UUID> blockedIds = new ArrayList<>(blocks.blockedBy(userId));
        blockedIds.add(NOBODY);
        return blockedIds;
    }

    private long unreadFor(UUID userId, UUID conversationId) {
        return messages.unreadCounts(userId, List.of(conversationId), hidden(userId)).stream()
                .mapToLong(row -> ((Number) row[1]).longValue()).sum();
    }

    private List<ChatDtos.Message> describe(List<ChatMessage> list) {
        Map<UUID, UserClient.UserSummary> senders = clubService.profilesOf(
                list.stream().map(ChatMessage::getSenderId).collect(Collectors.toSet()));
        return list.stream().map(m -> message(m, senders.get(m.getSenderId()), false)).toList();
    }

    private static ChatDtos.Message message(ChatMessage m, UserClient.UserSummary sender, boolean showDeleted) {
        boolean hide = m.isDeleted() && !showDeleted;
        return new ChatDtos.Message(m.getId(), m.getConversationId(), m.getSenderId(),
                sender == null ? null : sender.name(), sender == null ? null : sender.profilePicUrl(),
                hide ? null : m.getBody(), hide ? null : m.getPhotoUrl(), m.isDeleted(), m.getCreatedAt());
    }

    /** Finds the row, or creates it in its own transaction; if someone else created it at the same moment, uses theirs. */
    private ChatConversation getOrCreate(Supplier<Optional<ChatConversation>> find, Supplier<ChatConversation> create) {
        return find.get().orElseGet(() -> {
            try {
                return newTransaction.execute(tx -> conversations.saveAndFlush(create.get()));
            } catch (DataIntegrityViolationException e) {
                return find.get().orElseThrow(() -> e);
            }
        });
    }

    private String photoFolderUrl(UUID userId) {
        return mediaProperties.publicBaseUrl() + "/chat/" + userId + "/";
    }

    private static void requireAdmin(AuthenticatedUser caller) {
        if (!caller.isAdmin()) {
            throw ApiException.forbidden("Only admins can review reports");
        }
    }

    private static ApiException blocked() {
        return ApiException.forbidden("You can't message this person").withCode("CHAT_BLOCKED");
    }

    private static ApiException notConnected() {
        return ApiException.forbidden("You can message people who are in one of your clubs").withCode("CHAT_NOT_CONNECTED");
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
