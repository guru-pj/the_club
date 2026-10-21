package com.theclub.clubservice.service;

import com.theclub.clubservice.dto.ClubPhotoResponse;
import com.theclub.clubservice.model.Club;
import com.theclub.clubservice.model.ClubPhoto;
import com.theclub.clubservice.repository.ClubPhotoRepository;
import com.theclub.common.media.MediaStore;
import com.theclub.common.security.AuthenticatedUser;
import com.theclub.common.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/** The club's photo gallery and its uploaded logo (user journey step 4). */
@Service
public class ClubPhotoService {

    public static final int MAX_PHOTOS = 30;

    private final ClubService clubService;
    private final ClubPhotoRepository photos;
    private final MediaStore media;

    public ClubPhotoService(ClubService clubService, ClubPhotoRepository photos, MediaStore media) {
        this.clubService = clubService;
        this.photos = photos;
        this.media = media;
    }

    @Transactional(readOnly = true)
    public List<ClubPhotoResponse> list(UUID clubId) {
        clubService.get(clubId);
        return photos.findByClubIdOrderByCreatedAtDesc(clubId).stream().map(ClubPhotoResponse::of).toList();
    }

    @Transactional
    public ClubPhotoResponse add(AuthenticatedUser caller, UUID clubId, MultipartFile file, String caption) {
        Club club = clubService.get(clubId);
        ClubService.requireOwnerOrAdmin(club, caller);
        if (photos.countByClubId(clubId) >= MAX_PHOTOS) {
            throw ApiException.conflict("A club can have at most " + MAX_PHOTOS + " photos; remove one first")
                    .withCode("TOO_MANY_PHOTOS");
        }
        String url = media.storeImage("clubs/" + clubId, file).url();
        String text = caption == null || caption.isBlank() ? null : caption.strip();
        if (text != null && text.length() > 200) {
            throw ApiException.badRequest("A caption can be at most 200 characters");
        }
        return ClubPhotoResponse.of(photos.save(new ClubPhoto(clubId, url, text, caller.id())));
    }

    @Transactional
    public void remove(AuthenticatedUser caller, UUID clubId, UUID photoId) {
        Club club = clubService.get(clubId);
        ClubService.requireOwnerOrAdmin(club, caller);
        ClubPhoto photo = photos.findById(photoId).filter(p -> p.getClubId().equals(clubId))
                .orElseThrow(() -> ApiException.notFound("Photo not found"));
        photos.delete(photo);
        media.deleteByUrl(photo.getUrl());
    }

    /** Uploads a new logo and replaces the previous one. */
    @Transactional
    public Club uploadLogo(AuthenticatedUser caller, UUID clubId, MultipartFile file) {
        Club club = clubService.get(clubId);
        ClubService.requireOwnerOrAdmin(club, caller);
        String previous = club.getLogoUrl();
        club.setLogoUrl(media.storeImage("clubs/" + clubId, file).url());
        media.deleteByUrl(previous);
        return club;
    }

    /** Uploads a new cover photo and replaces the previous one. */
    @Transactional
    public Club uploadCover(AuthenticatedUser caller, UUID clubId, MultipartFile file) {
        Club club = clubService.get(clubId);
        ClubService.requireOwnerOrAdmin(club, caller);
        String previous = club.getCoverUrl();
        club.setCoverUrl(media.storeImage("clubs/" + clubId, file).url());
        media.deleteByUrl(previous);
        return club;
    }

    @Transactional
    public Club removeCover(AuthenticatedUser caller, UUID clubId) {
        Club club = clubService.get(clubId);
        ClubService.requireOwnerOrAdmin(club, caller);
        media.deleteByUrl(club.getCoverUrl());
        club.setCoverUrl(null);
        return club;
    }
}
