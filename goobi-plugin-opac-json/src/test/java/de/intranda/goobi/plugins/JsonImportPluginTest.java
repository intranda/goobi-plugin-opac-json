package de.intranda.goobi.plugins;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import de.intranda.goobi.plugins.util.Config;
import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.HttpClientHelper;
import de.unigoettingen.sub.search.opac.ConfigOpacCatalogue;
import ugh.dl.Fileformat;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ ConfigPlugins.class, HttpClientHelper.class })
@PowerMockIgnore({ "javax.management.*", "javax.net.ssl.*" })
public class JsonImportPluginTest {

    private XMLConfiguration config;

    private ConfigOpacCatalogue archive;

    @Before
    public void setUp() throws Exception {
        config = getConfig();
        String jsonResponse = new String(Files.readAllBytes(Paths.get("src/test/resources/ils/39002098971741_7748458.json")), StandardCharsets.UTF_8);

        PowerMock.mockStatic(ConfigPlugins.class);
        PowerMock.mockStatic(HttpClientHelper.class);
        EasyMock.expect(ConfigPlugins.getPluginConfig(EasyMock.anyString())).andReturn(config).anyTimes();

        EasyMock.expect(HttpClientHelper.getStringFromUrl(EasyMock.anyString())).andReturn(jsonResponse).anyTimes();

        PowerMock.replay(HttpClientHelper.class);
        PowerMock.replay(ConfigPlugins.class);

        archive = new ConfigOpacCatalogue("", "", "https://files.intranda.com/", "", "iktlist", 80, "utf-8", "", null, "json", null, null);
    }

    @Test
    public void testConstructor() {
        JsonOpacPlugin plugin = new JsonOpacPlugin();
        assertNotNull(plugin);
    }

    @Test
    public void testFileUpload() throws Exception {
        JsonOpacPlugin plugin = new JsonOpacPlugin();

        Fileformat ff = plugin.search("", "7748458", archive, null);

        //        assertEquals(1, recordList.size());
        //        assertEquals("7748458", recordList.get(0).getId());
    }

    @Test
    public void testConfiguration() {
        JsonOpacPlugin plugin = new JsonOpacPlugin();

        Config config = plugin.getConfig();
        assertEquals("Monograph", config.getDefaultPublicationType());
    }

    private static XMLConfiguration getConfig() throws ConfigurationException {
        String file = "src/test/resources/plugin_intranda_opac_json.xml";
        XMLConfiguration config;

        config = new XMLConfiguration(file);
        return config;
    }
}
