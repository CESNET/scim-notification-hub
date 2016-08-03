package core;

import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Testing the serialization of the sen object to and from JSON.
 *
 * @author Jiri Mauritz
 */
public class ScimEventNotificationTest {

    private static final String FILE_NAME = "sen1.json";

    @Test
    public void jsonSerializationTest() throws Exception {
        // load sen from file
        List<String> jsonList = Files.readAllLines(Paths.get(ClassLoader.getSystemResource(FILE_NAME).toURI()));
        String json = String.join("\n", jsonList);

        ObjectMapper mapper = new ObjectMapper();
        // String -> JSON
        ScimEventNotification sen = mapper.readValue(json, ScimEventNotification.class);
        Assert.assertNotNull(sen.getFeedUris());
        Assert.assertNotNull(sen.getPublisherUri());
        Assert.assertNotNull(sen.getResourceUris());
        Assert.assertNotNull(sen.getType());
        Assert.assertNotNull(sen.getAttributes());
        Assert.assertNotNull(sen.getValues());
        Assert.assertTrue(sen.getFeedUris().size() > 0);
        Assert.assertTrue(sen.getResourceUris().size() > 0);
        Assert.assertTrue(sen.getAttributes().size() > 0);
        Assert.assertTrue(sen.getValues().size() > 0);

        // JSON -> String
        String jsonOutput = mapper.writeValueAsString(sen);
    }

}