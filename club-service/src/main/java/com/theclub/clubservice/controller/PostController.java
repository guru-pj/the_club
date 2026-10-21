package com.theclub.clubservice.controller;

import com.theclub.clubservice.dto.CreatePostRequest;
import com.theclub.clubservice.dto.PageResponse;
import com.theclub.clubservice.dto.PostPreview;
import com.theclub.clubservice.dto.PostResponse;
import com.theclub.clubservice.dto.ResharePostRequest;
import com.theclub.clubservice.dto.UpdatePostRequest;
import com.theclub.clubservice.dto.UploadedImage;
import com.theclub.clubservice.service.PostService;
import com.theclub.common.security.AuthenticatedUser;
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

import java.util.UUID;

/**
 * Club feed: owners publish announcements and event recaps (with photos and an optional Strava activity) and reshare
 * other clubs' posts and events; subscribers write posts too.
 */
@RestController
@RequestMapping("/api/v1/clubs/{clubId}/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PostResponse create(@PathVariable UUID clubId, @Valid @RequestBody CreatePostRequest request,
                               @AuthenticationPrincipal AuthenticatedUser caller) {
        return postService.describe(postService.create(caller, clubId, request), caller.id());
    }

    @PostMapping("/reshare")
    @ResponseStatus(HttpStatus.CREATED)
    public PostResponse reshare(@PathVariable UUID clubId, @Valid @RequestBody ResharePostRequest request,
                                @AuthenticationPrincipal AuthenticatedUser caller) {
        return postService.describe(postService.reshare(caller, clubId, request), caller.id());
    }

    @PostMapping(value = "/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public UploadedImage uploadPhoto(@PathVariable UUID clubId, @RequestParam("file") MultipartFile file,
                                     @AuthenticationPrincipal AuthenticatedUser caller) {
        return postService.uploadPhoto(caller, clubId, file);
    }

    @GetMapping
    public PageResponse<PostResponse> feed(@PathVariable UUID clubId,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size,
                                           @AuthenticationPrincipal AuthenticatedUser caller) {
        return postService.feed(clubId, page, size, caller.id());
    }

    @GetMapping("/{postId}")
    public PostResponse get(@PathVariable UUID clubId, @PathVariable UUID postId,
                            @AuthenticationPrincipal AuthenticatedUser caller) {
        return postService.describe(clubId, postId, caller.id());
    }

    /** Public (no sign-in): what a shared link shows. */
    @GetMapping("/{postId}/preview")
    public PostPreview preview(@PathVariable UUID clubId, @PathVariable UUID postId) {
        return postService.preview(clubId, postId);
    }

    @PutMapping("/{postId}")
    public PostResponse update(@PathVariable UUID clubId, @PathVariable UUID postId,
                               @Valid @RequestBody UpdatePostRequest request,
                               @AuthenticationPrincipal AuthenticatedUser caller) {
        return postService.describe(postService.update(caller, clubId, postId, request), caller.id());
    }

    @DeleteMapping("/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID clubId, @PathVariable UUID postId,
                       @AuthenticationPrincipal AuthenticatedUser caller) {
        postService.delete(caller, clubId, postId);
    }
}
