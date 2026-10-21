package com.theclub.clubservice.controller;

import com.theclub.clubservice.dto.ChatDtos;
import com.theclub.clubservice.dto.PageResponse;
import com.theclub.clubservice.dto.UploadedImage;
import com.theclub.clubservice.model.ChatReportStatus;
import com.theclub.clubservice.service.ChatService;
import com.theclub.common.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

/**
 * Chat: each club's group chat (its subscribers and owner) and direct messages between people who share a club,
 * with blocking, reporting and the owner's moderation. New messages arrive live on {@code GET /stream}.
 */
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chat;

    public ChatController(ChatService chat) {
        this.chat = chat;
    }

    @GetMapping("/inbox")
    public List<ChatDtos.Conversation> inbox(@AuthenticationPrincipal AuthenticatedUser caller) {
        return chat.inbox(caller);
    }

    @GetMapping("/clubs/{clubId}")
    public ChatDtos.Conversation openClub(@PathVariable UUID clubId, @AuthenticationPrincipal AuthenticatedUser caller) {
        return chat.openClub(caller, clubId);
    }

    @PostMapping("/direct")
    public ChatDtos.Conversation openDirect(@Valid @RequestBody ChatDtos.StartDirectRequest request,
                                            @AuthenticationPrincipal AuthenticatedUser caller) {
        return chat.openDirect(caller, request.userId());
    }

    @GetMapping("/conversations/{conversationId}")
    public ChatDtos.Conversation get(@PathVariable UUID conversationId,
                                     @AuthenticationPrincipal AuthenticatedUser caller) {
        return chat.get(caller, conversationId);
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public List<ChatDtos.Message> messages(@PathVariable UUID conversationId,
                                           @RequestParam(required = false) UUID before,
                                           @RequestParam(required = false) Integer limit,
                                           @AuthenticationPrincipal AuthenticatedUser caller) {
        return chat.messages(caller, conversationId, before, limit);
    }

    @PostMapping("/conversations/{conversationId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatDtos.Message send(@PathVariable UUID conversationId,
                                 @Valid @RequestBody ChatDtos.SendMessageRequest request,
                                 @AuthenticationPrincipal AuthenticatedUser caller) {
        return chat.send(caller, conversationId, request);
    }

    @PostMapping("/conversations/{conversationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable UUID conversationId, @AuthenticationPrincipal AuthenticatedUser caller) {
        chat.markRead(caller, conversationId);
    }

    @DeleteMapping("/messages/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMessage(@PathVariable UUID messageId, @AuthenticationPrincipal AuthenticatedUser caller) {
        chat.deleteMessage(caller, messageId);
    }

    @PostMapping("/messages/{messageId}/report")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void report(@PathVariable UUID messageId, @Valid @RequestBody(required = false) ChatDtos.ReportRequest request,
                       @AuthenticationPrincipal AuthenticatedUser caller) {
        chat.report(caller, messageId, request);
    }

    @PostMapping(value = "/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public UploadedImage uploadPhoto(@RequestParam("file") MultipartFile file,
                                     @AuthenticationPrincipal AuthenticatedUser caller) {
        return chat.uploadPhoto(caller, file);
    }

    @GetMapping("/blocks")
    public List<ChatDtos.BlockedUser> blocked(@AuthenticationPrincipal AuthenticatedUser caller) {
        return chat.blocked(caller);
    }

    @PutMapping("/blocks/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void block(@PathVariable UUID userId, @AuthenticationPrincipal AuthenticatedUser caller) {
        chat.block(caller, userId);
    }

    @DeleteMapping("/blocks/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(@PathVariable UUID userId, @AuthenticationPrincipal AuthenticatedUser caller) {
        chat.unblock(caller, userId);
    }

    @GetMapping("/clubs/{clubId}/mutes")
    public List<ChatDtos.Mute> mutes(@PathVariable UUID clubId, @AuthenticationPrincipal AuthenticatedUser caller) {
        return chat.mutes(caller, clubId);
    }

    @PutMapping("/clubs/{clubId}/mutes/{userId}")
    public ChatDtos.Mute mute(@PathVariable UUID clubId, @PathVariable UUID userId,
                              @Valid @RequestBody(required = false) ChatDtos.MuteRequest request,
                              @AuthenticationPrincipal AuthenticatedUser caller) {
        return chat.mute(caller, clubId, userId, request);
    }

    @DeleteMapping("/clubs/{clubId}/mutes/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unmute(@PathVariable UUID clubId, @PathVariable UUID userId,
                       @AuthenticationPrincipal AuthenticatedUser caller) {
        chat.unmute(caller, clubId, userId);
    }

    /** Admins: reported messages. */
    @GetMapping("/reports")
    public PageResponse<ChatDtos.Report> reports(@RequestParam(required = false) ChatReportStatus status,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size,
                                                 @AuthenticationPrincipal AuthenticatedUser caller) {
        return chat.reports(caller, status, page, size);
    }

    @PostMapping("/reports/{reportId}/resolve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resolve(@PathVariable UUID reportId, @RequestBody ChatDtos.ResolveReportRequest request,
                        @AuthenticationPrincipal AuthenticatedUser caller) {
        chat.resolve(caller, reportId, request);
    }

    /**
     * Server-Sent Events: "ready" once connected, then a "message" event for each new or deleted message, and a
     * comment every 20 seconds to keep proxies from closing the connection. Reconnect when it ends.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal AuthenticatedUser caller, HttpServletResponse response) {
        // nginx must pass events on at once instead of buffering them
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
        return chat.stream(caller);
    }
}
