package com.subtrack.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Shortcuts for integration tests: register a fresh user and send authenticated JSON requests. */
public final class ApiTestHelper {

    private ApiTestHelper() {
    }

    /** A freshly registered user's access token. */
    public record TestUser(String email, String accessToken) {

        public MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder req) {
            return req.header("Authorization", "Bearer " + accessToken);
        }

        public MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder req, String body) {
            return auth(req).contentType(MediaType.APPLICATION_JSON).content(body);
        }
    }

    public static TestUser register(MockMvc mvc) throws Exception {
        String email = "user-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        MvcResult res = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tester\",\"email\":\"" + email + "\",\"password\":\"password1\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new TestUser(email, JsonPath.read(res.getResponse().getContentAsString(), "$.accessToken"));
    }

    public static <T> T read(MvcResult res, String path) throws Exception {
        return JsonPath.read(res.getResponse().getContentAsString(), path);
    }
}
