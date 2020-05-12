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
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.Person;
import ugh.dl.Prefs;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ ConfigPlugins.class, HttpClientHelper.class })
@PowerMockIgnore({ "javax.management.*", "javax.net.ssl.*" })
public class JsonImportPluginTest {

    private XMLConfiguration config;

    private ConfigOpacCatalogue archive;

    private Prefs prefs;

    @Before
    public void setUp() throws Exception {
        config = getConfig();
        //        String jsonResponse = new String(Files.readAllBytes(Paths.get("src/test/resources/ils/39002098971741_7748458.json")), StandardCharsets.UTF_8);
        String jsonArchiveResponse = new String(Files.readAllBytes(Paths.get("src/test/resources/aspace/304086.json")), StandardCharsets.UTF_8);

        String jsonMultiVolumeBibResponse =
                new String(Files.readAllBytes(Paths.get("src/test/resources/ils/39002098971741_7748458.json")), StandardCharsets.UTF_8);

        String jsonMonographBibResponse =
                new String(Files.readAllBytes(Paths.get("src/test/resources/ils/39002104686564_9869346.json")), StandardCharsets.UTF_8);

        PowerMock.mockStatic(ConfigPlugins.class);
        PowerMock.mockStatic(HttpClientHelper.class);
        EasyMock.expect(ConfigPlugins.getPluginConfig(EasyMock.anyString())).andReturn(config).anyTimes();

        EasyMock.expect(HttpClientHelper.getStringFromUrl("https://files.intranda.com/304086")).andReturn(jsonArchiveResponse).anyTimes();

        EasyMock.expect(HttpClientHelper.getStringFromUrl("https://files.intranda.com/7748458")).andReturn(jsonMultiVolumeBibResponse).anyTimes();

        EasyMock.expect(HttpClientHelper.getStringFromUrl("https://files.intranda.com/9869346")).andReturn(jsonMonographBibResponse).anyTimes();

        PowerMock.replay(HttpClientHelper.class);
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

        Fileformat ff = plugin.search("", "7748458", archive, prefs);
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
                assertEquals("7748458", md.getValue());
            } else if (md.getType().getName().equals("TitleDocMain")) {
                assertEquals("Jane Wodening and Stan Brakhage scrapbooks", md.getValue());
            }
        }

        // check volume metadata
        for (Metadata md : logical.getAllMetadata()) {
            if (md.getType().getName().equals("CatalogIDDigital")) {
                assertEquals("8246360", md.getValue());
            } else if (md.getType().getName().equals("shelfmarksource")) {
                assertEquals("229 YCAL MSS", md.getValue());
            } else if (md.getType().getName().equals("TitleDocMain")) {
                assertEquals("Jane Wodening and Stan Brakhage scrapbooks 1", md.getValue());
            } else if (md.getType().getName().equals("OtherTitle")) {
                System.out.println(md.getValue());
            }


        }
    }



    @Test
    public void testSearchMonographBibRecord() throws Exception {
        JsonOpacPlugin plugin = new JsonOpacPlugin();

        Fileformat ff = plugin.search("", "9869346", archive, prefs);
        DocStruct logical = ff.getDigitalDocument().getLogicalDocStruct();
        assertNotNull(logical);
        assertEquals("Monograph", logical.getType().getName());
    }


    @Test
    public void testSearchArchiveRecord() throws Exception {
        JsonOpacPlugin plugin = new JsonOpacPlugin();

        Fileformat ff = plugin.search("", "304086", archive, prefs);
        DocStruct logical = ff.getDigitalDocument().getLogicalDocStruct();
        assertNotNull(logical);

        assertEquals("304086", logical.getAllMetadata().get(0).getValue());

        Person p = logical.getAllPersons().get(0);
        assertEquals("George", p.getFirstname());
        assertEquals("Eliot", p.getLastname());
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
