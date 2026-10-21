package com.theclub.clubservice.controller;

import com.theclub.clubservice.dto.ClubPhotoResponse;
import com.theclub.clubservice.dto.ClubResponse;
import com.theclub.clubservice.service.ClubPhotoService;
import com.theclub.clubservice.service.ClubService;
import com.theclub.common.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/** Uploaded images: the gallery and the logo. Uploads are multipart (field "file"), JPEG, PNG or WebP. */
@RestController
@RequestMapping("/api/v1/clubs/{clubId}")
public class ClubPhotoController {

    private final ClubPhotoService photos;
    private final ClubService clubService;

    public ClubPhotoController(ClubPhotoService photos, ClubService clubService) {
        this.photos = photos;
        this.clubService = clubService;
    }

    @GetMapping("/photos")
    public List<ClubPhotoResponse> list(@PathVariable UUID clubId) {
        return photos.list(clubId);
    }

    @PostMapping(value = "/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ClubPhotoResponse add(@PathVariable UUID clubId, @RequestParam("file") MultipartFile file,
                                 @RequestParam(required = false) String caption,
                                 @AuthenticationPrincipal AuthenticatedUser caller) {
        return photos.add(caller, clubId, file, caption);
    }

    @DeleteMapping("/photos/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID clubId, @PathVariable UUID photoId,
                       @AuthenticationPrincipal AuthenticatedUser caller) {
        photos.remove(caller, clubId, photoId);
    }

    @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ClubResponse uploadLogo(@PathVariable UUID clubId, @RequestParam("file") MultipartFile file,
                                   @AuthenticationPrincipal AuthenticatedUser caller) {
        return clubService.describe(photos.uploadLogo(caller, clubId, file), caller.id());
    }
}
