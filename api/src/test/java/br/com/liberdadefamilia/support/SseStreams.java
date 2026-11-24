package br.com.liberdadefamilia.support;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Opens the family stream the way the web app does, with the session cookie, and reads what the server has written so
 * far. {@code MockMvc.perform} returns as soon as the request goes async; the emitter keeps writing into the mock
 * response, so a test reads the stream while it is still open.
 */
public final class SseStreams {

    private SseStreams() {}

    /**
     * Opens {@code GET /v1/workspaces/{ws}/events} with the session's cookie; the result is the open stream. The test
     * sign-in ({@link TestUsers#signedIn}) is sent too, since a session made by {@code GET /v1/csrf} carries no
     * sign-in of its own in tests.
     */
    public static MvcResult open(MockMvc mvc, UUID ws, CsrfSession session, RequestPostProcessor signedIn)
            throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get("/v1/workspaces/" + ws + "/events")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .with(signedIn)
                        .cookie(session.cookie()))
                .andReturn();
    }

    /** What the stream has written so far. */
    public static String content(MvcResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The {@code data} of every {@code changed} event written so far, in order. */
    public static List<String> changes(MvcResult result) {
        List<String> changes = new ArrayList<>();
        boolean changed = false;
        for (String line : content(result).split("\n")) {
            if (line.startsWith("event:")) {
                changed = line.substring("event:".length()).trim().equals("changed");
            } else if (line.startsWith("data:") && changed) {
                changes.add(line.substring("data:".length()).trim());
                changed = false;
            }
        }
        return changes;
    }
}
