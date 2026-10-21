package com.theclub.userservice.controller;

import com.theclub.common.media.MediaStore;
import com.theclub.common.media.StoredImage;
import com.theclub.userservice.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** User journey step 2: profile details, an uploaded picture and an approximate location. */
class ProfileSetupIntegrationTest extends IntegrationTestBase {

    @MockitoBean
    MediaStore media;
    @Autowired
    ObjectMapper json;

    private String register(String email) throws Exception {
        String body = mvc.perform(post("/api/v1/users/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Asha","email":"%s","password":"Str0ng-pass!"}""".formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.profileComplete").value(false))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asString();
    }

    @Test
    void setsAnApproximateLocationAndFinishesSetup() throws Exception {
        String id = register("setup@example.com");
        String token = tokenFor(id, "USER");

        mvc.perform(put("/api/v1/users/{id}", id).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bio":"Morning runner","city":"Chennai","latitude":13.082680,"longitude":80.270718,
                                 "profileComplete":true}"""))
                .andExpect(status().isOk())
                // ~100 m precision: the exact point isn't stored
                .andExpect(jsonPath("$.latitude").value(13.083))
                .andExpect(jsonPath("$.longitude").value(80.271))
                .andExpect(jsonPath("$.profileComplete").value(true));

        // Others see the city, never the coordinates
        String other = tokenFor("01a00000-0000-7000-8000-000000000001", "USER");
        mvc.perform(get("/api/v1/users/{id}", id).header("Authorization", other))
                .andExpect(jsonPath("$.city").value("Chennai"))
                .andExpect(jsonPath("$.latitude").doesNotExist())
                .andExpect(jsonPath("$.profileComplete").doesNotExist());

        mvc.perform(put("/api/v1/users/{id}", id).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clearLocation\":true}"))
                .andExpect(jsonPath("$.latitude").doesNotExist())
                .andExpect(jsonPath("$.profileComplete").value(true));
    }

    @Test
    void rejectsHalfALocationAndOutOfRangeCoordinates() throws Exception {
        String id = register("badloc@example.com");
        String token = tokenFor(id, "USER");

        mvc.perform(put("/api/v1/users/{id}", id).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":13.08}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INCOMPLETE_LOCATION"));
        mvc.perform(put("/api/v1/users/{id}", id).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":91,\"longitude\":80}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.latitude").exists());
    }

    @Test
    void uploadsReplacesAndRemovesTheProfilePicture() throws Exception {
        String id = register("photo@example.com");
        String token = tokenFor(id, "USER");
        when(media.storeImage(eq("profiles"), any()))
                .thenReturn(new StoredImage("/media/profiles/first.jpg", "profiles/first.jpg"))
                .thenReturn(new StoredImage("/media/profiles/second.jpg", "profiles/second.jpg"));
        MockMultipartFile file = new MockMultipartFile("file", "me.jpg", "image/jpeg", new byte[]{1, 2, 3});

        mvc.perform(multipart("/api/v1/users/me/photo").file(file).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profilePicUrl").value("/media/profiles/first.jpg"));
        mvc.perform(multipart("/api/v1/users/me/photo").file(file).header("Authorization", token))
                .andExpect(jsonPath("$.profilePicUrl").value("/media/profiles/second.jpg"));
        verify(media).deleteByUrl("/media/profiles/first.jpg");

        mvc.perform(delete("/api/v1/users/me/photo").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profilePicUrl").doesNotExist());
        verify(media).deleteByUrl("/media/profiles/second.jpg");
    }

    @Test
    void uploadingRequiresSignIn() throws Exception {
        mvc.perform(multipart("/api/v1/users/me/photo")
                        .file(new MockMultipartFile("file", "me.jpg", "image/jpeg", new byte[]{1})))
                .andExpect(status().isUnauthorized());
    }
}
