package com.theclub.clubservice.repository;

import com.theclub.clubservice.model.ClubPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClubPhotoRepository extends JpaRepository<ClubPhoto, UUID> {

    List<ClubPhoto> findByClubIdOrderByCreatedAtDesc(UUID clubId);

    long countByClubId(UUID clubId);
}
