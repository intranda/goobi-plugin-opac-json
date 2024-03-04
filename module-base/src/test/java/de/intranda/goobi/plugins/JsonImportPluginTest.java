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
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import de.intranda.goobi.plugins.util.Config;
import de.sub.goobi.config.ConfigPlugins;
import de.unigoettingen.sub.search.opac.ConfigOpacCatalogue;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.Person;
import ugh.dl.Prefs;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ ConfigPlugins.class, JsonOpacPlugin.class })
@PowerMockIgnore({ "javax.management.*", "javax.net.ssl.*" })
public class JsonImportPluginTest {

    private XMLConfiguration config;

    private ConfigOpacCatalogue archive;

    private Prefs prefs;

    @Before
    public void setUp() throws Exception {
        config = getConfig();

        String jsonArchiveResponse = new String(Files.readAllBytes(Paths.get("src/test/resources/archive.json")), StandardCharsets.UTF_8);

        String jsonMultiVolumeBibResponse =
                new String(Files.readAllBytes(Paths.get("src/test/resources/multivolume.json")), StandardCharsets.UTF_8);

        String jsonMonographBibResponse =
                new String(Files.readAllBytes(Paths.get("src/test/resources/monograph.json")), StandardCharsets.UTF_8);

        PowerMock.mockStatic(ConfigPlugins.class);
        PowerMock.mockStatic(JsonOpacPlugin.class);
        EasyMock.expect(ConfigPlugins.getPluginConfig(EasyMock.anyString())).andReturn(config).anyTimes();

        EasyMock.expect(JsonOpacPlugin.getStringFromUrl("https://files.intranda.com/1234", null, null, null, null)).andReturn(jsonArchiveResponse).anyTimes();

        EasyMock.expect(JsonOpacPlugin.getStringFromUrl("https://files.intranda.com/666_123", null, null, null, null)).andReturn(jsonMultiVolumeBibResponse).anyTimes();

        EasyMock.expect(JsonOpacPlugin.getStringFromUrl("https://files.intranda.com/333", null, null, null, null)).andReturn(jsonMonographBibResponse).anyTimes();

        PowerMock.replay(JsonOpacPlugin.class);
        PowerMock.replay(ConfigPlugins.class);

        archive = new ConfigOpacCatalogue("", "", "https://files.intranda.com/", "", "iktlist", 80, "utf-8", "", null, "json", null, null);

        prefs = new Prefs();
        prefs.loadPrefs("src/test/resources/ruleset.xml");
    }

    @Test
    public void testConstructor() {
        JsonOpacPlugin plugin = new JsonOpacPlugin();
        assertNotNull(plugin);
    }

    @Test
    public void testSearchVolumeBibRecord() throws Exception {
        JsonOpacPlugin plugin = new JsonOpacPlugin();

        Fileformat ff = plugin.search("", "666_123", archive, prefs);
        DocStruct logical = ff.getDigitalDocument().getLogicalDocStruct();
        assertNotNull(logical);
        assertEquals("MultiVolumeWork", logical.getType().getName());

        DocStruct anchor = logical;
        logical = anchor.getAllChildren().get(0);

        assertNotNull(anchor.getAllMetadata());
        assertNotNull(logical.getAllMetadata());
        // check anchor metadata
        for (Metadata md : anchor.getAllMetadata()) {
            if (md.getType().getName().equals("CatalogIDDigital")) {
                assertEquals("666", md.getValue());
            } else if (md.getType().getName().equals("TitleDocMain")) {
                assertEquals("Jane Doe and John Doe scrapbooks, 1958-1967", md.getValue());
            }
        }

        // check volume metadata
        for (Metadata md : logical.getAllMetadata()) {
            if (md.getType().getName().equals("CatalogIDDigital")) {
                assertEquals("456", md.getValue());
            } else if (md.getType().getName().equals("shelfmarksource")) {
                assertEquals("229 YCAL MSS", md.getValue());
            } else if (md.getType().getName().equals("TitleDocMain")) {
                assertEquals("Jane Doe and John Doe scrapbooks", md.getValue());
            }
        }
    }



    @Test
    public void testSearchMonographBibRecord() throws Exception {
        JsonOpacPlugin plugin = new JsonOpacPlugin();

        Fileformat ff = plugin.search("", "333", archive, prefs);
        DocStruct logical = ff.getDigitalDocument().getLogicalDocStruct();
        assertNotNull(logical);
        assertEquals("Monograph", logical.getType().getName());
    }


    @Test
    @Ignore("Test was not executed in prior release")
    public void testSearchArchiveRecord() throws Exception {
        JsonOpacPlugin plugin = new JsonOpacPlugin();

        Fileformat ff = plugin.search("", "1234", archive, prefs);
        DocStruct logical = ff.getDigitalDocument().getLogicalDocStruct();
        assertNotNull(logical);

        assertEquals("1234", logical.getAllMetadata().get(0).getValue());

        Person p = logical.getAllPersons().get(0);
        assertEquals("Jane", p.getFirstname());
        assertEquals("Doe", p.getLastname());
    }

    @Test
    public void testConfiguration() {
        JsonOpacPlugin plugin = new JsonOpacPlugin();

        Config config = plugin.getConfig("template");
        assertEquals("Monograph", config.getDefaultPublicationType());
    }

    private static XMLConfiguration getConfig() throws ConfigurationException {
        String file = "src/test/resources/plugin_intranda_opac_json.xml";
        XMLConfiguration config;

        config = new XMLConfiguration(file);
        return config;
    }
}
